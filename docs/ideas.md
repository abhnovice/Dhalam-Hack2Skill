# Dhalam — repository overview and ideas

Updated: 7 November 2026.

This document summarizes the source code currently present in `google-startup-hackathon`, distinguishes implemented features from prototypes, and records the main remaining work. Generated build outputs and machine-specific files are grouped rather than listed individually.

## 1. Product idea

**Dhalam is a mobile security-awareness and configuration dashboard.** It helps users review device protections, understand sensitive access, notice observable changes, and decide what to review in system settings.

The project started as a native Kotlin Android app and is being migrated to Flutter so that it can also run on iPhone and iPad. Both implementations remain in this workspace.

The current product emphasizes:

- Device-lock and biometric configuration checks.
- Android accessibility and app-permission visibility.
- Actionable findings with explicit unavailable and manual-review states.
- Local check history and changes between observations.
- Guided privacy reviews, local URL inspection, and reports users choose to share.
- Capture awareness while Dhalam is visible, where supported.

It does **not** claim to be an antivirus, prove that a device or URL is safe, measure passcode strength, or identify whether a legitimate permission is being abused. There is no overall security score.

## 2. Workspace layout

| Location | What it contains |
| --- | --- |
| `Dhalam-Hack2Skill/` | Original Android app: Kotlin, Jetpack Compose, Android APIs, WorkManager, and ObjectBox. |
| `Dhalam-Hack2Skill/app/src/main/` | App UI, telemetry, persistence, background scans, exports, security tools, manifest, and resources. |
| `Dhalam-Hack2Skill/app/src/test/` | Local JVM tests for exports, security tools, and a starter unit test. |
| `Dhalam-Hack2Skill/app/src/androidTest/` | Device/instrumentation tests, including ObjectBox persistence behavior. |
| `Dhalam-Hack2Skill/app/objectbox-models/` | ObjectBox schema/model metadata and a backup copy. |
| `Dhalam-Hack2Skill/ml/` | Python synthetic-data exploration, proposed ML dependencies, and duplicate font assets. |
| `dhalam_flutter/lib/` | Shared Dart application, dashboard, security tools, models, services, and theme. |
| `dhalam_flutter/android/` | Flutter Android host and Kotlin platform-channel implementations. |
| `dhalam_flutter/ios/` | Flutter iOS host, Swift native implementation, Xcode project, signing configuration, and resources. |
| `dhalam_flutter/test/` | Flutter service/domain and widget tests. |
| `dhalam_flutter/web/`, `macos/`, `windows/`, `linux/` | Generated Flutter platform scaffolding. Native security scanning is implemented only for Android and iOS. |
| `dhalam_flutter/README.md` | Setup, supported checks, limitations, validation commands, and security-tools documentation. |

The workspace also contains Gradle wrappers and build scripts, dependency lock/configuration files, launcher icons, launch screens, IDE metadata, local SDK paths, generated plugin registrants, caches/build directories, and an earlier Flutter diagnostic log. These support development; they are not separate product features.

## 3. Implemented feature comparison

| Feature | Original Kotlin Android | Flutter Android | Flutter iOS |
| --- | --- | --- | --- |
| Foreground security check | Yes; also runs on activity resume | Yes; dashboard button | Yes; platform-limited observations |
| Device-lock configuration | PIN/pattern/password status | Same native check | Passcode availability through LocalAuthentication |
| Biometric readiness | Android 10+; enrollment/availability | Same native check | Face ID/Touch ID availability, enrollment, and lockout states |
| OS information | Installed Android version | Installed Android version | Installed iOS/iPadOS version |
| Notification status/settings | Status, permission request, settings | Status and settings | Status, permission request, app settings |
| Accessibility inventory | Enabled and installed services, capabilities | Native collection; dashboard shows enabled services | Unavailable |
| Other apps' permission inventory | Visible packages and grant states; searchable UI | Visible packages and grant states; expandable UI | Unavailable |
| Developer-setting review | Limited readable signals plus manual guidance | Same collection and settings shortcuts | Manual review guidance |
| Actionable findings | Tools tab | Security tools screen | Security tools screen |
| Local summary history and comparisons | Latest 30 successful foreground checks | Same | Same |
| Manual privacy checklist | Android guidance | Android guidance | Apple guidance, including Safety Check instructions |
| Offline suspicious-link inspection | Yes | Yes | Yes |
| Preview and share text report | Android share sheet | Android share sheet | iOS share sheet |
| Capture awareness | Android 15+ recording callbacks | Android 15+ recording callbacks | Scene capture state on iOS 17+; screen capture state on older supported versions |
| Periodic background checks and change alerts | WorkManager implementation | Not migrated | Not implemented |
| ObjectBox security-fact storage | Yes | Not migrated | Not implemented |
| Detailed permission CSV exports | Yes | Not migrated | No cross-app inventory to export |

