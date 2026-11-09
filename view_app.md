# View the apps

The current **native Android debug build** is the hosted AI demo. It signs in
with Google and calls `https://dhalam-api-xxpghctsqq-el.a.run.app`. The Flutter
clients still work with a local backend but do not yet send the Google ID token
required by hosted Cloud Run. Run commands from `Dhalam-Hack2Skill/` unless a
`cd` says otherwise.

## One-time setup

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

These commands work when pasted into interactive zsh. That shell may treat
lines beginning with `#` as commands unless `setopt interactivecomments` is on.

## Start a device first

Android emulator (leave this terminal open, or add `&` to background it):

```bash
emulator -avd Medium_Phone_API_36.0
adb devices
```

Wait until `adb devices` lists `emulator-5554` as `device`.

iOS simulator:

```bash
open -a Simulator
flutter devices
```

A physical iPhone or Android phone also works once it is plugged in and
trusted. `flutter devices` lists the id to use below.

## Make sure you are running the current version

Starting the emulator does **not** update the apps on it. It keeps whatever was
installed last, and both apps report `1.0` / `1.0.0` either way, so the version
number cannot tell old from new. Always install explicitly with one of these.

**For the hosted demo, build and install native Android from source:**

```bash
./gradlew :app:installDebug
adb shell am start -n com.dhalam.ap/.MainActivity
```

The checked-in `gradle.properties` has the hosted URL. Set the Dhalam AI Web
OAuth client ID through `DHALAM_GOOGLE_WEB_CLIENT_ID` in your environment or
pass `-PDHALAM_GOOGLE_WEB_CLIENT_ID=<client ID>` when building. Google sign-in
and one synthetic scam check succeeded on the
API 36 emulator on 9 November 2026. The emulator has no fingerprint enrolled.

The demo APKs can also be installed directly. Use the native APK for hosted AI;
Flutter still needs the local backend for AI checks:

```bash
adb install -r build/apks/dhalam-native-android.apk
adb install -r build/apks/dhalam-flutter-android.apk
```

`-r` replaces the installed app and keeps its data. Both prebuilt APKs and a
debug build use the same signing key, so they replace each other without an
uninstall. If Android still refuses with a signature error, run
`adb uninstall com.dhalam.ap` (native) or `adb uninstall com.dhalam.dhalam`
(Flutter) and install again.

`flutter run` and `./gradlew :app:installDebug` rebuild their respective clients.

**Check what is on the device.** The commands below find the installed
`base.apk` path automatically, pull it, and check for a current feature.
They work from `Dhalam-Hack2Skill/`. A result of `0` can mean an older build:

```bash
native_apk_path=$(adb shell pm path com.dhalam.ap | sed -n 's/^package:\(.*\/base\.apk\)$/\1/p')
adb pull "$native_apk_path" /tmp/native.apk
unzip -p /tmp/native.apk 'classes*.dex' | grep -ac "Speak your message"
```

```bash
flutter_apk_path=$(adb shell pm path com.dhalam.dhalam | sed -n 's/^package:\(.*\/base\.apk\)$/\1/p')
adb pull "$flutter_apk_path" /tmp/flutter.apk
unzip -p /tmp/flutter.apk 'lib/*' 'assets/*' | grep -ac "Speak your message"
```

The native APK currently prints `1`. Flutter may print more than `1` because
the same label appears in multiple packaged assets.

Quick visual check: the chat has a **translate** button (language menu)
and a **mic** button, and icons use the rounded Material Symbols style. The old
build has neither.

This text check confirms the voice feature, not the newer OAuth configuration.

---

## 1. Flutter on Android

```bash
cd flutter
flutter pub get
flutter run -d emulator-5554
```

Use `-d <id>` from `flutter devices` for a real phone.
The Flutter client cannot call the current hosted AI service until Google ID
token sign-in is implemented there. Use the local backend for Flutter testing.

## 2. Flutter on iOS

```bash
cd flutter
flutter pub get
flutter run -d <ios-device-or-simulator-id>
```

A physical iPhone needs a development team set in Xcode first:
`open ios/Runner.xcworkspace`, select **Runner**, then **Signing & Capabilities**.
The simulator needs no signing.
Hosted AI has the same Google sign-in limitation as Flutter Android. Start the
local backend below and set **Demo connection** to `http://127.0.0.1:18765` in
the iOS simulator. A physical device needs an address reachable from that device.

## 3. Native Android (Compose)

```bash
./gradlew :app:installDebug \
  -PDHALAM_BACKEND_URL=https://dhalam-api-xxpghctsqq-el.a.run.app
adb shell monkey -p com.dhalam.ap -c android.intent.category.LAUNCHER 1
```

The checked-in `gradle.properties` currently points native Android builds at the
hosted Cloud Run URL. An explicit `-PDHALAM_BACKEND_URL` overrides it. To use a
backend running on your Mac in the emulator, pass
`-PDHALAM_BACKEND_URL=http://10.0.2.2:18765` (see "run the backend locally" below).
The Google client ID is read from the `DHALAM_GOOGLE_WEB_CLIENT_ID` environment
variable or an explicit `-P` Gradle property. Do not commit it to
`gradle.properties`.

### Finish Google sign-in setup for this emulator build

