import Foundation

/// Reads the `EXFieldAgent` dictionary the config plugin wrote into Info.plist,
/// then layers the `start(options)` overrides persisted in UserDefaults on top.
struct FieldAgentConfig {
  var trackingURL: URL?
  var batchURL: URL?
  var intervalSeconds: Double
  var idleIntervalSeconds: Double
  var distanceFilterMeters: Double
  var batchSize: Int
  var queueSize: Int
  var heartbeatSeconds: Double
  var alertTitlePattern: String
  var alertSound: String
  var alertTTLSeconds: Double
  var alertRoute: String
  var criticalAlerts: Bool

  private static let overridesKey = "expo.fieldagent.overrides"
  private static let lock = NSLock()
  private static var cached: FieldAgentConfig?

  static var current: FieldAgentConfig {
    lock.lock()
    defer { lock.unlock() }
    if let cached { return cached }
    let resolved = load()
    cached = resolved
    return resolved
  }

  static func invalidate() {
    lock.lock()
    cached = nil
    lock.unlock()
  }

  static func setOverrides(_ overrides: [String: Any]?) {
    if let overrides, !overrides.isEmpty {
      UserDefaults.standard.set(overrides, forKey: overridesKey)
    } else {
      UserDefaults.standard.removeObject(forKey: overridesKey)
    }
    invalidate()
  }

  private static func load() -> FieldAgentConfig {
    let plist = Bundle.main.object(forInfoDictionaryKey: "EXFieldAgent") as? [String: Any] ?? [:]
    let overrides = UserDefaults.standard.dictionary(forKey: overridesKey) ?? [:]

    func string(_ plistKey: String, _ overrideKey: String) -> String {
      if let value = overrides[overrideKey] as? String, !value.isEmpty { return value }
      return plist[plistKey] as? String ?? ""
    }

    func number(_ plistKey: String, _ overrideKey: String, _ fallback: Double) -> Double {
      if let value = overrides[overrideKey] as? NSNumber { return value.doubleValue }
      if let value = plist[plistKey] as? NSNumber { return value.doubleValue }
      return fallback
    }

    let idle = number("idleIntervalSeconds", "idleIntervalSeconds", 60)

    return FieldAgentConfig(
      trackingURL: URL(string: string("trackingUrl", "url")),
      batchURL: URL(string: string("trackingBatchUrl", "batchUrl")),
      intervalSeconds: max(1, number("intervalSeconds", "intervalSeconds", 15)),
      idleIntervalSeconds: max(1, idle),
      distanceFilterMeters: max(0, number("distanceFilterMeters", "distanceFilterMeters", 15)),
      batchSize: max(1, Int(number("batchSize", "batchSize", 50))),
      queueSize: max(1, Int(number("queueSize", "queueSize", 1000))),
      heartbeatSeconds: max(30, number("heartbeatSeconds", "heartbeatSeconds", max(idle * 2, 120))),
      alertTitlePattern: {
        let pattern = plist["alertTitlePattern"] as? String ?? ".*"
        return pattern.isEmpty ? ".*" : pattern
      }(),
      alertSound: plist["alertSound"] as? String ?? "",
      alertTTLSeconds: number("alertTtlSeconds", "alertTtlSeconds", 45),
      alertRoute: plist["alertRoute"] as? String ?? "field-agent-alert",
      criticalAlerts: plist["criticalAlerts"] as? Bool ?? false
    )
  }
}