Android package visibility affects the inventory: it is not a guaranteed list of every installed app. The original Android manifest includes a launcher-app visibility query; the Flutter manifest does not currently duplicate that query, so the apps visible to the two versions may differ.

## 4. Security observations

### Device protections

- **Device lock:** reports configured, not configured, or unavailable. It does not read or assess the passcode itself.
- **Biometrics:** reports readiness, missing enrollment, temporary issues, or unavailable access. The check does not authenticate the user or identify enrolled people.
- **Operating system:** displays the installed version and directs the user to Software Update. It does not compare against a maintained release/support database.
- **Notifications:** reports Dhalam's own notification permission. Permission by itself does not create a monitoring service.

### Android accessibility and permissions

The scanner records service names, identifiers, and reported capabilities, such as reading window content, performing gestures, requesting key-event filtering, taking screenshots, controlling magnification, touch exploration, and fingerprint gestures.

Permission inventories group visible apps by requested permission and preserve granted, not granted, and unknown states. The Kotlin dashboard supports searching by permission, app label, or package name. Permission exports describe observed grants, not when a permission was used.

### Developer settings

Developer options, USB debugging, and wireless debugging are presented as settings to review manually. Where readable, the scanner reports “Stay awake while charging” and “Don't keep activities.” Android settings shortcuts are provided; iOS does not receive invented Android-style results.

## 5. Shared security tools

### Findings and next steps

Findings use **Configured**, **Needs attention**, **Unavailable**, and **Manual review** labels. They include an explanation, a suggested action, and the timestamp of the latest readable result retained in history.

Examples include setting a missing screen lock, reviewing biometrics, checking software updates, and stopping an unintended capture session before viewing private reports. Enabled accessibility access is not automatically labeled malicious.

### History and change detection

- Retains the most recent **30 successful foreground checks** locally.
- Compares the latest two snapshots only where both readings are available.
- Reports unavailable comparisons rather than treating missing data as a change to a safe state.
- Stores device/developer observations, notification and capture state, and accessibility/permission inventory summaries.
- Inventory summaries contain counts, not historical per-app grant records or individual service changes.
- Offers a confirmed “Clear local history and reviews” action.

The original Kotlin app's ObjectBox facts and background-scan baseline are separate. Clearing this summary journal does not clear those stores.

### Guided privacy review

Both versions include checklist items for app permissions, account security, software updates, device management/VPNs, and sharing/access. Completion is explicitly **user-confirmed**, timestamped, and reversible.

On iPhone, guidance includes Settings → Privacy & Security → Safety Check for supported iOS versions. Android guidance covers Google account security, location sharing, permission management, device administrators, and VPNs. Dhalam does not automatically verify or operate these settings.

### Suspicious-link inspector

The inspector parses a pasted HTTP/HTTPS URL locally and displays its destination hostname. Structural checks cover:

- Plain HTTP.
- User-information tricks before `@`.
- Unicode or punycode hostnames that merit a look-alike review.
- Numerous domain labels that can hide a misleading brand name on the left.
- Numeric/IP destinations and unusual ports.
- Invalid or ambiguous input, malformed ports, spaces, control characters, and backslashes.

The inspector does not open the link, fetch the website, follow redirects, check reputation, or inspect page content. Pasted links are not saved in the journal or included in reports. HTTPS and an absence of warnings are not proof of trustworthiness.

### Reports

Reports include the generation time, platform, observations and their timestamps, recent comparisons, suggested actions, manual-review confirmations, and scope limitations. Users generate a preview before opening the native share sheet and choosing a recipient. Sharing is not automatic.

The Kotlin app additionally retains two detailed CSV exports: an apps-and-permissions report and an app/permission/observation-timestamp log. CSV cells are quoted and spreadsheet-formula prefixes are neutralized.

### Capture awareness

Dhalam displays a warning when supported system APIs report an active capture involving its visible content. Android uses recording callbacks on Android 15+. Flutter iOS uses scene capture state on iOS 17+ and screen capture state on older supported iOS versions. Unsupported/unreadable states are shown as unavailable.

This is foreground awareness, not a system-wide surveillance detector. It does not detect every capture method, and older Android versions do not gain equivalent recording detection.

## 6. Architecture and storage

### Original Kotlin app