The native debug build and backend use a Web OAuth client in the Dhalam AI
project (`1052479619555`). The matching Android client was created in
[Google Auth Platform → Clients](https://console.cloud.google.com/auth/clients?project=project-075ab0b6-c39b-4175-9b1).
Use package `com.dhalam.ap` and SHA-1
`E5:6B:CD:CA:9C:66:0D:41:53:4A:3B:34:D3:27:2C:AF:90:6D:A5:F6`.
This SHA-1 is from the current Mac's Android debug keystore; run
`./gradlew :app:signingReport` for another signing key. The Web application
client ID is supplied as `DHALAM_GOOGLE_WEB_CLIENT_ID` to the native build,
stored as `GOOGLE_WEB_CLIENT_ID` in ignored `backend/.env`, and configured in
the deployed Cloud Run service.
Tap **Sign in with Google**, choose the allowed emulator
account, and accept Google's consent prompt. The native
app then sends that Google ID token to the hosted backend. Temporary developer
or biometric unlock can open local screens first; use **Account → Google account**
to link once for hosted AI.

The current emulator has no fingerprint or supported face enrolled. To test
biometric unlock, enroll one in Android **Settings → Security & privacy → Device
unlock**, then relaunch Dhalam and choose **Unlock with biometrics**. The app
requires a successful device biometric prompt before opening local screens;
hosted AI then refreshes the previously linked Google account.

Or open the repository root in Android Studio and press Run on the `app`
configuration. The checked-in Gradle properties already select the hosted URL.

### Temporary developer sign-in (native Android, debug builds only)

The native debug app opens on a sign-in screen. To inspect local features before
linking Google, use the temporary login:

1. Tap **Temporary developer sign-in**.
2. Enter:

   | Field | Value |
   | --- | --- |
   | Username | `abhyudayamarya` |
   | Password | `dummydhalam` |

3. Tap to confirm. The account shows as "Temporary · abhyudayamarya".

Notes:

- This is a dummy, local-only login for development. It unlocks screens and
  local scans. Hosted AI requires linking the allowed Google account once from
  **Account → Google account**; later biometric or temporary unlocks can refresh
  that account through Android Credential Manager.
- The button and the credentials work **only in debug builds**
  (`./gradlew :app:installDebug` or Android Studio Run). Release builds, including
  the prebuilt `dhalam-native-android.apk`, hide the button and reject the login.
  Use **Unlock with biometrics** or **Sign in with Google** there.
- It is marked `TEMPORARY_LOGIN` in the source and is for development only. Do
  not reuse these values anywhere real.
- The Flutter apps have no sign-in screen and open straight into the chat.

---

## Install the rebuilt Android packages (no build)

The Android builds in [`build/apks/`](build/apks) were rebuilt on 9 November
2026 from the current native and Flutter source. The backend test suite has 66
passing tests; earlier runs had 30 Flutter and 34 native JVM tests. Both APKs
use the Android debug signing configuration for demo installation:

```bash
adb install -r build/apks/dhalam-native-android.apk
adb install -r build/apks/dhalam-flutter-android.apk
```

| Package | Status |
| --- | --- |
| `dhalam-native-android.apk` | Current debug build with hosted OAuth configuration and temporary developer sign-in. Google sign-in needs the matching Android OAuth signing key. |
| `dhalam-flutter-android.apk` | Current Flutter release build; lacks Google ID token sign-in for hosted AI. |
| `dhalam-flutter-ios.ipa` | **Out of date.** Built before these changes; it has no voice, languages, new fonts or icons. Run the iOS app from source with `flutter run` (section 2) to see the current version. |

Both Android packages point at the hosted Cloud Run backend. The hosted service
requires a verified Google ID token from an allowed account for AI requests;
use the native APK for that demo. Flutter AI checks still need a local backend.
Voice input and read-aloud have automated fake-based coverage; real-device
speech data remains to be checked.

## Try the new chat features (all three apps)

Flutter Android and Flutter iOS share one codebase. The native Android app has
the same features: language menu, **Speak your message** button (opens Android's
speech screen) and **Read aloud**.

- Tap the **translate** icon in the top bar to switch between English, Hindi,
  Marathi, Bengali, Tamil and Telugu. The selection applies to chat, dashboard,
  findings and tools. Journal states remain stored in English and translate
  when displayed. `python3 tools/i18n/generate.py` refreshes the Flutter and
  native tables from `tools/i18n/*.json`.
- Tap the **mic** icon next to the camera, speak, then review the text and tap
  **Check**. Allow the microphone permission when asked.
- After an answer appears, tap **Read aloud** (or its translated label).
- On the emulator, enable a microphone in the emulator's extended controls
  (**... > Microphone**). Voice for a language also needs that language's
  speech data installed on the device.

## Optional: run the backend locally

```bash
conda activate fastapi-rag
python -m pip install -r backend/requirements-dev.txt
python -m uvicorn backend.app:app --host 127.0.0.1 --port 18765 --no-access-log
```

For a local backend on an Android emulator, run
`adb reverse tcp:18765 tcp:18765` and omit `--dart-define`; Flutter debug builds
then use `http://127.0.0.1:18765`.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `No supported devices found with name or id matching 'emulator-5554'` | The emulator is not running or not finished booting. Start it, then check `adb devices`. |
| `Unable to locate a Java Runtime` | Set `JAVA_HOME` as in the setup section. |
| First launch shows a plain system font | Flutter downloads Noto Sans on first launch and caches it. Connect to the internet and relaunch. |
| Voice says it is unavailable | The device has no speech recogniser or voice for that language. Install it in the device's language and voice settings. |
