# Dhalam: local biometric access, optional Google sign-in, and Groq

Users unlock with enrolled strong biometrics or choose **Sign in with Google**.
Both options allow AI questions; users never enter backend URLs or API keys.
Android performs biometric verification locally; the app never receives or stores
biometric templates. No Google token is sent for local sessions. If a Google
token is supplied, the backend verifies its signature, expiry, issuer, audience,
subject and verified email. The Groq key stays only on the backend.

The AI endpoints intentionally accept requests without an identity token.
Local biometric success is an app access gate, not remotely verified identity.
Restart the backend after this update. Google OAuth setup below is optional
and is needed only for the Google sign-in option.

This replaces the demo-token setup. `DHALAM_API_TOKEN` is ignored, and Android
deletes the retired saved demo configuration. No Firebase, password database,
separate registration form, or custom verification-email flow is added.
Google handles the account's credentials and email verification.

## One-time developer setup in Google Cloud

Sign in with Google itself has no per-sign-in charge. This does not make Groq
usage or backend hosting unlimited/free; their separate quotas still apply.

1. Create/select a Google Cloud project and open **Google Auth Platform**.
2. Configure Branding and Audience. While in Testing, add the Google accounts
   that will test the app. Request only the basic identity/profile/email scopes.
3. Create an OAuth client of type **Android** with package `com.dhalam.ap` and
   your debug signing certificate SHA-1. Obtain SHA-1 from Android Studio's
   Gradle `app > Tasks > android > signingReport` task. Register the release/Play
   signing SHA-1 separately before distributing a signed release.
4. Create an OAuth client of type **Web application** in the same project.
   Copy its public client ID ending in `.apps.googleusercontent.com`.
   Do not put an OAuth client secret in Android. This ID is the server audience;
   it is not the Android OAuth client ID.
5. Add the public Web client ID to the project-root `gradle.properties`:

   ```properties
   DHALAM_GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   ```

6. In the ignored `backend/.env`, retain your Groq key and add:

   ```dotenv
   GROQ_API_KEY=your_server_side_groq_key
   GROQ_MODEL=openai/gpt-oss-20b
   GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   ```

The Web client IDs in Android and the backend must match. These developer IDs
are public configuration, not API keys. Re-sync Gradle/rebuild after changing
Android configuration; restart the backend after changing its environment.
The backend loads only `backend/.env` or existing environment variables.

## Local development

From the project root (Python 3.11+):

```bash
python3 -m venv backend/.venv
backend/.venv/bin/python -m pip install -r backend/requirements-dev.txt
backend/.venv/bin/python -m uvicorn backend.app:app --host 127.0.0.1 --port 18765 --no-access-log
```

The debug app defaults to `http://10.0.2.2:18765` for the standard emulator.
Use enrolled strong biometrics, or an emulator with Google Play and an account
for Google sign-in. Android 17 may request local-network access for AI requests.
Until biometric unlock or Google sign-in succeeds, dashboard, scans, search,
export and navigation remain unavailable. Background scans are gated on the
in-process session; a process restart requires unlocking again.

A release build has **no default backend URL**. It must have a deployed HTTPS
endpoint before users can use Ask Dhalam away from your development computer.
There is no endpoint/token settings dialog in the user interface.

## Physical phone or remote demo

For a remote demo, you can expose the running backend
through a temporary HTTPS tunnel, for example with an installed `cloudflared`:

```bash
cloudflared tunnel --url http://127.0.0.1:18765
```

This is an optional developer action, not something end users run. Use the
returned HTTPS origin in project-root `gradle.properties`:

```properties
DHALAM_BACKEND_URL=https://YOUR_TEMPORARY_HOST.trycloudflare.com
```

Rebuild the app. The phone does not need your computer's local IP or a shared
password, but the backend computer and tunnel must remain running. A Quick
Tunnel URL can change and has no uptime guarantee; use a stable HTTPS host for
distribution. No public tunnel/deployment is created automatically by this code.

Only debug builds permit local HTTP for emulator/loopback hosts. Remote
connections require HTTPS, and redirects are not followed with user credentials.
Never bundle the Groq key in Gradle properties, BuildConfig or app resources.

## API and lifecycle

- `GET /health`: public process liveness, no Groq call.
- `GET /check`: configuration status; verifies Google identity if supplied.
- `POST /ask`: question and selected evidence → validated Groq answer; Google is optional.

Local sessions omit Authorization. When supplied, the header contains a Google
ID token; invalid/expired credentials return 401 and unverified email returns 403.
Google configuration is required only for requests supplying Google credentials.

Google tokens stay in the app's ViewModel memory and are never logged or saved
to disk/backups. Rotation retains the session. Process restart or an expired
token requires signing in again. Sign out or a rejected session immediately
returns to the sign-in screen, clears displayed observations, cancels scan work,
and prevents background collection. Sign out immediately clears app credentials,
questions/results, and pending work, and clears Credential Manager state.
This is local sign-out, not revocation of the Google account.

The app displays an answer only after a user-initiated Send. It selects at most
48 current saved facts using deterministic text matching and capability
prioritization, declares limited coverage, and excludes retained older facts.
The request is bounded to 90 KiB; the backend accepts at most 96 KiB/64 facts.
Model citations are constrained to submitted IDs and revalidated before display.
Blank optional recommendations/limitations are omitted.

Embeddings, vector retrieval and numerical risk scoring are still deferred.
No unrelated scan, export, database or navigation redesign is included.

## Verification

```bash
backend/.venv/bin/python -m pytest backend/test_app.py -q
backend/.venv/bin/python -m backend.check --url http://127.0.0.1:18765
```

The connectivity check does not require or print a user credential and does not
claim a live Groq request succeeded. Automated tests mock Google/Groq and check
rejected/expired identities, audience/issuer, verified email, auth service
failures, evidence validation and upstream errors.

After configuring the OAuth clients:
1. Build/run the app, tap Sign in with Google and choose an authorized test account.
2. Confirm sign-in is verified by the backend, then ask a question after a scan.
3. Verify answer/evidence display, Sign out, and that the entire dashboard is replaced by the sign-in screen. Back must not reveal protected content.
4. Test Cancel in the Google picker, no account, offline service and expired sign-in.
5. Test a physical phone using the configured HTTPS endpoint.

Without the real OAuth client IDs, the code can compile and be tested with
mocks, but live Google sign-in cannot be verified. An ID token's profile label
is only for display; authorization relies on server verification.

Official references:
- [Android Google sign-in](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Backend ID-token verification](https://developers.google.com/identity/sign-in/android/backend-auth)
- [Temporary tunnel limits](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