- `MainActivity.kt`: lifecycle, foreground scanning, notification permission/settings, capture monitoring, and scheduling the background worker.
- `SecurityDashboard.kt`: Compose dashboard with Permissions, Access, Device, and Tools tabs; permission search and CSV-export flow.
- `SecurityTelemetry.kt`: Android observations and shared Kotlin data types.
- `SecurityScanWorker.kt`: periodic WorkManager checks, scoped baselines, and quiet change notifications. The requested interval is 15 minutes; execution is subject to Android scheduling.
- `PermissionExport.kt`: permission CSV generation.
- `SecurityTools.kt`, `SecurityToolsPanel.kt`, `LinkInspector.kt`: findings, journal/comparison/report logic, tools UI, checklist, and URL parsing.
- `SecurityToolsNative.kt`: atomic local journal storage, capture callbacks, and Android sharing.
- `data/`: ObjectBox entities, observation-to-fact mapping, repository, and process-wide store access.

ObjectBox stores facts with stable keys, kinds, subjects, details, statuses, and observation times. Category watermarks reject stale writes. Unavailable categories retain prior facts; successful empty categories remove prior entries. The database represents the latest retained facts, not the new 30-check history.

The background worker separately compares normalized accessibility-service IDs and known permission-grant transitions. It saves its baseline and posts quiet notifications when changes are observed and notification permission allows it.

### Flutter app

- `lib/main.dart`: app entry point and light/dark theme selection.
- `lib/screens/dashboard_screen.dart`: scans, observed device information, permission/accessibility views, notification controls, and capture awareness.
- `lib/screens/security_tools_screen.dart`: Findings, History, Privacy review, Link inspector, and Report views.
- `lib/features/security_tools.dart`: findings, versioned journal, comparisons, review guidance, reports, and local URL inspection.
- `lib/models/security_models.dart`: Dart representations of native scan data, including nested platform-channel map decoding.
- `lib/services/`: scanning/settings and notification channels with error handling.
- `lib/theme/app_theme.dart`: shared Material light/dark visual styling.
- Android host Kotlin files implement native Android scanning and tools.
- `ios/Runner/AppDelegate.swift` implements iOS checks, notifications, settings, capture reads, journal storage, and sharing.

Three method channels connect Dart to native code:

| Channel | Responsibility |
| --- | --- |
| `com.dhalam.ap/security` | Collect observations and open supported settings. |
| `com.dhalam.ap/notifications` | Read notification permission and request/manage it. |
| `com.dhalam.ap/tools` | Load/save the journal, read capture status, open privacy/app settings, and share reports. |

The summary journal is a versioned JSON document. Android uses atomic writes in `noBackupFilesDir`. iOS uses an application-support directory excluded from backup and atomic writes with file protection. Corrupt history is surfaced rather than silently overwritten. Flutter's capture display refreshes while the app is in the foreground.

The current Dart dependencies are Flutter and Cupertino icons, with Flutter test/lint tooling. Native bridges are implemented directly rather than through additional scanning/sharing plugins.

## 7. UI and assets

The native app uses Jetpack Compose Material 3, teal/green light and dark palettes, IBM Plex Sans fonts, Fluent-style vector icons, and Android launcher assets. Separate theme files are also present.

Flutter uses a Material dashboard and tools screens with light/dark themes. Its platform folders contain launcher icons, launch screens/storyboards, manifests, and standard runner resources. These scaffolds do not establish full desktop/web security-feature support.

The Kotlin dashboard includes an **“Ask Dhalam · preview”** text field with a scam-related prompt. It currently stores input in UI state only: there is no response engine, LLM connection, or scam-classification flow behind it.

## 8. ML exploration — prototype only

`Dhalam-Hack2Skill/ml/train_model.py` creates a small pandas dataset of synthetic accessibility scenarios. It prints the rows, checks for conflicting risk labels for identical feature combinations, and prints class counts.

The features are old/new accessibility counts. Some identical combinations have both benign and risky labels, illustrating that counts alone cannot establish scam intent.

`ml/Requirements.txt` lists NumPy, pandas, scikit-learn, skl2onnx, ONNX, and ONNX Runtime for a possible training/export pipeline. **The current script does not train or evaluate a classifier, export an ONNX model, or integrate inference into either app.** No trained-model artifact or working AI assistant was found in the reviewed source.

## 9. Tests and verification

Present test coverage includes:

