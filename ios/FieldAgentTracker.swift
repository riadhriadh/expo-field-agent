import CoreLocation
import Foundation

/// Background tracking, iOS flavour.
///
/// What iOS gives: `allowsBackgroundLocationUpdates`, no automatic pausing, and
/// significant-location-change monitoring — the only mechanism that relaunches a
/// terminated app. What it does not give: any way back after the *user* force
/// quits, beyond that same significant-change relaunch. That limit is real and
/// the README states it rather than pretending otherwise.
final class FieldAgentTracker: NSObject, CLLocationManagerDelegate {

  static let shared = FieldAgentTracker()

  private let manager = CLLocationManager()
  private let queue: FieldAgentQueue
  private let uploadQueue = DispatchQueue(label: "expo.fieldagent.upload")

  private var running = false
  private var lastAccepted: (latitude: Double, longitude: Double, time: Date, accuracy: Double, acceptedAt: Date)?
  private var lastSent: (latitude: Double, longitude: Double, time: Date)?
  private var lastLocation: CLLocation?
  private var heartbeatTimer: Timer?
  private var uploading = false
  private var backgroundLocationWarned = false

  var onPosition: (([String: Any]) -> Void)?
  var onSent: ((Int, Int) -> Void)?
  var onError: ((String, String) -> Void)?
  /// Fired by the delegate, so the permission ladder can wait on the real
  /// answer instead of guessing how long the system prompt stays up.
  var onAuthorizationChange: (() -> Void)?

  private let runningKey = "expo.fieldagent.running"
  private let lastFixKey = "expo.fieldagent.lastFixAt"
  private let lastSentKey = "expo.fieldagent.lastSentAt"
  private let lastErrorKey = "expo.fieldagent.lastError"
  private let authKey = "expo.fieldagent.auth"

  override private init() {
    queue = FieldAgentQueue(url: FieldAgentQueue.defaultURL(), maxSize: FieldAgentConfig.current.queueSize)
    super.init()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyBest
    manager.activityType = .automotiveNavigation
    // Never let iOS decide the tracking is "done": a rider stopped at a light is
    // not a rider who went home.
    manager.pausesLocationUpdatesAutomatically = false
  }

  var isRunning: Bool { running }

  var queuedCount: Int { queue.count() }

  func start() throws {
    let config = FieldAgentConfig.current
    guard config.trackingURL != nil else {
      throw FieldAgentError.missingURL
    }
    let status = manager.authorizationStatus
    guard status == .authorizedAlways || status == .authorizedWhenInUse else {
      throw FieldAgentError.missingPermission
    }

    running = true
    UserDefaults.standard.set(true, forKey: runningKey)

    manager.distanceFilter = kCLDistanceFilterNone
    if status == .authorizedAlways {
      manager.allowsBackgroundLocationUpdates = true
      // The only thing that brings the app back after a termination.
      manager.startMonitoringSignificantLocationChanges()
      backgroundLocationWarned = false
    } else {
      warnBackgroundLocationLost()
    }
    manager.startUpdatingLocation()
    // Same reason as on Android: without this the first point can be minutes
    // away and the user sees "on duty" while appearing nowhere. `requestLocation`
    // is not usable here — it conflicts with continuous updates — so the cached
    // fix is what fills the gap until the first real one lands.
    if let cached = manager.location, abs(cached.timestamp.timeIntervalSinceNow) < 60 {
      handle(cached, heartbeat: false)
    }
    armHeartbeat()
    scheduleUpload()
  }

  func stop() {
    running = false
    UserDefaults.standard.set(false, forKey: runningKey)
    manager.stopUpdatingLocation()
    manager.stopMonitoringSignificantLocationChanges()
    if manager.authorizationStatus == .authorizedAlways {
      manager.allowsBackgroundLocationUpdates = false
    }
    heartbeatTimer?.invalidate()
    heartbeatTimer = nil
  }

  /// Called at launch: a relaunch by a significant location change must resume.
  func resumeIfWanted() {
    guard UserDefaults.standard.bool(forKey: runningKey) else { return }
    try? start()
  }

