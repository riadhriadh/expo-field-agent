import Foundation
import UserNotifications

/// Alerts, iOS flavour.
///
/// There is no equivalent of Android's full-screen intent. The closest thing the
/// platform offers is a time-sensitive notification, and — only with the Critical
/// Alerts entitlement Apple grants on a motivated request — a sound that plays
/// through silent mode. Everything below says so plainly rather than pretending
/// the screen will light up on its own.
final class FieldAgentAlerts {

  static let shared = FieldAgentAlerts()

  private let pendingKey = "expo.fieldagent.pendingAlert"
  private let soundKey = "expo.fieldagent.alertSound"
  private let categoryIdentifier = "expo.fieldagent.alert"

  var onAlert: (([String: Any]) -> Void)?

  private var ttlTimer: Timer?

  private init() {}

  var soundEnabled: Bool {
    get { UserDefaults.standard.object(forKey: soundKey) as? Bool ?? true }
    set { UserDefaults.standard.set(newValue, forKey: soundKey) }
  }

  func pending() -> [String: Any]? {
    UserDefaults.standard.dictionary(forKey: pendingKey)
  }

  /// Tested against the title, the notification tag and the channel id —
  /// whichever the sender filled in. A push pipeline that routes by channel
  /// should not have to fake a title to be recognised.
  func matches(_ candidates: String?...) -> Bool {
    let pattern = FieldAgentConfig.current.alertTitlePattern
    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else {
      return true
    }
    return candidates.contains { candidate in
      guard let candidate, !candidate.isEmpty else { return false }
      let range = NSRange(candidate.startIndex..., in: candidate)
      return regex.firstMatch(in: candidate, options: [], range: range) != nil
    }
  }

  @discardableResult
  func trigger(
    title: String,
    body: String?,
    data: [String: Any]?,
    silent: Bool,
    tag: String? = nil,
    channelId: String? = nil
  ) -> Bool {
    guard matches(title, tag, channelId) else { return false }

    // One alert, one ring: a duplicate push must not stack a second sound.
    if let existing = pending(),
       existing["title"] as? String == title,
       (existing["body"] as? String) == body {
      return true
    }

    var alert: [String: Any] = [
      "id": UUID().uuidString,
      "title": title,
      "receivedAt": Date().timeIntervalSince1970 * 1000,
      // Carried through so a host can navigate instead of overlaying.
      "route": FieldAgentConfig.current.alertRoute
    ]
    if let body { alert["body"] = body }
    if let data, let encoded = try? JSONSerialization.data(withJSONObject: data),
       let text = String(data: encoded, encoding: .utf8) {
      alert["dataJson"] = text
    }
    UserDefaults.standard.set(alert, forKey: pendingKey)

    post(alert: alert, silent: silent || !soundEnabled)
    armTTL()
    onAlert?(alert)
    return true
  }

  func dismiss() {
    ttlTimer?.invalidate()
    ttlTimer = nil
    UserDefaults.standard.removeObject(forKey: pendingKey)
    let center = UNUserNotificationCenter.current()
    center.removeDeliveredNotifications(withIdentifiers: [categoryIdentifier])
    center.removePendingNotificationRequests(withIdentifiers: [categoryIdentifier])
  }

  /// The server can hand an alert back on a position response — the one path
  /// that needs no push infrastructure at all.
  func consumeServerAlert(_ data: Data) {
    guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let alert = root["alert"] as? [String: Any],
          let title = alert["title"] as? String, !title.isEmpty else { return }
    DispatchQueue.main.async {
      self.trigger(
        title: title,
        body: alert["body"] as? String,
        data: alert["data"] as? [String: Any],
        silent: false,
        tag: alert["tag"] as? String,
        channelId: alert["channelId"] as? String
      )
    }
  }

  private func armTTL() {
    ttlTimer?.invalidate()
    // A ringtone that never stops because an end event was lost is how an app
    // gets uninstalled. The ceiling is not optional.
    let timer = Timer(timeInterval: FieldAgentConfig.current.alertTTLSeconds, repeats: false) { [weak self] _ in
      self?.dismiss()
    }
    RunLoop.main.add(timer, forMode: .common)
    ttlTimer = timer
  }

  private func post(alert: [String: Any], silent: Bool) {
    let content = UNMutableNotificationContent()
    content.title = alert["title"] as? String ?? ""
    content.body = alert["body"] as? String ?? ""
    content.categoryIdentifier = categoryIdentifier
    content.userInfo = alert

    if #available(iOS 15.0, *) {
      // Time-sensitive is what pierces Focus modes; critical also pierces the
      // ring switch, and only with the entitlement Apple has to grant.
      content.interruptionLevel = FieldAgentConfig.current.criticalAlerts ? .critical : .timeSensitive
      content.relevanceScore = 1.0
    }

    if !silent {
      content.sound = soundForAlert()
    }

    let request = UNNotificationRequest(identifier: categoryIdentifier, content: content, trigger: nil)
    UNUserNotificationCenter.current().add(request)
  }

  private func soundForAlert() -> UNNotificationSound {
    let filename = FieldAgentConfig.current.alertSound
    guard !filename.isEmpty else {
      return FieldAgentConfig.current.criticalAlerts
        ? UNNotificationSound.defaultCritical
        : UNNotificationSound.default
    }
    let name = UNNotificationSoundName(rawValue: filename)
    return FieldAgentConfig.current.criticalAlerts
      ? UNNotificationSound.criticalSoundNamed(name, withAudioVolume: 1.0)
      : UNNotificationSound(named: name)
  }
}
