"""Dhalam checks and deployment; all project settings are read from backend/.env."""
import argparse
import asyncio
import os
from pathlib import Path
import subprocess
import time
from urllib.parse import urlparse

from dotenv import load_dotenv, set_key
import httpx

ROOT = Path(__file__).resolve().parent
load_dotenv(ROOT / '.env')


def gcloud(*args, capture=False):
    result = subprocess.run(['gcloud', *args, '--project=' + os.environ['GOOGLE_CLOUD_PROJECT'],
                             '--account=' + os.getenv('GOOGLE_ACCOUNT', 'ad.anushajain@gmail.com')],
                            check=True, capture_output=capture, text=True)
    return result.stdout.strip() if capture else None


def deploy():
    project = os.environ['GOOGLE_CLOUD_PROJECT']
    region = os.getenv('CLOUD_RUN_REGION', 'asia-south1')
    service = os.getenv('CLOUD_RUN_SERVICE', 'dhalam-api')
    identity = f'dhalam-runtime@{project}.iam.gserviceaccount.com'
    accounts = gcloud('iam', 'service-accounts', 'list', '--format=value(email)', capture=True).splitlines()
    if identity not in accounts:
        gcloud('iam', 'service-accounts', 'create', 'dhalam-runtime', '--display-name=Dhalam Cloud Run runtime')
    gcloud('projects', 'add-iam-policy-binding', project, '--member=serviceAccount:' + identity,
           '--role=roles/aiplatform.user', '--condition=None', '--quiet', '--format=none')
    build_identity = f'dhalam-build@{project}.iam.gserviceaccount.com'
    if build_identity not in accounts:
        gcloud('iam', 'service-accounts', 'create', 'dhalam-build', '--display-name=Dhalam source build')
    gcloud('projects', 'add-iam-policy-binding', project, '--member=serviceAccount:' + build_identity,
           '--role=roles/run.builder', '--condition=None', '--quiet', '--format=none')
    # Explicit allowlist: never upload .env, API keys or local ADC credentials.
    config = {
        'GOOGLE_CLOUD_PROJECT': project,
        'GOOGLE_CLOUD_LOCATION': os.getenv('GOOGLE_CLOUD_LOCATION', 'global'),
        'GOOGLE_GENAI_USE_VERTEXAI': 'true',
        'GEMINI_MODEL': os.environ['GEMINI_MODEL'],
        'AI_PROVIDER': 'gemini',
    }
    gcloud('run', 'deploy', service, '--source=' + str(ROOT), '--region=' + region,
           '--service-account=' + identity, '--build-service-account=projects/' + project + '/serviceAccounts/' + build_identity,
           '--no-allow-unauthenticated',
           '--memory=512Mi', '--cpu=1', '--min-instances=0', '--max-instances=2',
           '--concurrency=4', '--timeout=90', '--quiet',
           '--set-env-vars=' + ','.join(k + '=' + v for k, v in config.items()))
    url = gcloud('run', 'services', 'describe', service, '--region=' + region,
                 '--format=value(status.url)', capture=True)
    set_key(ROOT / '.env', 'DHALAM_API_URL', url)
    print('Private Cloud Run URL saved to backend/.env:', url)
    print('Mobile demos need a short-lived identity token; this is not public production authentication.')