- Flutter: platform limitations, native-map decoding, visible failures/retries, history retention and reload, unavailable comparisons, corrupt-storage protection, save failures, link parsing, capture warnings, and report preview before sharing.
- Kotlin JVM: permission CSV behavior, link inspection, findings, unavailable comparisons, reports, and a starter unit test.
- Android instrumentation: ObjectBox persistence across reopen, stable IDs, stale-write rejection, unknown states, and unavailable-versus-empty category behavior; plus a starter instrumentation test.
- Generated iOS/macOS runner test scaffolding.

Latest verification completed during development before this summary:

| Check | Result |
| --- | --- |
| Flutter analysis | Passed |
| Flutter tests | 11 passed |
| Original Kotlin JVM tests | 8 passed |
| Flutter Android debug APK | Built |
| Original Kotlin Android debug APK | Built |
| Flutter iOS release app, without signing | Built |

These are recorded development results, not a fresh test run performed for this documentation change. Device instrumentation tests, capture start/stop behavior, actual share sheets, and the complete physical-device state matrix are not established by these results.

The original Android build needs a complete JDK 25, including `jlink`. A fresh Gradle process with explicit JDK selection worked around an IDE-bundled runtime missing that executable. The earlier local Xcode framework problem no longer blocked the latest unsigned iOS build.

## 10. Running and demonstrating the app

From `dhalam_flutter/`:

```bash
flutter pub get
flutter devices
flutter run -d YOUR_DEVICE_ID
```

For an iPhone build that can be opened from its home-screen icon:

```bash
flutter run --release -d YOUR_DEVICE_ID
```

Physical iOS deployment requires the appropriate Xcode signing team/bundle identifier, device trust, and Developer Mode. Android development deployment requires USB debugging and device authorization. See [the Flutter README](../flutter/README.md) for more setup information.

A practical demo flow is:

1. Run a security check and inspect device findings and unavailable categories.
2. Open Security tools and read an actionable finding.
3. Run a second check to demonstrate comparison and timestamped history.
4. Complete a manual privacy-review item and show its user-confirmed timestamp.
5. Inspect a sample such as `https://trusted.example@evil.example/login` without opening it.
6. Generate a report preview, then demonstrate the native share sheet.
7. On a supported physical device, start/stop a capture session while Dhalam is visible.

## 11. Source audit and actionable backlog

Reviewed against both application directories on **7 November 2026**. This pass inspected source, manifests, build configuration, and test files; it did not rerun builds or device tests. “Implemented” below means code exists, not that physical-device behavior has been verified. Priorities are a proposed implementation order, not commitments to add every feature.

### Migration gaps: native Android → Flutter

| Priority | Pending item | Source evidence / current coverage | Completion criteria |
| --- | --- | --- | --- |
| P1 | Align Android package visibility | Native [manifest](../app/src/main/AndroidManifest.xml) queries launcher activities; Flutter [manifest](../flutter/android/app/src/main/AndroidManifest.xml) only queries text-processing activities. | Add the intended launcher query and compare inventories on the same device. Continue describing results as visible packages, not all installed apps. |
| P1 | Permission search and package identity | Native [dashboard](../app/src/main/java/com/dhalam/ap/SecurityDashboard.kt) searches permissions, labels, and package IDs. Flutter [dashboard](../flutter/lib/screens/dashboard_screen.dart) lists expandable permissions and app labels without search or visible package IDs. | Search by permission, app label, and package ID; display IDs to distinguish apps with the same label; cover empty results and unknown grants. |
| P1 | Detailed CSV exports on Flutter Android | Native [PermissionExport.kt](../app/src/main/java/com/dhalam/ap/PermissionExport.kt) provides two CSV formats. Flutter only shares a summary text report. | Port both formats with observation timestamps, escaping, formula protection, and a user-selected save/share destination; handle cancellation and errors. Keep unavailable on iOS where inventory cannot be collected. |
| P1 | Decide refresh behavior after Settings | Native `MainActivity.onResume()` scans automatically. Flutter refreshes notifications/capture on resume, but device and permission results stay at the previous check until the user scans. | Either add a guarded resume scan or explicit recheck guidance. Verify no concurrent scans or unintended duplicate journal entries and that displayed timestamps remain truthful. |
| P2 | Expose installed accessibility services if useful | Both Android telemetry files collect enabled and installed services; Flutter renders only enabled services. | Decide whether installed-but-disabled services belong in the UI; if shown, clearly separate them from enabled access. |
| P2 | Android background checks and alert controls | Native [worker](../app/src/main/java/com/dhalam/ap/SecurityScanWorker.kt) exists and is scheduled at activity creation. No Flutter worker or scheduling bridge exists. | Define enable/disable controls and monitoring status, then migrate scheduling, baselines, retry behavior, and quiet notifications. Test denied notifications and unavailable observations; do not promise exact 15-minute execution. |
| P2 | Decide whether Flutter needs a latest-facts store | Native [data layer](../app/src/main/java/com/dhalam/ap/data/SecurityRepository.kt) retains ObjectBox facts; Flutter has only its summary journal. | Either document summary-only storage as intentional or implement equivalent stable keys, stale-write rejection, and unavailable-versus-empty handling. ObjectBox itself is not a requirement. |
| P2 | Decide installation/data migration strategy | Android application IDs differ: native `com.dhalam.ap`, Flutter `com.dhalam.dhalam`, as configured in their app Gradle files. No cross-app import flow was found. | Decide whether Flutter is a separate install or a successor release. Document data continuity; if transfer is needed, implement and test an explicit migration path. Source-code migration does not transfer existing app data. |

