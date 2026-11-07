# Dhalam backend

One backend lives here. The former workspace-level `backend/` was consolidated
into this directory. `app.py` preserves the native Android `/ask` contract;
`triage/` implements text, image and URL scam triage for Android and iOS Flutter.
Gemini on Vertex AI is the default provider. The previous Groq adapter remains
available only for compatibility (`AI_PROVIDER=groq`), with its original tests.

## Configuration and local run

Use the existing `fastapi-rag` conda environment (Python 3.11). All settings live
in **this directory's `.env`**, moved here from the workspace root. Do not use
shell exports or copy `.env` into a mobile application. Existing keys were
preserved. Vertex uses Application Default Credentials locally and an attached
service identity on Cloud Run; no service-account JSON key is needed.

From `Dhalam-Hack2Skill/`:

```bash
conda activate fastapi-rag
python -m pip install -r backend/requirements-dev.txt
python -m uvicorn backend.app:app --host 127.0.0.1 --port 18765 --no-access-log
```

If shell aliases select the wrong Python, use
`/Users/anushajain/miniforge3/envs/fastapi-rag/bin/python` explicitly.
The default `.env` selects `gemini-3.8-flash`, Vertex location `global` and the
Dhalam AI project. `global` does not provide an India-only processing guarantee.
Cloud Run's region is separately set to `asia-south1`.

The `.env.example` documents the settings without credentials. Keep `.env`
private. Never paste API keys into a mobile connection screen.

## Endpoints

| Route | Behavior |
| --- | --- |
| `GET /health` | Process liveness; no model call |
| `GET /` | Model/provider metadata; no credentials |
| `POST /api/v1/analyze-threat` | Explicit consent plus message, URL and/or JPEG/PNG/WebP; validated structured Gemini assessment |
| `POST /api/v1/coercion-check` | Compatibility helper; mobile evaluates capture warnings locally without uploading telemetry |
| `GET /check` | Native Android compatibility configuration check |
| `POST /ask` | Native Android evidence-grounded questions with validated evidence citations; defaults to Gemini |

Triage requires `consent: true`. Text is limited to 12,000 characters, URLs to
4,096 and images to 4 MiB / 20 megapixels. Image format is verified, EXIF is
removed by pixel re-encoding, and generation uses bounded timeouts. The server
never visits user-provided links or follows their redirects. Invalid input and
provider errors do not echo the submitted content. Request-body logging is off;
application code does not persist submissions. Google service retention policies
still apply. The concern score is uncalibrated, not a measured probability.

`SAFE_BROWSING_API_KEY` is optional and separate from the Gemini key. Without it,
responses explicitly say reputation was not checked; Gemini only analyzes the
URL text. The lookup adapter distinguishes a match, no match, unavailable service
and missing configuration. No match is not a safety guarantee. Safe Browsing is
for non-commercial use; a commercial bank product needs Web Risk or another
appropriately licensed reputation provider. [Google usage terms](https://developers.google.com/safe-browsing/)

## Deployment and checks

```bash
python -m pytest backend/test_app.py -q --rootdir=.
python -m backend.check --live
python -m backend.check --deploy
python -m backend.check --cloud-auth
```

`--live` makes one synthetic request using Cloud quota. It is a connectivity
check, not an accuracy benchmark. `--deploy` reads `.env`, creates a dedicated
`dhalam-runtime` service identity if needed, grants it `roles/aiplatform.user`,
and deploys a **private** Cloud Run service with 0–2 instances and concurrency 4.
Build, storage, hosting and inference can incur charges. The deployment writes
the resulting `DHALAM_API_URL` back to `.env`. Source-upload and Docker allowlists
exclude credentials, tests and documentation.

Cloud Run IAM protects every deployed route. Local development is bound to
loopback; do not expose it publicly. The optional native `/ask` Google sign-in
verification is preserved, but it is not the same as Cloud Run IAM. Do not make
the service public simply to bypass a 403. A consumer launch requires a complete
mobile identity/token exchange, per-user abuse controls and a distribution plan.

For an authorized developer demo, use a short-lived `gcloud auth print-identity-token`
result in Flutter's **Demo connection**, together with the Cloud Run URL. The
token stays in screen memory and expires. Do not commit it, bake it into an APK,
or use an API key as a substitute. The app sends it only to the configured HTTPS
backend and refuses redirects. On emulator-only local runs use
`http://10.0.2.2:18765`; iOS simulator can use loopback as platform policy permits.

Native Android optional Google sign-in still requires Android OAuth client
registration for `com.dhalam.ap` and its signing SHA-1, plus a Web OAuth client.
The backend's `GOOGLE_WEB_CLIENT_ID` and native app audience must match. Cloud Run
IAM and native OAuth are separate layers. Physical sign-in was not verified here.

## Validation boundaries

Automated tests cover the existing Android contract, identity errors, citation
validation, input bounds, image validation, consent, sanitized errors, unknown
capture state, and triage/reputation separation. A live synthetic text request
succeeded with Gemini 3.8 Flash in approximately 8.6 seconds on November 7, 2026.
This is one observation, not a latency guarantee. Do not claim 95% detection
accuracy, sub-second inference, document authentication or automatic intervention.

References: [Gemini model](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/3-8-flash),
[Google ID-token verification](https://developers.google.com/identity/sign-in/android/backend-auth),
[Cybercrime reporting](https://cybercrime.gov.in/).
