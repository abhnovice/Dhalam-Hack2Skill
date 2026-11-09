# Test Dhalam on Android, iOS, or Emulator

Dhalam keeps one current demo package in [`build/apks/`](../../build/apks).
Native Android supports hosted Google authentication when its signing key
matches the registered Android OAuth client. Flutter is built from source and
still needs a local backend for AI:

- [`dhalam-native-android.apk`](../../build/apks/dhalam-native-android.apk) — Native Android / Jetpack Compose application (`com.dhalam.ap`)

The native Android APK was rebuilt on 9 November 2026 with six-language screen
translations, voice controls, Noto Sans and a configured Web OAuth client. It is
signed with the Android debug key for demo installation. Build Flutter from
source on Android or iOS; iOS device installation requires Apple signing.
Rebuild the shareable native APK with `python3 tools/build_native_demo.py` from
the repository root; it validates the OAuth client ID before updating the file.
Cloud Run was deployed in mobile-auth mode on 9 November 2026. Its health endpoint
is public, but AI requests require a verified Google ID token from an allowed
account. The current native emulator debug build points to this hosted URL.
Flutter clients have not yet been updated for this authentication mode.

## Flutter with a local backend

Run these commands from `Dhalam-Hack2Skill/` in a terminal:

```bash
conda activate fastapi-rag
python -m uvicorn backend.app:app --host 127.0.0.1 --port 18765 --no-access-log
```

Leave that terminal running. If it reports that the port is occupied, the backend
may already be running. In another terminal:

```bash
adb devices
adb -s emulator-5554 reverse tcp:18765 tcp:18765
cd flutter
flutter run -d emulator-5554
```

Use the actual serial shown by `adb devices` if it differs. Reapply `adb reverse`
after restarting the emulator. Launch the AVD from Android Studio Device Manager
if no emulator is listed. No emulator data wipe is needed.

The Android **debug** build now defaults to `http://127.0.0.1:18765` in **Demo
connection**. Leave the token field empty for this local server. An explicit
`DHALAM_API_URL` Dart define overrides that default. Release builds have no local
default and do not include the debug HTTP network policy. Keep backend `.env`
and Google credentials on your Mac; never paste them into the app.

The backend uses existing Vertex AI credentials. Each Analyze action uses cloud
quota/credits; local security checks, history and capture warnings do not.
No billing upgrade or cloud deployment is required for this setup.

## Suggested interaction sequence

1. Open Dhalam. It starts directly in the chat, with a message box and photo
   controls. Tap a starter such as **Someone wants money**, or paste your own
   synthetic example from [sample-content.md](sample-content.md).
2. Tap **Check**. Review the short consent dialog, then choose **Send for check**.
   No cloud call happens if you choose **Not now**. The confirmation applies to
   this submission, including earlier user messages in this chat.
3. Read the large headline and first action. Red means pause for a concerning
   request, amber means more checking is needed, and the neutral low-concern
   result explicitly says it does not prove safety. Icons and words carry the
   meaning as well as color.
4. Tap **Why? Show details** for evidence, all actions, reputation status and
   limitations. **Preview incident report** opens a review before sharing.
5. Ask a follow-up in the same message box. Up to six submissions stay in memory;
   earlier user text is included with your consent. Earlier photos are not sent
   again. **Chat options → New chat** clears this in-memory conversation.
6. Use the photo button to choose [synthetic-message.png](synthetic-message.png).
   First copy it to the emulator:

   ```bash
   adb push docs/interact-with-app/synthetic-message.png /sdcard/Pictures/synthetic-message.png
   ```

   Use Browse in Android's picker if it does not appear under Photos. Flutter
   also offers Take photo.
7. **Chat options → Demo connection** holds the backend URL/token. **Chat options
   → Device checks** opens the existing device dashboard. Run a security check
   there to inspect local signals. The chat does not upload that inventory.
8. Return to the dashboard and enable **Review access changes in the background**
   if desired. Notification permission is separate. The first scheduled check
   establishes a baseline; subsequent known changes can produce an alert.
   Scheduling is approximately every 15 minutes or later, not immediate.
   Turn it off to clear the background baseline.
9. Use the translate icon in the chat or dashboard app bar to select English,
   Hindi, Marathi, Bengali, Tamil or Telugu. Visit **Device checks** and
   **Security tools** to see the same choice applied throughout the app.
   Findings keep their stored English state and update on screen. Speech input
   and read-aloud require speech data for the selected language on the device.
10. For capture testing, use Android's **Screen record** Quick Settings tile while
   viewing Dhalam. You may need to edit the tiles to add it. Stop through Android's
   recording controls. A supported capture session should show Coercion Shield;
   the emulator host's video recorder is not a reliable substitute for Android's
   screen-sharing APIs. Real-device capture validation is still required.

## Troubleshooting

- Connection error: keep the backend terminal running and repeat `adb reverse`.
  `curl http://127.0.0.1:18765/health` on the Mac should return `{"status":"ok"}`.
- Hosted 401: link the allowed Google account in the native app. The Flutter
  client does not yet send a Google ID token to Cloud Run.
- Hosted 403: the Google account is not on the backend allowlist.
- Model unavailable: check Google ADC with your existing account. If necessary,
  run `gcloud auth application-default login` and retry. Do not paste token output
  or backend secrets into chat, screenshots or the repository.
- Reputation not configured: expected; Gemini evaluates the URL text, but the
  optional Safe Browsing adapter has no configured key.
- A low score is not proof of safety. Gemini does not authenticate documents,
  retrieve past incident records, intercept calls or stop payments.