### Work missing or incomplete across the product

- [ ] **P1 — Validate native behavior on devices.** Record device/OS/build and pass/fail evidence for lock and biometric states, notification denial, settings return, capture start/stop, process restart, and report sharing on Android, iPhone, and iPad. An unsigned build or mocked channel test does not establish these behaviors.
- [ ] **P1 — Resolve native backup and deletion scope.** The native manifest enables backup; [backup rules](../app/src/main/res/xml/backup_rules.xml) and [data-extraction rules](../app/src/main/res/xml/data_extraction_rules.xml) remain templates without explicit store exclusions. The summary journal is separately excluded, but the ObjectBox facts and worker preferences need an explicit backup/restore policy. Define whether a full reset should also clear facts and baselines, and coordinate it with running workers to avoid immediate repopulation. Actual backup behavior remains unverified.
- [ ] **P1 — Finish release configuration.** Flutter Android release builds explicitly use debug signing in [build.gradle.kts](../flutter/android/app/build.gradle.kts). Configure production signing outside source control, confirm identifiers/versioning and iOS provisioning, and validate signed release artifacts. Existing compile results are not release approval.
- [ ] **P2 — Add focused tests for uncovered boundaries.** No worker/baseline tests were found in the native JVM suite; Flutter has no Android host test directories or `integration_test/` suite, and its iOS `RunnerTests` is a starter test. Cover unavailable/empty reads, permission transitions, baseline persistence/retry, native channel contracts, settings failures, and capture lifecycle. Existing Dart tests mock native behavior.
- [ ] **P2 — Automate reproducible checks.** Neither app directory has a `.github/workflows/` directory. Add CI for Flutter analysis/tests and Kotlin unit tests, with pinned toolchains and build checks where available. Keep physical-device checks as a separately recorded validation step.
- [ ] **P2 — Prevent Android scanner drift.** The two `SecurityTelemetry.kt` files currently differ only in their package declaration. Share the collector or establish common contract fixtures so future fixes remain consistent across the two hosts.
- [ ] **P2 — Define detailed history before collecting it.** Current journals retain counts, while the native worker retains only a comparison baseline. Neither supplies a browsable per-app/per-service event history. Specify retention, deletion, visibility-change handling, and unknown states before implementing richer history.
- [ ] **P2 — Validate usability and accessibility.** Record screen-reader, large-text, contrast, small-screen, and long-inventory checks for both UIs; current tests do not establish this coverage.

### Optional product ideas, not migration blockers

- **Ask Dhalam:** only the native preview text field exists. Define supported questions, evidence and data handling, then build an answer flow with failure states and tests. There is no Flutter assistant UI or working answer engine to port.
- **ML inference:** the Python script explores synthetic data and conflicting labels; neither app has a trained model or inference integration. Establish useful features, reliable labels, and evaluation criteria first.
- **Online URL intelligence:** neither implementation fetches links, follows redirects, or checks reputation. Treat any such service as a separately scoped network feature with explicit handling of sensitive pasted URLs.
- **Backend, accounts, synchronization, or Gemini integration:** absent from the reviewed application source and not needed for the existing local dashboard. Define an actual use case before adding them.
- **Desktop/web scanning:** generated Flutter targets exist, but the scanner explicitly supports Android and iOS only. Decide whether to remove unsupported targets from the product scope or design platform-specific implementations.

**Suggested next implementation slice:** Android visibility parity, permission search with package IDs, then detailed CSV exports. These close concrete gaps against the native app without depending on an AI service or a new storage/background architecture. Keep iOS inventory limitations explicit.

No backend API, cloud synchronization, user-login service, trained AI model, or Google/Gemini integration is implemented in the reviewed application source. The working product currently relies on local observations, local processing/storage, manual guidance, and user-initiated sharing.
