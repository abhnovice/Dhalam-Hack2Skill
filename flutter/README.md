# Dhalam AI — Flutter

Android and iOS scam-triage and local access-review app. The shared backend is in `../backend`; configuration is in its `.env`.

Run `flutter pub get`, then `flutter run -d <device-id>` from this directory.
Use `flutter devices` to list devices. For a physical iPhone, configure the Runner
signing team and a unique bundle identifier in `ios/Runner.xcworkspace` using Xcode.

The app-wide translate menu offers English, Hindi, Marathi, Bengali, Tamil and
Telugu on chat, dashboard and tools screens. Finding states remain unchanged in
local history and are translated when drawn. Translation source lives in
`../tools/i18n/*.json`; regenerate both clients with
`python3 ../tools/i18n/generate.py`. Noto Sans comes from Google Fonts and is
cached after its first download. Voice input and read-aloud use platform speech
services; each language needs compatible speech data on the device.

## Available checks

| Check | iOS | Android |
| --- | --- | --- |
| Device lock configured | LocalAuthentication passcode availability | KeyguardManager PIN/pattern/password status |
| Biometric readiness | Face ID/Touch ID availability, enrollment and lockout | BiometricManager availability/enrollment on Android 10+ |
| Operating system | Installed version; manual update guidance | Installed version; manual update guidance |
| Dhalam notification permission | Read/request permission or open app settings | Read permission and open notification settings |
| Accessibility service inventory | Unavailable | Enabled and installed services |
| Other apps' permissions | Unavailable | Packages visible to this app; not a complete inventory |
| Developer settings | Manual review only | Available signals and settings shortcut |

Device lock and biometric checks do not display an authentication prompt. They
cannot measure passcode strength, identify enrolled people, prove the device is
safe, or detect malware. Unavailable data is not interpreted as a passing check.
The OS version is informational, not a claim that the device is up to date.

The equivalent device checks also appear in the original Kotlin app's Device tab
and are included in its security fact persistence. Flutter has a foreground check flow with local summary history and text reports.
Android also supports opt-in periodic local access checks (approximately every
15 minutes or later, subject to OS scheduling and battery constraints). The first
check establishes a baseline; later checks compare enabled services and known
permission grants. Notifications require separate permission. Disabling checks
clears this baseline. Detailed permission CSV exports remain in the native app.

## Validation

- `flutter analyze`
- `flutter test`
- `flutter build ios --simulator --debug`
- `flutter build apk --debug`

On physical devices, check a configured/unconfigured screen lock, enrolled and
unenrolled biometrics, temporary biometric lockout, and notification permission
allowed/denied. Return from Settings and verify notification status refreshes;
device observations refresh after returning from a Settings shortcut opened inside Dhalam. Use Run security check after other changes.

The Android release APK was rebuilt on 9 November 2026 and signed with the
Android debug key for demo use. The iOS `.ipa` predates the voice and language
changes. A current device package requires Apple signing and physical-device checks.

## Security tools

Open **Security tools** in Flutter, or the **Tools** tab in the original Kotlin
app. Both versions include:

- Actionable findings with Configured, Needs attention, Unavailable and Manual
  review labels, plus the last readable result timestamp for each check.
- The latest 30 successful foreground checks, persisted locally outside OS backup.
  Changes compare the two most recent snapshots only when both readings are
  available. New Flutter snapshots retain service identifiers and app permission states;
  older records remain readable as summaries. Android package visibility can affect comparisons.
- A manual privacy review with timestamped, user-confirmed checklist items.
  iPhone guidance includes Safety Check (iOS 16+); Android guidance covers Google
  account/location sharing and permission management. These settings are not
  automatically verified or changed.
- An offline link inspector showing the hostname and structural warning signs.
  It never opens or fetches the URL, checks reputation, or persists pasted links.
  An absence of warnings and HTTPS are not guarantees of trustworthiness.
- A plain-text report preview followed by the native share sheet. Reports include
  observations, timestamps, limitations and manual-review confirmations. The
  user chooses the recipient; no report is sent automatically.