  func setInterval(_ seconds: Double) {
    // iOS has no update interval: it delivers on movement. The knob that maps
    // to the same intent is the distance filter, so that is what moves.
    let config = FieldAgentConfig.current
    manager.distanceFilter = seconds <= config.intervalSeconds
      ? kCLDistanceFilterNone
      : max(config.distanceFilterMeters, 1)
  }

  func setAuthHeader(_ value: String?) {
    // Keychain, not UserDefaults: a bearer token in a plist is a token on a backup.
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: authKey
    ]
    SecItemDelete(query as CFDictionary)
    guard let value, let data = value.data(using: .utf8) else { return }
    var insert = query
    insert[kSecValueData as String] = data
    insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
    SecItemAdd(insert as CFDictionary, nil)
  }

  private func authHeader() -> String? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: authKey,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne
    ]
    var result: AnyObject?
    guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
          let data = result as? Data else { return nil }
    return String(data: data, encoding: .utf8)
  }

  func state() -> [String: Any] {
    let defaults = UserDefaults.standard
    let lastFix = defaults.double(forKey: lastFixKey)
    let lastSentAt = defaults.double(forKey: lastSentKey)
    // NSNull rather than an absent key: the contract says nullable, and an
    // absent key reads as undefined on the JS side.
    return [
      "running": running,
      "queued": queue.count(),
      "lastFixAt": lastFix > 0 ? lastFix : NSNull(),
      "lastSentAt": lastSentAt > 0 ? lastSentAt : NSNull(),
      "lastError": defaults.string(forKey: lastErrorKey) ?? NSNull()
    ]
  }

  // MARK: - Authorization

  var authorizationStatus: CLAuthorizationStatus { manager.authorizationStatus }

  /// The manager is kept alive by this singleton; a throwaway CLLocationManager
  /// is deallocated before the system prompt can answer it.
  func requestWhenInUse() { manager.requestWhenInUseAuthorization() }

  func requestAlways() { manager.requestAlwaysAuthorization() }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    let callback = onAuthorizationChange
    onAuthorizationChange = nil
    callback?()

    guard running else { return }
    if manager.authorizationStatus == .authorizedAlways {
      backgroundLocationWarned = false
      return
    }
    // The system clears allowsBackgroundLocationUpdates itself the moment
    // authorization drops below Always — silently, from the tracker's point
    // of view. A driver who downgrades to "While Using" in Settings mid-shift
    // would otherwise vanish from the map with nothing telling anyone why.
    manager.stopMonitoringSignificantLocationChanges()
    warnBackgroundLocationLost()
  }

  /// Covers both entry points: starting with only "While Using" already
  /// granted, and downgrading to it later while running. Same silence either
  /// way, so the same one-shot warning.
  private func warnBackgroundLocationLost() {
    guard !backgroundLocationWarned else { return }
    backgroundLocationWarned = true
    onError?(
      "BACKGROUND_LOCATION_LOST",
      "L'autorisation \"Toujours\" n'est pas accordee : le suivi ne captera rien en arriere-plan."
    )
  }

  // MARK: - Delegate

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    locations.forEach { handle($0, heartbeat: false) }
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    // kCLErrorLocationUnknown is transient noise, not a failure worth surfacing.
    if let clError = error as? CLError, clError.code == .locationUnknown { return }
    UserDefaults.standard.set(error.localizedDescription, forKey: lastErrorKey)
    onError?("LOCATION", error.localizedDescription)
  }

  // MARK: - Pipeline

  private func handle(_ location: CLLocation, heartbeat: Bool) {
    let accuracy = location.horizontalAccuracy
    let time = location.timestamp

    if !heartbeat, !isPlausible(location) { return }

    // A heartbeat replays the same lastLocation on a timer: letting it rewrite
    // lastAccepted would refresh acceptedAt to "now" every period and starve
    // the tunnel exemption below forever, exactly the freeze it exists to bound.
    if !heartbeat {
      lastAccepted = (location.coordinate.latitude, location.coordinate.longitude, time, accuracy, Date())
      lastLocation = location
    }
    UserDefaults.standard.set(time.timeIntervalSince1970 * 1000, forKey: lastFixKey)

    let clientId = UUID().uuidString
    onPosition?([
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "accuracy": accuracy,
      "altitude": location.altitude,
      "speed": location.speed,
      "heading": location.course,
      "timestamp": time.timeIntervalSince1970 * 1000,
      "clientId": clientId,
      "heartbeat": heartbeat
    ])

    let config = FieldAgentConfig.current
    if shouldSend(location, heartbeat: heartbeat, config: config) {
      lastSent = (location.coordinate.latitude, location.coordinate.longitude, time)
      enqueue(location, heartbeat: heartbeat, clientId: clientId)
      scheduleUpload()
    }
  }

  private func isPlausible(_ location: CLLocation) -> Bool {
    let accuracy = location.horizontalAccuracy
    if accuracy > 100 { return false }
    guard let previous = lastAccepted else { return true }
    let elapsed = location.timestamp.timeIntervalSince(previous.time)
    // Checked BEFORE the out-of-order guard below, not just the tunnel one:
    // location.timestamp is CoreLocation's own clock for the fix, not ours, and
    // a provider clock that is frozen, replayed, OR running BACKWARD must not be
    // able to starve this exemption by tripping `elapsed < 0` first. Our own
    // clock at the moment we last accepted a fix always ticks, so the freeze can
    // never last longer than one real 120s window no matter what the provider
    // clock does.
    if Date().timeIntervalSince(previous.acceptedAt) >= 120 { return true }
    if elapsed < 0 { return false }
    // Tunnel exemption: after a long gap the first point back is necessarily far.
    if elapsed >= 120 { return true }
    let distance = location.distance(
      from: CLLocation(latitude: previous.latitude, longitude: previous.longitude)
    )
    if elapsed == 0 { return distance <= max(previous.accuracy, 0) }
    return distance / elapsed <= 60
  }

  private func shouldSend(_ location: CLLocation, heartbeat: Bool, config: FieldAgentConfig) -> Bool {
    if heartbeat { return true }
    guard let sent = lastSent else { return true }
    if location.timestamp.timeIntervalSince(sent.time) >= config.heartbeatSeconds { return true }
    let distance = location.distance(from: CLLocation(latitude: sent.latitude, longitude: sent.longitude))
    return distance >= config.distanceFilterMeters
  }

  private func armHeartbeat() {
    heartbeatTimer?.invalidate()
    let period = FieldAgentConfig.current.heartbeatSeconds
    let timer = Timer(timeInterval: period, repeats: true) { [weak self] _ in
      guard let self, let location = self.lastLocation else { return }
      let lastSentAt = UserDefaults.standard.double(forKey: self.lastSentKey) / 1000
      if Date().timeIntervalSince1970 - lastSentAt < period { return }
      self.handle(location, heartbeat: true)
    }
    RunLoop.main.add(timer, forMode: .common)
    heartbeatTimer = timer
  }

  private func enqueue(_ location: CLLocation, heartbeat: Bool, clientId: String) {
    let payload: [String: Any] = [
      "client_id": clientId,
      "lat": location.coordinate.latitude,
      "lng": location.coordinate.longitude,
      "accuracy": location.horizontalAccuracy,
      "speed": location.speed,
      "heading": location.course,
      "altitude": location.altitude,
      "recorded_at": (heartbeat ? Date() : location.timestamp).timeIntervalSince1970 * 1000,
      "heartbeat": heartbeat
    ]
    guard let data = try? JSONSerialization.data(withJSONObject: payload),
          let json = String(data: data, encoding: .utf8) else { return }
    let dropped = queue.add(FieldAgentQueue.Entry(clientId: clientId, payload: json))
    if dropped > 0 {
      onError?("QUEUE_FULL", "\(dropped) position(s) supprimee(s) : la file a atteint son plafond.")
    }
  }

  func scheduleUpload() {
    uploadQueue.async { [weak self] in self?.flushBlocking(completion: nil) }
  }

  func flush(completion: @escaping (Int, Int) -> Void) {
    uploadQueue.async { [weak self] in
      guard let self else { return completion(0, 0) }
      self.flushBlocking(completion: completion)
    }
  }

  private func flushBlocking(completion: ((Int, Int) -> Void)?) {
    if uploading {
      completion?(0, queue.count())
      return
    }
    uploading = true
    defer { uploading = false }

    let config = FieldAgentConfig.current
    guard let url = config.trackingURL else {
      completion?(0, queue.count())
      return
    }

    var sent = 0
    let auth = authHeader()

    while true {
      let batch = queue.peek(config.batchSize)
      if batch.isEmpty { break }

      let asBatch = batch.count > 1 && config.batchURL != nil
      let handled = asBatch ? batch : [batch[0]]
      let target = asBatch ? config.batchURL! : url
      let body: String
      if asBatch {
        let objects = batch.compactMap { entry -> Any? in
          guard let data = entry.payload.data(using: .utf8) else { return nil }
          return try? JSONSerialization.jsonObject(with: data)
        }
        guard let wrapped = try? JSONSerialization.data(withJSONObject: ["positions": objects]),
              let text = String(data: wrapped, encoding: .utf8) else { break }
        body = text
      } else {
        body = batch[0].payload
      }

      let result = post(url: target, body: body, auth: auth)
      if result.ok {
        queue.remove(ids: handled.map { $0.clientId })
        sent += handled.count
        UserDefaults.standard.set(Date().timeIntervalSince1970 * 1000, forKey: lastSentKey)
        UserDefaults.standard.removeObject(forKey: lastErrorKey)
        continue
      }

      UserDefaults.standard.set(result.message, forKey: lastErrorKey)
      onError?(result.code, result.message)
      // A transport failure keeps everything; a definitive 4xx would otherwise
      // block every later point behind one poisoned payload.
      if !result.retryable && result.status != 401 && result.status != 403 {
        queue.remove(ids: handled.map { $0.clientId })
      }
      break
    }

    let remaining = queue.count()
    if sent > 0 { onSent?(sent, remaining) }
    completion?(sent, remaining)
  }

  private struct PostResult {
    let ok: Bool
    let status: Int
    let code: String
    let message: String
    var retryable: Bool { status == 0 || status >= 500 || status == 429 || status == 408 }
  }

  /// Synchronous on the upload queue: the loop above needs the verdict before it
  /// can decide what to drop, and this never runs on the main thread.
  private func post(url: URL, body: String, auth: String?) -> PostResult {
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.timeoutInterval = 20
    request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    if let auth { request.setValue(auth, forHTTPHeaderField: "Authorization") }
    request.httpBody = body.data(using: .utf8)

    let semaphore = DispatchSemaphore(value: 0)
    var result = PostResult(ok: false, status: 0, code: "OFFLINE", message: "reseau indisponible")

    let task = URLSession.shared.dataTask(with: request) { data, response, error in
      defer { semaphore.signal() }
      if let error {
        let nsError = error as NSError
        let code = nsError.code == NSURLErrorTimedOut ? "TIMEOUT" : "OFFLINE"
        result = PostResult(ok: false, status: 0, code: code, message: error.localizedDescription)
        return
      }
      let status = (response as? HTTPURLResponse)?.statusCode ?? 0
      if (200...299).contains(status) {
        result = PostResult(ok: true, status: status, code: "", message: "")
        if let data { FieldAgentAlerts.shared.consumeServerAlert(data) }
      } else {
        result = PostResult(ok: false, status: status, code: "HTTP_\(status)", message: "Le serveur a repondu \(status)")
      }
    }
    task.resume()
    _ = semaphore.wait(timeout: .now() + 30)
    return result
  }
}

enum FieldAgentError: Error, LocalizedError {
  case missingURL
  case missingPermission
  case invalidPayload

  var errorDescription: String? {
    switch self {
    case .missingURL:
      return "tracking.url est absente : renseigne-la dans app.json ou passe start({ url })."
    case .missingPermission:
      return "L'autorisation de localisation n'est pas accordee : appelle requestPermissions()."
    case .invalidPayload:
      return "triggerAlert attend { title: string }."
    }
  }
}
