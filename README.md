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

## Try the app

Flutter opens directly into a chat: paste a message or choose a photo, tap **Check**,
and confirm consent. A short answer and first action appear in a visual card;
**Why? Show details** contains evidence and report export. Follow-ups include earlier
user messages with consent, up to six submissions per in-memory chat. Earlier
photos are not resent. **Chat options → Device checks** opens the local dashboard.

The native Android app now has the same core scam-chat workflow in **Scam check**,
plus its separate device-facts chat. It keeps its existing sign-in and worker
lifecycle. Native image input uses the system picker; Flutter also offers camera.

See [emulator setup and sample conversations](docs/interact-with-app/README.md).

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

## Verification and deployment

From the repository root:

```bash
python -m pytest backend/test_app.py -q --rootdir=.
python -m backend.check --live
python -m backend.check --deploy
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

Local verification after repository consolidation (November 7, 2026): 47 backend
tests and 16 Flutter tests pass; Flutter analysis is clean. Android debug APK and
unsigned iOS release builds both succeed. The capture
regression verifies that native events supersede older pending polling responses.
Earlier live checks returned a structured Vertex text response and a screenshot
response through private Cloud Run (HTTP 200, about 9.5 seconds); unauthenticated
requests received HTTP 403. These are smoke tests, not detection-accuracy or
fraud-prevention benchmarks. No cloud settings or billing were changed during
this local verification.

Implemented locally: opt-in Flutter Android periodic access checks and native
capture-event updates with polling fallback. Checks run approximately every 15
minutes or later as Android permits; they do not run cloud inference. Disabling
checks clears their separate baseline. Notification permission is independent. Physical device verification, public mobile authentication, production
signing, voice triage, on-device Gemma and bank integration are
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

`backend/.env`, credential JSON, signing keys, build outputs and local caches are
ignored. Review `git status --short` and `git diff --stat` before committing.
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

This handoff does not commit or push automatically. The native `app/` already
contained teammate/user changes before this work; they have been preserved.