def setup_reputation():
    """Configure the non-commercial Safe Browsing demo without printing its key."""
    project = os.environ['GOOGLE_CLOUD_PROJECT']
    gcloud('services', 'enable', 'apikeys.googleapis.com', 'secretmanager.googleapis.com', 'safebrowsing.googleapis.com')
    key = os.getenv('SAFE_BROWSING_API_KEY', '').strip()
    if not key:
        resource = os.getenv('SAFE_BROWSING_KEY_RESOURCE', '').strip()
        if not resource:
            import json
            created = json.loads(gcloud('services', 'api-keys', 'create',
                '--display-name=Dhalam non-commercial URL demo',
                '--api-target=service=safebrowsing.googleapis.com', '--format=json', capture=True))
            resource = created.get('name') or created.get('response', {}).get('name')
            if not resource or '/keys/' not in resource:
                raise RuntimeError('API key resource was not returned; inspect Cloud Console before retrying')
            set_key(ROOT / '.env', 'SAFE_BROWSING_KEY_RESOURCE', resource)
        key = gcloud('services', 'api-keys', 'get-key-string', resource, '--format=value(keyString)', capture=True)
        if not key:
            raise RuntimeError('No API key returned')
        set_key(ROOT / '.env', 'SAFE_BROWSING_API_KEY', key)
    secret = 'dhalam-safe-browsing'
    names = gcloud('secrets', 'list', '--format=value(name)', capture=True).splitlines()
    verb = ['secrets', 'versions', 'add', secret] if secret in names else ['secrets', 'create', secret, '--replication-policy=automatic']
    subprocess.run(['gcloud', *verb, '--data-file=-', '--project=' + project,
                    '--account=' + os.getenv('GOOGLE_ACCOUNT', 'ad.anushajain@gmail.com'), '--quiet'],
                   input=key, text=True, check=True, capture_output=True)
    gcloud('secrets', 'add-iam-policy-binding', secret,
           '--member=serviceAccount:dhalam-runtime@' + project + '.iam.gserviceaccount.com',
           '--role=roles/secretmanager.secretAccessor', '--quiet', '--format=none')
    gcloud('run', 'services', 'update', os.getenv('CLOUD_RUN_SERVICE', 'dhalam-api'),
           '--region=' + os.getenv('CLOUD_RUN_REGION', 'asia-south1'),
           '--update-secrets=SAFE_BROWSING_API_KEY=' + secret + ':latest', '--quiet')
    print('Safe Browsing key restricted to its API, stored in .env and Secret Manager, and attached to Cloud Run.')


async def live():
    from .triage.gemini_service import analyze_threat
    from .triage.schemas import ThreatAnalysisRequest
    from .triage.config import settings
    start = time.monotonic()
    result = await analyze_threat(ThreatAnalysisRequest(consent=True, text=
        'Synthetic demo: a caller claiming to be police demands a payment to avoid arrest and tells me not to speak to my family.'))
    print('PASS: live structured Gemini response;', settings.DEFAULT_MODEL,
          'elapsed seconds:', round(time.monotonic() - start, 2), 'level:', result.threat_level)
    print('This one synthetic example is not an accuracy benchmark.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default=os.getenv('DHALAM_API_URL', 'http://127.0.0.1:18765'))
    parser.add_argument('--setup-reputation', action='store_true', help='Configure restricted Safe Browsing key and Cloud Run secret for the non-commercial demo')
    parser.add_argument('--live', action='store_true', help='Make one synthetic Vertex AI request (uses quota)')
    parser.add_argument('--deploy', action='store_true', help='Deploy a private Cloud Run demo (uses billing)')
    parser.add_argument('--cloud-auth', action='store_true', help='Use gcloud identity in memory to check private Cloud Run')
    args = parser.parse_args()
    if args.setup_reputation:
        setup_reputation()
        return 0
    if args.deploy:
        deploy()
        return 0
    if args.live:
        asyncio.run(live())
        return 0
    base = args.url.rstrip('/')
    parsed = urlparse(base)
    if (parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        parser.error('Use a plain HTTP(S) backend origin without credentials or query parameters.')
    if parsed.scheme == 'http' and parsed.hostname not in ('127.0.0.1', 'localhost', '::1'):
        parser.error('Use HTTPS outside loopback.')
    headers = {}
    if args.cloud_auth:
        if parsed.scheme != 'https' or not parsed.hostname.endswith('.run.app'):
            parser.error('Cloud credentials may only be sent to an HTTPS run.app service.')
        headers['Authorization'] = 'Bearer ' + gcloud('auth', 'print-identity-token', capture=True)
    try:
        with httpx.Client(timeout=20, follow_redirects=False, headers=headers) as client:
            health = client.get(base + '/health')
            info = client.get(base + '/')
        if health.status_code != 200 or info.status_code != 200:
            print('FAIL: backend health or configuration endpoint rejected the request.')
            return 1
        print('PASS:', info.json().get('service'), info.json().get('model'), info.json().get('auth_mode'))
        print('Liveness does not prove model inference. Use --live for a synthetic model test.')
        return 0
    except httpx.HTTPError:
        print('FAIL: backend unreachable. Check the server, identity and URL.')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