## Which version is current?

| Capability | Flutter Android (local backend) | Flutter iOS (local backend) | Native Android (hosted demo) |
| --- | --- | --- | --- |
| Shared text/image/URL triage UI | Yes | Yes | Scam chat plus separate device-facts `/ask` UI |
| Local access inventory | Android-visible apps/services | Restricted by iOS; unavailable | Android inventory |
| Opt-in periodic local checks | Yes | No | Separate native worker |
| Foreground capture awareness | Supported Android versions | Supported iOS APIs | Separate native implementation |
| Validation | Chat tests, debug build and emulator setup | Shared chat source; device tests pending | Debug build and JVM tests; emulator UI verification |

Flutter Android and iOS share Dart source; OS capabilities differ. The native app now also has text/URL/image scam chat, explicit consent, contextual
follow-ups, capture warning and incident reports. Its authentication and worker
lifecycle remain different; it is not an identical copy of Flutter. Native photo
input uses the system picker; Flutter additionally has a camera shortcut.

## Open the native Android alternative

The native app package is `com.dhalam.ap`; Flutter is `com.dhalam.dhalam`.
The native sign-in screen uses Google/biometric flow and
debug-only temporary developer login. See `app/src/main/java/com/dhalam/ap/SignInScreen.kt`
for the existing local test login; it is not a cloud credential. After unlocking,
**Scam check** is the default tab. Use the same sample content with the hosted
backend after Google sign-in. Build and install with `./gradlew :app:installDebug`;
see [view_app.md](../../view_app.md) for OAuth and emulator setup.

```bash
adb -s emulator-5554 shell am start -n com.dhalam.ap/.MainActivity
# Return to Flutter:
adb -s emulator-5554 shell am start -n com.dhalam.dhalam/.MainActivity
```

## Verified in this setup

- Flutter chat launches on the API 36 emulator with a loopback backend connection.
- The backend returned HTTP 200 with a live Gemini assessment after restart.
- On 9 November 2026, 66 backend tests passed; earlier runs had 30 Flutter tests and 34 native JVM tests
  passed; Flutter analysis reported no issues. The Flutter suite covers
  language switching, consent, contextual follow-ups and enlarged text.
- A previous run passed 10 native emulator UI tests. Both current Android APKs
  installed and launched on the API 36 emulator. Hindi switching was checked in
  Flutter chat/dashboard and native sign-in/dashboard/Tools. The native Tools
  screen showed translated finding states while retaining the stored English keys.
- Native consent cancellation and Google sign-in gating were exercised by UI
  tests. On 9 November, Google sign-in and one synthetic hosted scam check
  succeeded in the API 36 debug emulator. Biometric unlock and physical-device
  capture behavior still need separate verification.

## Try reference-backed advice

Send a synthetic message such as “A caller claiming to be from RBI says my account
will be frozen unless I send a security deposit now.” Open **Why? Show details**
and scroll to **Sources for this advice**. If Gemini used retrieved guidance,
you will see official source titles, summaries, dates and selectable URLs. The
report includes the same references. Unavailable retrieval and no matching
reference are shown explicitly. An advisory match is not proof of fraud.

The local backend uses Google gemini-embedding-001 with a SQLite/sqlite-vec index
of five public RBI summaries. It does not store your conversations or search
private incident records. The deployed Cloud Run backend also has reference
retrieval; the native debug app is the hosted test target.

## Hosted demo and device testing

Use the native Android APK or build it from source for hosted AI. Its signing
key must match the registered Android OAuth client. Flutter needs its own
Google ID token flow before it can use hosted AI; physical-device verification
remains pending. The iOS archive is older than the current source.

### WhatsApp test bot

The direct Meta test-number bot is a separate entry point to the same hosted
triage backend. Approved testers can send `hello` for instructions, then send
`CHECK: <fictional message>` for an assessment. Sharing the test number alone
does not grant access: each tester must be verified in Meta's **To** test
recipients and added to the backend sender allowlist. See the
[WhatsApp setup and tester instructions](whatsapp-bot.md).

### Authentication and Session Handling
- **Google Sign-In**: Powered by Android Credential Manager. When signing in or asking questions, the app interactively prompts for your Google account if auto-selection has not occurred yet.
- **Biometric Unlock**: You can unlock local features with a device fingerprint / face unlock. Link the allowed Google account once before using hosted AI; later visits can refresh that account through Android Credential Manager.
- **Log out**: Available on every native tab to securely clear in-memory sessions and cancel background scanning.
- **AskDhalam & Permission Facts**: Tap **Scan now** to record local device facts into ObjectBox, then ask questions like *"Which visible apps have camera permission?"*. Answers cite specific fact IDs from the local scan.
- **Scam Check**: Submit suspicious messages or screenshot photos. Gemini 3.8 Flash evaluates threats and retrieves official RBI guidance citations with advice and actionable next steps.


## Inspect links inside chat

Paste an HTTP(S) link into the message box and tap **Inspect link locally**.
The dialog shows the actual hostname, scheme, path, and structural warnings for
up to five distinct links. It runs before consent or submission and makes no
network request. Editing the message refreshes the available inspection.
Native Ask Dhalam also offers this action in its device-question input.
For general scam-pattern analysis, use Scam check and its existing consent flow.
The scam-chat transcript retains local inspection on submitted messages.

The local check does not visit websites, follow redirects, or verify reputation.
AI scam triage can use general model knowledge and relevant guidance from the
five bundled RBI advisory summaries when retrieval is available. It does not
search a comprehensive incident database or learn automatically from past chats.
