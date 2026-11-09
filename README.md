# Dhalam AI

A simple scam-checking chat and device-access review for Android and iOS. Team: **Ladydebuggers**
— Anusha Jain and Abhyudaya Marya.

## Repository layout

| Path | Purpose |
| --- | --- |
| [`flutter/`](flutter/README.md) | Main cross-platform client: Android and iOS, Gemini triage, capture warnings, local history and reports |
| `flutter/android/` | Required Android host and Kotlin platform bridges for Flutter; not a second copy to delete |
| `flutter/ios/` | Required iOS host and Swift platform bridges for Flutter |
| `app/` | Original native Android/Compose application, retained as a reference and working alternative |
| [`backend/`](backend/README.md) | The single FastAPI service, shared by Flutter and native Android |
| [`docs/`](docs/HACKATHON_EVALUATION_AND_STRATEGY.md) | Original evaluation/strategy and earlier idea/recommendation notes; historical claims are not proof of implementation |
| [`docs/submission/`](docs/submission/) | Existing hackathon submission deck |
| `ml/` | Earlier exploratory data scripts; not a trained or deployed detector |

The root Gradle project builds the **original native Android app**. Flutter has
its own Android host and build entry point. Telemetry and capture bridges overlap
between the two clients. Keep the original client while validating the migration;
future consolidation can extract shared Kotlin logic after behavior is compared.
Do not delete `flutter/android/` or merge its manifest into root `app/`.

## Try the current Android APK

Only the current native Android demo APK is kept in [`build/apks/`](build/apks).
It includes the registered Web OAuth client and requires an allowed Google
account for hosted AI. Build Flutter Android or iOS from source when needed;
Flutter needs a Google sign-in flow before it can call hosted AI.

| Platform | Build Artifact | Purpose |
| --- | --- | --- |
| **Native Android** | [`dhalam-native-android.apk`](build/apks/dhalam-native-android.apk) | Version 1.0.2 (code 3) debug APK with the configured Web OAuth client, Credential Manager Google Sign-In, biometric unlock fallback, logout, and permission-grounded AskDhalam. |

- **Flutter**: Opens directly into scam triage: paste a message or choose a photo, tap **Check**, and confirm consent. A structured assessment and action appear in a visual card; **Why? Show details** displays evidence, official RBI reference citations, and incident report export.
- **Native Android**: Features **Scam check** (Gemini threat triage with photo/text input and reference citations) and **AskDhalam** (permission-grounded device QA). Supports Google OAuth sign-in via Android Credential Manager, biometric unlock, and logout on all tabs.

See [testing setup and sample conversations](docs/interact-with-app/README.md).
For the optional direct Meta test-number bot, see
[WhatsApp setup](docs/interact-with-app/whatsapp-bot.md).
On 9 November 2026, a real WhatsApp `hello` and one consented synthetic `CHECK`
received Dhalam replies through Meta's test number. This is a limited developer
demo; the temporary Meta access token will need rotation when it expires. To
invite a friend, verify their number in Meta's test recipients and add it to
the backend sender allowlist before updating Cloud Run.
As of 9 November 2026, two test senders are configured on Cloud Run; use a
fresh `hello` from each phone to confirm delivery after a credential change.

The translate menu changes the full Flutter or native Android interface between
English, Hindi, Marathi, Bengali, Tamil and Telugu. Each client keeps one shared
language setting while it runs. Finding states stay in English in local history;
the interface translates them when displayed. `tools/i18n/*.json` is the source
for both tables; run `python3 tools/i18n/generate.py` after editing the strings.
Native Android bundles Noto Sans, and Flutter loads it through Google Fonts.
Voice input and read-aloud rely on speech services and installed voices on the device.
Cloud Run was redeployed on 9 November 2026 with mobile Google authentication.
AI requests require a verified Google ID token from an allowed account. Native
Android links an account once before hosted AI; Google sign-in and one synthetic
hosted check succeeded on the API 36 emulator. Flutter clients still need an
authentication update for hosted AI.

## Run from this repository

Backend (Python 3.11+, existing conda environment):

```bash
conda activate fastapi-rag
python -m pip install -r backend/requirements-dev.txt
python -m uvicorn backend.app:app --host 127.0.0.1 --port 18765 --no-access-log
```

All backend settings are in ignored `backend/.env`. New teammates copy
`backend/.env.example` to `backend/.env`, select their project, and configure ADC
with `gcloud auth application-default login`. No keys are bundled into clients.
The previous workspace-level `.env`, backend and Flutter directory have moved here.

