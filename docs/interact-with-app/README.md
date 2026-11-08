# Test Dhalam on the Android emulator

Use **Flutter Android** (`com.dhalam.dhalam`), not the older native Android app
(`com.dhalam.ap`). The configured virtual device is `Medium_Phone_API_36.0`.

## Start or reconnect

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
   It is copied to the emulator’s Pictures directory. Use Browse in Android's
   picker if it does not appear under Photos. Flutter also offers Take photo.
7. **Chat options → Demo connection** holds the backend URL/token. **Chat options
   → Device checks** opens the existing device dashboard. Run a security check
   there to inspect local signals. The chat does not upload that inventory.
8. Return to the dashboard and enable **Review access changes in the background**
   if desired. Notification permission is separate. The first scheduled check
   establishes a baseline; subsequent known changes can produce an alert.
   Scheduling is approximately every 15 minutes or later, not immediate.
   Turn it off to clear the background baseline.
9. For capture testing, use Android's **Screen record** Quick Settings tile while
   viewing Dhalam. You may need to edit the tiles to add it. Stop through Android's
   recording controls. A supported capture session should show Coercion Shield;
   the emulator host's video recorder is not a reliable substitute for Android's
   screen-sharing APIs. Real-device capture validation is still required.

## Troubleshooting

- Connection error: keep the backend terminal running and repeat `adb reverse`.
  `curl http://127.0.0.1:18765/health` on the Mac should return `{"status":"ok"}`.
- 401/403: clear any Cloud Run token and verify the local URL. For a deliberately
  remote Cloud Run test, use that service URL and a fresh IAM identity token.
- Model unavailable: check Google ADC with your existing account. If necessary,
  run `gcloud auth application-default login` and retry. Do not paste token output
  or backend secrets into chat, screenshots or the repository.
- Reputation not configured: expected; Gemini evaluates the URL text, but the
  optional Safe Browsing adapter has no configured key.
- A low score is not proof of safety. Gemini does not authenticate documents,
  retrieve past incident records, intercept calls or stop payments.

## Which version is current?

| Capability | Flutter Android (main demo) | Flutter iOS | Original native Android |
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
The native sign-in screen still uses its existing Google/biometric flow and
debug-only temporary developer login. See `app/src/main/java/com/dhalam/ap/SignInScreen.kt`
for the existing local test login; it is not a cloud credential. After unlocking,
**Scam check** is the default tab. Use the same sample content and local backend.

```bash
adb -s emulator-5554 shell am start -n com.dhalam.ap/.MainActivity
# Return to Flutter:
adb -s emulator-5554 shell am start -n com.dhalam.dhalam/.MainActivity
```

## Verified in this setup

- Flutter chat launches on the API 36 emulator with a loopback backend connection.
- The backend returned HTTP 200 with a live Gemini assessment after restart.
- 47 backend tests, 18 Flutter tests, 19 native JVM tests and 10 native emulator
  UI tests passed. The Flutter suite covers per-message consent, contextual
  follow-ups without device telemetry, and enlarged text.
- Native consent cancellation and existing Google sign-in gating were exercised
  by UI tests. Production Google sign-in and physical-device capture behavior
  still need separate verification.

## Try reference-backed advice

Send a synthetic message such as “A caller claiming to be from RBI says my account
will be frozen unless I send a security deposit now.” Open **Why? Show details**
and scroll to **Sources for this advice**. If Gemini used retrieved guidance,
you will see official source titles, summaries, dates and selectable URLs. The
report includes the same references. Unavailable retrieval and no matching
reference are shown explicitly. An advisory match is not proof of fraud.

The local backend uses Google gemini-embedding-001 with a SQLite/sqlite-vec index
of five public RBI summaries. It does not store your conversations or search
private incident records. Cloud Run must be redeployed separately to gain this
version; the emulator’s local backend is the test target.

## Native Android on a physical phone with Cloud Run

The phone APK must be built with `DHALAM_BACKEND_URL` set to the hosted HTTPS
origin. The default `10.0.2.2` address only works in an emulator.

Sign in with Google. Cloud Run's application verifies the Google signature,
audience, issuer, expiry and verified email on every AI request. Any verified
Google account is allowed; biometric unlock alone only authorizes local checks.
After biometric unlock, cloud AI asks Credential Manager for a previously
authorized Google account. A backend 401 triggers credential renewal and one
retry; Google may require an account prompt or explicit sign-in. There is no
manual token field, bundled developer token, client secret or service-account key.
Google tokens live only in memory. Use **Google account** to sign in again if
renewal cannot complete.

**Log out** appears in the header of every native dashboard tab. It clears the
session, returns to sign-in and cancels the native session's periodic checks.
Saved scan history remains on the device.

Tap **Scan now**, then ask **AskDhalam**, “Which visible apps have camera
permission?” Sending shares the question and up to 48 selected facts from the
latest stored scan with the backend and Gemini. Answers cite those facts. Android
visibility limits apply; a permission grant does not establish actual use.
Scam check does not automatically upload the permission inventory.

The Android OAuth client must register `com.dhalam.ap` with the APK's signing
certificate SHA-1, and the Web client ID must match the backend. OAuth consent
screen publishing/test-user restrictions still apply. A Google account prompt
must be completed on the device; automated UI tests do not prove account consent.

Older Flutter builds with a developer-token field are not compatible with this
Google-user authentication mode. They need a Google sign-in client before using
the hosted AI endpoints; their local device checks remain available.
