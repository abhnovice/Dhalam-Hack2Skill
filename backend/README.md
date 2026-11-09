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
| `GET/POST /webhooks/whatsapp` | Optional direct Meta test-number webhook with signature verification and sender allowlist |

Triage requires `consent: true`. Text is limited to 12,000 characters, URLs to
4,096 and images to 4 MiB / 20 megapixels. Image format is verified, EXIF is
removed by pixel re-encoding, and generation uses bounded timeouts. The server
never visits user-provided links or follows their redirects. Invalid input and
provider errors do not echo the submitted content. Request-body logging is off;
application code does not persist submissions. Google service retention policies
still apply. The concern score is uncalibrated, not a measured probability.

For direct Meta WhatsApp Cloud API testing, follow the
[WhatsApp bot setup](../docs/interact-with-app/whatsapp-bot.md). This webhook
requires `CHECK` consent on each text or image submission and stays disabled
until its private credentials and test-sender allowlist are configured.

`SAFE_BROWSING_API_KEY` is optional and separate from the Gemini key. Without it,
responses explicitly say reputation was not checked; Gemini only analyzes the
URL text. The lookup adapter distinguishes a match, no match, unavailable service
and missing configuration. No match is not a safety guarantee. Safe Browsing is
for non-commercial use; a commercial bank product needs Web Risk or another
appropriately licensed reputation provider. [Google usage terms](https://developers.google.com/safe-browsing/)

## Deployment and checks

```bash
python -m pytest backend/ -q --rootdir=.
python -m backend.check --live
python -m backend.check --deploy --mobile-auth
python -m backend.check --cloud-auth
```

`--live` makes one synthetic request using Cloud quota. It is a connectivity
check, not an accuracy benchmark. A bare `--deploy` reads `.env`, creates a dedicated
`dhalam-runtime` service identity if needed, grants it `roles/aiplatform.user`,
and deploys a **private** Cloud Run service with 0–2 instances and concurrency 4.
In this private mode, Cloud Run verifies IAM identity tokens before forwarding
requests; the app accepts them only when `K_SERVICE` and the deployment's
`TRUST_CLOUD_RUN_IAM=true` setting are both present. A private-mode deployment
has no public invoker binding. The current service uses `--deploy --mobile-auth`
instead, with Google token verification and an account allowlist.
On 9 November 2026, the native Android debug app signed in with Google and
received one hosted synthetic scam assessment. Flutter does not yet send the
required Google ID token, and device biometric unlock has not been verified.
Build, storage, hosting and inference can incur charges. The deployment writes
the resulting `DHALAM_API_URL` back to `.env`. Source-upload and Docker allowlists
exclude credentials, tests and documentation. For a change to WhatsApp test
senders or Meta credentials, edit the ignored `backend/.env` and run
`python -m backend.check --setup-whatsapp`. This updates Cloud Run without
rebuilding either Android APK.

### Mobile Google authentication and direct demo connection

Deploy the phone-compatible service with:

```bash
python -m backend.check --deploy --mobile-auth
```

This validates `GOOGLE_WEB_CLIENT_ID` and deploys the Cloud Run service with attached `dhalam-runtime` service account for Vertex AI inference.
- **Google ID Token Authentication**: The backend verifies token signature, expiry, issuer, audience, and verified email for Google-authenticated sessions.
- **Biometric & temporary unlock**: These unlock local features. Native Android links a Google account once before hosted AI requests, then refreshes the authorized account on later visits. The backend rejects anonymous AI requests.
- **Rate Limits**: 10 POSTs/minute and 60/hour per verified Google subject. `ALLOWED_GOOGLE_EMAILS` can restrict access to designated demo accounts.
- **Client integration**: Native Android uses Credential Manager for sign-in and token renewal. Flutter needs its own Google authentication before it can call this mobile-auth deployment.

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

## Synthetic chat smoke cases

`python -m backend.check --evaluate` validates the six starter scenarios locally,
without a model call. `--evaluate --live --limit 6 --output /tmp/dhalam-eval.json`
runs them sequentially using cloud quota and records schema-valid responses,
level agreement and direct-model latency. Output files must not already exist.
The runner stops on the first provider error. These author-labeled synthetic
examples are a smoke suite, not a real-world fraud-accuracy benchmark or the
strategy’s proposed 50-case evaluation. Screenshot testing is separate.

## Reference-backed recommendations (vector retrieval)

The backend now uses **SQLite + sqlite-vec** with Google **gemini-embedding-001**,
768 dimensions and cosine-distance nearest-neighbor search. This is a persistent
vector index, independent of Android’s ordinary ObjectBox device records. No
watsonx, Milvus service or separately provisioned vector hosting is required.

The starter corpus is five reviewed summaries from two official RBI advisories:
[cyber threats and safe banking](https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx?prid=53185)
and [RBI impersonation](https://rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx?prid=58595).
They describe reported fraud patterns. They are not private FIRs, victim records,
a live incident feed or proof that a submitted message comes from the same attacker.
The source URLs, publication dates and review date are in `triage/references.json`.

```bash
# From the repo root in fastapi-rag; uses five Google embedding calls:
python -m backend.check --build-index
```

The generated `triage/references.sqlite3` is ignored by Git and explicitly included
in Docker/Cloud Run uploads. Rebuild it after editing the corpus or embedding model.
Deployment checks index freshness before any cloud mutation. Rebuilds are atomic;
a failed build retains the previous index. Cloud Run loads the packaged index
read-only; this does not need a persistent volume or a vector-service instance.
The container makes the packaged public-reference index readable by its non-root runtime user.

Each consented text query is embedded with `RETRIEVAL_QUERY`; reference vectors
use `RETRIEVAL_DOCUMENT`. Up to three references within cosine distance 0.40 and
within 0.05 of the best hit are supplied to Gemini. These are initial relevance
heuristics, not a calibrated fraud probability or a validated recall threshold.
Only IDs returned by retrieval can become citations; titles, URLs and dates are
resolved by the server. The model can choose not to cite an irrelevant match.
For image-only input, an initial model pass extracts observations before retrieval
and a second assessment; this can increase latency.

User queries, query embeddings, image extractions and conversation history are
never written to the vector database. Embedding calls use Google quota and service
retention policies still apply. Missing/stale indexes or embedding failures degrade
to ordinary triage with `grounding_status=unavailable`, not fabricated references.
An unrelated query can return `no_match`; this does not imply safety.

Flutter and native Android display cited sources under **Why? → Sources for this
advice**, and include them in incident reports. This is enabled for scam triage;
the older native `/ask` device-facts route still uses its existing fact citations.

Implementation references: [Google embedding API](https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-text-embeddings),
[sqlite-vec Python integration](https://alexgarcia.xyz/sqlite-vec/python.html).
