import Flutter
import UIKit
import UserNotifications
import LocalAuthentication

@main
@objc class AppDelegate: FlutterAppDelegate {
  private var captureObserver: NSObjectProtocol?
  deinit { if let observer = captureObserver { NotificationCenter.default.removeObserver(observer) } }

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    if let controller = window?.rootViewController as? FlutterViewController {
      let security = FlutterMethodChannel(
        name: "com.dhalam.ap/security", binaryMessenger: controller.binaryMessenger)
      security.setMethodCallHandler { [weak self] call, result in
        switch call.method {
        case "collectSecurityObservation":
          // Unavailable cross-app checks stay null, never an empty 'safe' result.
          result([
            "observedAt": Int64(Date().timeIntervalSince1970 * 1000),
            "services": NSNull(),
            "installedServices": NSNull(),
            "permissionInventory": NSNull(),
            "deviceSignals": Self.deviceSignals(),
            "developerSignals": [
              ["label": "Other apps’ permissions", "status": "Unavailable on iOS"],
              ["label": "Accessibility service inventory", "status": "Unavailable on iOS"],
              ["label": "Developer settings", "status": "Review manually in Settings"]
            ]
          ])
        case "openAppSettings":
          self?.openSettings(result)
        default:
          result(FlutterMethodNotImplemented)
        }
      }
      let tools = FlutterMethodChannel(name: "com.dhalam.ap/tools", binaryMessenger: controller.binaryMessenger)
      captureObserver = NotificationCenter.default.addObserver(
        forName: UIScreen.capturedDidChangeNotification, object: nil, queue: .main
      ) { [weak controller] _ in
        guard let controller = controller else { return }
        tools.invokeMethod("captureChanged", arguments: controller.view.window?.screen.isCaptured == true ? "Active" : "Inactive")
      }
      tools.setMethodCallHandler { [weak self, weak controller] call, result in
        guard let self = self, let controller = controller else {
          result(FlutterError(code: "UNAVAILABLE", message: "App is not active.", details: nil)); return
        }
        do {
          switch call.method {
          case "loadJournal":
            let url = try self.journalURL()
            result(FileManager.default.fileExists(atPath: url.path) ? try String(contentsOf: url, encoding: .utf8) : nil)
          case "saveJournal":
            guard let text = call.arguments as? String, text.utf8.count <= 2_000_000 else {
              result(FlutterError(code: "INVALID_REPORT", message: "Invalid history data.", details: nil)); return
            }
            let url = try self.journalURL()
            try Data(text.utf8).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            result(nil)
          case "captureStatus":
            if #available(iOS 17.0, *) {
              switch controller.traitCollection.sceneCaptureState {
              case .active: result("Active")
              case .inactive: result("Inactive")
              default: result("Unavailable")
              }
            } else {
              result(controller.view.window?.screen.isCaptured == true ? "Active" : "Inactive")
            }
          case "openPrivacySettings": self.openSettings(result)
          case "shareReport":
            guard let text = call.arguments as? String, text.utf8.count <= 2_000_000 else {
              result(FlutterError(code: "INVALID_REPORT", message: "Report is too large.", details: nil)); return
            }
            let sheet = UIActivityViewController(activityItems: [text], applicationActivities: nil)
            if let popover = sheet.popoverPresentationController {
              popover.sourceView = controller.view
              popover.sourceRect = CGRect(x: controller.view.bounds.midX, y: controller.view.bounds.midY, width: 1, height: 1)
              popover.permittedArrowDirections = []
            }
            controller.present(sheet, animated: true)
            result(nil)
          default: result(FlutterMethodNotImplemented)
          }
        } catch {
          result(FlutterError(code: "TOOLS_FAILED", message: error.localizedDescription, details: nil))
        }
      }
      let notifications = FlutterMethodChannel(
        name: "com.dhalam.ap/notifications", binaryMessenger: controller.binaryMessenger)
      notifications.setMethodCallHandler { [weak self] call, result in
        let center = UNUserNotificationCenter.current()
        switch call.method {
        case "areNotificationsEnabled":
          center.getNotificationSettings { settings in
            let enabled = settings.authorizationStatus == .authorized ||
              settings.authorizationStatus == .provisional
            DispatchQueue.main.async { result(enabled) }
          }
        case "enableAlerts":
          center.getNotificationSettings { settings in
            if settings.authorizationStatus == .notDetermined {
              center.requestAuthorization(options: [.alert, .sound, .badge]) { _, error in
                DispatchQueue.main.async {
                  if let error = error {
                    result(FlutterError(code: "NOTIFICATION_PERMISSION", message: error.localizedDescription, details: nil))
                  } else {
                    result(nil)
                  }
                }
              }
            } else {
              DispatchQueue.main.async { self?.openSettings(result) }
            }
          }
        default:
          result(FlutterMethodNotImplemented)
        }
      }
    }
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  private func journalURL() throws -> URL {
    var folder = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("Dhalam", isDirectory: true)
    try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try folder.setResourceValues(values)
    return folder.appendingPathComponent("security-journal.json")
  }

  private static func deviceSignals() -> [[String: String]] {
    let context = LAContext()
    defer { context.invalidate() }
    var lockError: NSError?
    let canAuthenticate = context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &lockError)
    let lockStatus: String
    if canAuthenticate {
      lockStatus = "Configured — device passcode is set"
    } else if lockError?.domain == LAError.errorDomain && lockError?.code == LAError.passcodeNotSet.rawValue {
      lockStatus = "Not configured — set a passcode in Settings"
    } else {
      lockStatus = "Unavailable — could not determine passcode status"
    }

    var biometricError: NSError?
    let ready = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &biometricError)
    let biometricStatus: String
    if ready {
      biometricStatus = "Ready — biometrics are enrolled and available to this app"
    } else if biometricError?.domain == LAError.errorDomain {
      switch biometricError?.code {
      case LAError.biometryNotEnrolled.rawValue:
        biometricStatus = "Not enrolled — configure Face ID or Touch ID in Settings"
      case LAError.biometryLockout.rawValue:
        biometricStatus = "Temporarily locked — unlock with your passcode"
      case LAError.passcodeNotSet.rawValue:
        biometricStatus = "Set a device passcode before configuring biometrics"
      default:
        biometricStatus = "Unavailable to this app — hardware, permissions or device policy may restrict access"
      }
    } else {
      biometricStatus = "Unavailable — could not determine biometric readiness"
    }
    return [
      ["label": "Device lock", "status": lockStatus],
      ["label": "Biometric authentication", "status": biometricStatus],
      ["label": "Operating system", "status": "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion) — review Software Update in Settings"]
    ]
  }

  private func openSettings(_ result: @escaping FlutterResult) {
    guard let url = URL(string: UIApplication.openSettingsURLString) else {
      result(FlutterError(code: "SETTINGS_UNAVAILABLE", message: "Cannot open app settings.", details: nil))
      return
    }
    UIApplication.shared.open(url, options: [:]) { opened in
      if opened {
        result(nil)
      } else {
        result(FlutterError(code: "SETTINGS_UNAVAILABLE", message: "Cannot open app settings.", details: nil))
      }
    }
  }
}