Flutter:

```bash
cd flutter
flutter pub get
flutter run
```

Native Android alternative: open the repository root in Android Studio, or use
`./gradlew :app:assembleDebug` with the project's required full JDK. It is not
necessary to build the native alternative to run Flutter.
For a shareable native APK, run `python3 tools/build_native_demo.py`. It reads
the Web OAuth client ID from ignored `backend/.env`, validates the packaged
value, and updates `build/apks/dhalam-native-android.apk`. No Gradle file edit
is needed.

## Verification and deployment

From the repository root:

```bash
python -m pytest backend/test_app.py -q --rootdir=.
python -m backend.check --live
python -m backend.check --deploy --mobile-auth
python -m backend.check --cloud-auth
```

From `flutter/`:

```bash
flutter analyze
flutter test
flutter build apk --debug
flutter build ios --release --no-codesign
```

The native phone build uses hosted Cloud Run with verified Google-user authentication.
Its URL is written to `backend/.env` as `DHALAM_API_URL`. The backend README
explains mobile deployment, account limits and OAuth client setup. Production
signing remains separate. Do not publish service credentials or identity tokens.

## Technical progress and limits

Implemented: Vertex Gemini text/image/URL triage with consent and bounded input,
structured responses, image metadata removal, native Android `/ask` compatibility,
Cloud Run deployment, local capture warnings, Android launcher visibility,
permission search/filtering, service/permission change history, and user-reviewed
incident export. Google Safe Browsing is an optional adapter and needs a separate
restricted key; missing/unavailable lookup is shown explicitly.

Current verification status (9 November 2026): 66 backend tests (`pytest backend/`),
30 Flutter tests (`flutter test`), and 34 native Android unit tests
(`./gradlew testDebugUnitTest`) pass. Flutter analysis reports no issues.
Both Android release APKs were rebuilt with the current six-language interface
and signed with the same Android debug key as the previous demo packages.
Live Cloud Run endpoint (`https://dhalam-api-xxpghctsqq-el.a.run.app`) returns
structured Gemini 3.8 Flash threat assessments with sqlite-vec reference
citations. Google ID token authentication and a synthetic hosted check were
verified in the native Android debug emulator. Local temporary sign-in opens
local screens; biometric unlock still needs a device with biometrics enrolled.

Implemented locally: opt-in Flutter Android periodic access checks and native
capture-event updates with polling fallback. Checks run approximately every 15
minutes or later as Android permits; they do not run cloud inference. Disabling
checks clears their separate baseline. Notification permission is independent.
Voice input and read-aloud (Flutter, six languages) use on-device speech services
and need physical-device verification. Broad public sign-up, Flutter hosted
authentication, production signing, voice-call triage, on-device Gemma and bank
integration are
not complete. iOS cannot expose Android-style cross-app inventories. No payment
blocking, other-app capture cancellation or official complaint filing is claimed.
Research/pitch work is paused at the team's request.

## Reference-backed advice

Scam triage now retrieves related official guidance using Google
`gemini-embedding-001` (768 dimensions) and a local SQLite/sqlite-vec vector index.
Five reviewed RBI summaries form the initial collection. Cited sources appear
under **Why? → Sources for this advice** and in reports. This does not identify
past victims or prove a match to a known criminal. User content is never added to
the index. See [build and deployment instructions](backend/README.md#reference-backed-recommendations-vector-retrieval).

## Share progress safely

`backend/.env`, credential JSON, signing keys and local caches are ignored.
The native demo APK in `build/apks/` is tracked; review it before committing. Review
`git status --short` and `git diff --stat` before committing.
IDE metadata and the generated ObjectBox schema backup have been removed from
Git tracking; existing local copies are preserved. Their staged removals are part
of this cleanup. The actual ObjectBox schema remains tracked. Stage intended paths explicitly:

```bash
git add README.md .gitignore backend flutter docs
# Review the native app changes separately before adding app/.
git diff --cached --stat
git diff --cached --check
```

Open `Dhalam-Hack2Skill/` as the repository in your editor. The parent directory
is only a workspace container. The old workspace-level Flutter source and
submission folder have been consolidated into `flutter/` and `docs/submission/`.
An editor may recreate workspace state at the old `dhalam_flutter/ios/` path;
open `flutter/ios/Runner.xcworkspace` here instead. That old folder contains no
application source.

The native `app/` can contain teammate changes; review its diff before committing.