- Live capture awareness while Dhalam is visible: iOS scene capture status (iOS 17+)
  with screen-based fallback for older supported iOS; Android recording callbacks
  on Android 15+. Older Android releases show Unavailable. This is not detection
  of every recording method or capture of other apps.

Use **History → Clear local history and reviews** to reset stored summaries and
checklist confirmations. In the original Android app this does not delete the
separate pre-existing ObjectBox facts or background-worker baseline.

After installing a new build, run a security check to populate history. Recheck
after changing device settings. Test capture start/stop while viewing both the
dashboard and report preview; verify the share sheet on iPhone, iPad and Android.

For the original Kotlin project's unit tests, use a complete JDK 25 (including
`jlink`). If Gradle selects an IDE-bundled JRE instead, set `JAVA_HOME` to a full
JDK and run with `--no-daemon`, `-Dorg.gradle.java.installations.auto-detect=false`
and `-Dorg.gradle.java.installations.paths=<full-JDK-home>`.

## Scam triage (Android and iOS)

The app opens directly into chat. Paste a message/link or select a
JPEG, PNG or WebP screenshot (up to 4 MiB). Camera and library selection use the
native picker. Review/redact private details, tap Check, then explicitly confirm
each submission. Follow-up checks include earlier user text in this chat (up to
six submissions); earlier images are not resent. New chat clears the in-memory
conversation. Submissions are not saved in the local journal.
Device inventories are never included in Flutter cloud requests.

The prebuilt release packages point to Cloud Run
(`https://dhalam-api-xxpghctsqq-el.a.run.app`), but hosted AI calls return 401
because Flutter does not yet send the required Google ID token. For an AI demo,
run a local backend and use the debug build as described in
[the interaction guide](../docs/interact-with-app/README.md).
Prebuilt artifacts are in `../build/apks/dhalam-flutter-android.apk` and `../build/apks/dhalam-flutter-ios.ipa`.

Results distinguish the uncalibrated AI concern score, extracted evidence,
actions, and actual URL reputation status. A model cannot authenticate a legal
notice or determine a sender's identity from an image. Report preview combines
user-submitted text/link, AI assessment and timestamped local observations.
The share sheet is user-directed. The draft is not an official complaint or
forensic evidence certification, and original screenshots must be attached
separately if appropriate.

**Coercion Shield** is a prominent foreground warning while Dhalam is visible in
a supported capture session, updated by native events with a two-second polling
fallback. Newer native events take precedence over pending polling responses. Android
can additionally show that accessibility services were enabled at the last
check. It cannot determine which other app is foreground, stop someone else's
recording, or infer fraudulent intent from assistive technology. iOS inventories
remain unavailable; older Android capture detection remains unavailable.

Android package queries now include launcher activities; this is still limited
visibility. Search filters match names, package identifiers and permission names.
Sensitive-permission filtering is a review aid, not a malware classification.

## Current verification and release limits

- Regression tests cover consent, no telemetry in cloud requests, capture
  transitions and event/poll ordering, opt-in background controls, and
  identity-preserving journal comparisons.
- `flutter analyze` is clean and all 30 Flutter tests passed on 9 November 2026.
- Android release APK rebuilt on 9 November 2026; the iOS device archive is stale.
- iOS minimum deployment target is 15.0 to match the installed Xcode 27 toolchain.
- Physical camera/gallery, capture callbacks, Settings return and sharing still
  need testing on real Android and iOS devices. An unsigned iOS build is not installable.
- Android release signing still needs a production key. Demo IAM tokens are not
  consumer sign-in. Public launch requires mobile identity and abuse controls.
- Flutter Android includes opt-in periodic local access checks. Capture
  awareness remains foreground-only; iOS has no periodic access inventory worker. No bank integration, voice triage, on-device Gemma inference,
  payment blocking or official complaint submission is claimed.
- Scam triage can now retrieve official RBI reference guidance through the backend.
  Sources and fallback status appear under Why? and in incident reports; these
  references do not establish a match to a specific past incident.
- Cloud Run now requires a verified Google ID token from an allowed account for
  AI requests. This Flutter client does not yet provide that authentication flow,
  so its hosted AI requests return 401 until the client is updated.
