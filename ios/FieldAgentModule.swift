import CoreLocation
import ExpoModulesCore
import UIKit
import UserNotifications

/// iOS surface of expo-field-agent.
///
/// The whole API exists here. Capabilities the platform does not have return an
/// explicit value — `false`, `"unsupported"` — and never throw: a host must be
/// able to write one code path for both platforms.
public class FieldAgentModule: Module {

  private let tracker = FieldAgentTracker.shared
  private let alerts = FieldAgentAlerts.shared

  public func definition() -> ModuleDefinition {
    Name("FieldAgent")

    Events("position", "sent", "error", "alert", "bubblePress")

    OnCreate {
      self.tracker.onPosition = { [weak self] payload in self?.sendEvent("position", payload) }
      self.tracker.onSent = { [weak self] count, queued in
        self?.sendEvent("sent", ["count": count, "queued": queued])
      }
      self.tracker.onError = { [weak self] code, message in
        self?.sendEvent("error", ["code": code, "message": message])
      }
      self.alerts.onAlert = { [weak self] alert in self?.sendEvent("alert", alert) }
      // A significant location change can relaunch the app straight into this
      // module; resuming here is what makes that relaunch worth anything.
      self.tracker.resumeIfWanted()
    }

    OnDestroy {
      self.tracker.onPosition = nil
      self.tracker.onSent = nil
      self.tracker.onError = nil
      self.alerts.onAlert = nil
    }

    // MARK: Permissions

    AsyncFunction("getPermissions") { (promise: Promise) in
      self.readPermissions { promise.resolve($0) }
    }

    AsyncFunction("requestPermissions") { (skip: [String], promise: Promise) in
      let skipped = Set(skip)

      let finish = { self.readPermissions { promise.resolve($0) } }

      let askNotifications = {
        guard !skipped.contains("notifications") else { return finish() }
        var options: UNAuthorizationOptions = [.alert, .sound, .badge]
        if FieldAgentConfig.current.criticalAlerts { options.insert(.criticalAlert) }
        UNUserNotificationCenter.current().requestAuthorization(options: options) { _, _ in
          DispatchQueue.main.async { finish() }
        }
      }

      guard !(skipped.contains("location") && skipped.contains("backgroundLocation")) else {
        return askNotifications()
      }

      // iOS imposes the same two-step ladder as Android: when-in-use first,
      // "always" only afterwards, and only the system may show the second prompt.
      self.requestLocation(wantsAlways: !skipped.contains("backgroundLocation")) {
        askNotifications()
      }
    }

    AsyncFunction("openSettings") { (_: String) -> Void in
      // iOS has exactly one destination for all of them.
      guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
      DispatchQueue.main.async { UIApplication.shared.open(url) }
    }

    // MARK: Tracking

    AsyncFunction("start") { (options: [String: Any]?) -> Void in
      FieldAgentConfig.setOverrides(options)
      try self.tracker.start()
    }

    AsyncFunction("stop") { () -> Void in self.tracker.stop() }

    AsyncFunction("isRunning") { () -> Bool in self.tracker.isRunning }

    AsyncFunction("setAuthHeader") { (value: String?) -> Void in self.tracker.setAuthHeader(value) }

    AsyncFunction("setIntervalSeconds") { (seconds: Double) -> Void in self.tracker.setInterval(seconds) }

    AsyncFunction("flush") { (promise: Promise) in
      self.tracker.flush { sent, queued in
        promise.resolve(["sent": sent, "queued": queued])
      }
    }

    AsyncFunction("getState") { () -> [String: Any] in self.tracker.state() }

    // MARK: Bubble — no such thing on iOS, and there never will be

    AsyncFunction("showBubble") { () -> Bool in false }
    AsyncFunction("hideBubble") { () -> Void in }
    AsyncFunction("setBubbleState") { (_: String, _: String?) -> Void in }

    // MARK: Alert

    AsyncFunction("triggerAlert") { (payload: [String: Any]) -> Void in
      guard let title = payload["title"] as? String, !title.isEmpty else {
        throw FieldAgentError.invalidPayload
      }
      let data = payload["data"] as? [String: Any]
      let silent = (data?["silent"] as? Bool) == true
      DispatchQueue.main.async {
        self.alerts.trigger(
          title: title,
          body: payload["body"] as? String,
          data: data,
          silent: silent,
          tag: payload["tag"] as? String,
          channelId: payload["channelId"] as? String
        )
      }
    }

    AsyncFunction("dismissAlert") { () -> Void in
      DispatchQueue.main.async { self.alerts.dismiss() }
    }

    AsyncFunction("setAlertSound") { (enabled: Bool) -> Void in self.alerts.soundEnabled = enabled }

    Function("getPendingAlertSync") { () -> [String: Any]? in self.alerts.pending() }
  }

  // MARK: - Helpers

  private func readPermissions(_ completion: @escaping ([String: String]) -> Void) {
    let location: String
    let background: String

    switch tracker.authorizationStatus {
    case .authorizedAlways:
      location = "granted"
      background = "granted"
    case .authorizedWhenInUse:
      location = "granted"
      background = "denied"
    case .denied, .restricted:
      location = "denied"
      background = "denied"
    default:
      location = "undetermined"
      background = "undetermined"
    }

    UNUserNotificationCenter.current().getNotificationSettings { settings in
      let notifications: String
      switch settings.authorizationStatus {
      case .authorized, .provisional, .ephemeral: notifications = "granted"
      case .denied: notifications = "denied"
      default: notifications = "undetermined"
      }

      completion([
        "location": location,
        "backgroundLocation": background,
        "notifications": notifications,
        // No overlay window, no battery allow-list, no notification policy
        // access, no full-screen intent, no manufacturer autostart on iOS.
        "overlay": "unsupported",
        "batteryUnrestricted": "unsupported",
        "dndAccess": "unsupported",
        "fullScreenIntent": "unsupported",
        "autostart": "unsupported"
      ])
    }
  }

  private func requestLocation(wantsAlways: Bool, completion: @escaping () -> Void) {
    // The system prompt can be dismissed without ever changing the status, so
    // every wait carries a deadline: a permission call that never resolves is a
    // host stuck on a spinner.
    var settled = false
    let once = {
      guard !settled else { return }
      settled = true
      completion()
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + 60) { once() }

    switch tracker.authorizationStatus {
    case .notDetermined:
      tracker.onAuthorizationChange = { [weak self] in
        guard let self, wantsAlways, self.tracker.authorizationStatus == .authorizedWhenInUse else {
          return once()
        }
        // Second step: iOS offers "Always" only after when-in-use was granted.
        self.tracker.onAuthorizationChange = { once() }
        self.tracker.requestAlways()
      }
      tracker.requestWhenInUse()

    case .authorizedWhenInUse where wantsAlways:
      tracker.onAuthorizationChange = { once() }
      tracker.requestAlways()

    default:
      once()
    }
  }
}
