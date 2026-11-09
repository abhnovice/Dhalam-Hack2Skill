"""Dhalam checks and deployment; all project settings are read from backend/.env."""
import argparse
import asyncio
import hashlib
import json
import math
import os
from datetime import datetime, timezone
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


def deploy(mobile_auth=False):
    # Package only a fresh public-reference index; never rebuild with user data.
    from .triage.retrieval import index_ready
    index_ready()
    if mobile_auth and not os.getenv('GOOGLE_WEB_CLIENT_ID', '').endswith('.apps.googleusercontent.com'):
        raise ValueError('Configure GOOGLE_WEB_CLIENT_ID before mobile deployment')
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
        'REQUIRE_GOOGLE_SIGNIN': 'true' if mobile_auth else 'false',
        'TRUST_CLOUD_RUN_IAM': 'false' if mobile_auth else 'true',
    }
    if mobile_auth:
        config['REQUIRE_GOOGLE_SIGNIN'] = 'true'
        config['GOOGLE_WEB_CLIENT_ID'] = os.environ['GOOGLE_WEB_CLIENT_ID']
        config['ALLOWED_GOOGLE_EMAILS'] = os.getenv('ALLOWED_GOOGLE_EMAILS', '')
    gcloud('run', 'deploy', service, '--source=' + str(ROOT), '--region=' + region,
           '--service-account=' + identity, '--build-service-account=projects/' + project + '/serviceAccounts/' + build_identity,
           '--no-allow-unauthenticated',
           '--memory=512Mi', '--cpu=1', '--min-instances=0', '--max-instances=2',
           '--concurrency=4', '--timeout=90', '--quiet',
           '--set-env-vars=^|^' + '|'.join(k + '=' + v for k, v in config.items()))
    if mobile_auth:
        # Enable public transport only after the revision enforcing Google identity is ready.
        gcloud('run', 'services', 'add-iam-policy-binding', service, '--region=' + region,
               '--member=allUsers', '--role=roles/run.invoker', '--quiet', '--format=none')
    url = gcloud('run', 'services', 'describe', service, '--region=' + region,
                 '--format=value(status.url)', capture=True)
    set_key(ROOT / '.env', 'DHALAM_API_URL', url)
    print('Cloud Run URL saved to backend/.env:', url)
    print('Verified Google sign-in required.' if mobile_auth else 'Private service: Cloud Run IAM required.')
    if mobile_auth:
        from .whatsapp import WhatsAppConfig
        if WhatsAppConfig.from_env().ready:
            setup_whatsapp()


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


def setup_whatsapp():
    """Attach Meta credentials through Secret Manager without exposing values in argv."""
    from .whatsapp import WhatsAppConfig
    config = WhatsAppConfig.from_env()
    if not config.ready:
        raise ValueError('Fill all WHATSAPP_* values in backend/.env before setup')
    project = os.environ['GOOGLE_CLOUD_PROJECT']
    region = os.getenv('CLOUD_RUN_REGION', 'asia-south1')
    service = os.getenv('CLOUD_RUN_SERVICE', 'dhalam-api')
    identity = f'dhalam-runtime@{project}.iam.gserviceaccount.com'
    gcloud('services', 'enable', 'secretmanager.googleapis.com')
    existing = set(gcloud('secrets', 'list', '--format=value(name)', capture=True).splitlines())
    values = {
        'WHATSAPP_VERIFY_TOKEN': ('dhalam-whatsapp-verify-token', config.verify_token),
        'WHATSAPP_APP_SECRET': ('dhalam-whatsapp-app-secret', config.app_secret),
        'WHATSAPP_ACCESS_TOKEN': ('dhalam-whatsapp-access-token', config.access_token),
        'WHATSAPP_ALLOWED_SENDERS': ('dhalam-whatsapp-allowed-senders', ','.join(config.allowed_senders)),
    }
    bindings = []
    for env_name, (secret, value) in values.items():
        if secret not in existing:
            gcloud('secrets', 'create', secret, '--replication-policy=automatic', '--quiet')
        subprocess.run(['gcloud', 'secrets', 'versions', 'add', secret, '--data-file=-',
                        '--project=' + project,
                        '--account=' + os.getenv('GOOGLE_ACCOUNT', 'ad.anushajain@gmail.com'), '--quiet'],
                       input=value, text=True, check=True, capture_output=True)
        gcloud('secrets', 'add-iam-policy-binding', secret,
               '--member=serviceAccount:' + identity,
               '--role=roles/secretmanager.secretAccessor', '--quiet', '--format=none')
        bindings.append(f'{env_name}={secret}:latest')
    gcloud('run', 'services', 'update', service, '--region=' + region,
           '--update-secrets=' + ','.join(bindings),
           '--update-env-vars=WHATSAPP_GRAPH_VERSION=' + config.graph_version + ',WHATSAPP_PHONE_NUMBER_ID=' + config.phone_number_id,
           '--quiet')
    print('WhatsApp webhook credentials attached to Cloud Run via Secret Manager.')
    print('Configure Meta callback URL: ' + os.getenv('DHALAM_API_URL', '').rstrip('/') + '/webhooks/whatsapp')


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


def evaluation_cases():
    """Validate the versioned synthetic corpus before making any paid calls."""
    from .triage.schemas import ThreatAnalysisRequest
    path = ROOT / 'evaluation_cases.json'
    raw = path.read_bytes()
    cases = json.loads(raw)
    ids = set()
    for case in cases:
        if case['id'] in ids or case['group'] not in ('scam', 'benign', 'ambiguous', 'injection'):
            raise ValueError('Invalid or duplicate evaluation case')
        ids.add(case['id'])
        if not case['expected_levels'] or not set(case['expected_levels']) <= {'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'}:
            raise ValueError('Invalid expected levels')
        ThreatAnalysisRequest(consent=True, text=case['text'], url=case.get('url'))
    return cases, hashlib.sha256(raw).hexdigest()


async def evaluate(limit, run_live=False, analyzer=None):
    from .triage.gemini_service import analyze_threat
    from .triage.schemas import ThreatAnalysisRequest
    from .triage.config import settings
    cases, digest = evaluation_cases()
    if not 1 <= limit <= len(cases):
        raise ValueError(f'Limit must be between 1 and {len(cases)}')
    selected = cases[:limit]
    report = {
        'kind': 'synthetic text/URL regression; not real-world detection accuracy',
        'created_at': datetime.now(timezone.utc).isoformat(),
        'dataset_sha256': digest, 'model': settings.DEFAULT_MODEL,
        'planned_calls': len(selected), 'live': run_live, 'results': [],
        'limits': 'Author-labeled synthetic cases, no screenshots/audio or URL lookup. '
                  'Level agreement does not validate advice, evidence or factual claims. '
                  'Latency measures direct model calls, not the mobile/Cloud Run path.',
    }
    if not run_live:
        report['case_ids'] = [case['id'] for case in selected]
        return report
    analyzer = analyzer or analyze_threat
    for case in selected:
        start = time.monotonic()
        row = {'id': case['id'], 'group': case['group'], 'language': case['language'],
               'expected_levels': case['expected_levels']}
        try:
            async with asyncio.timeout(65):
                result = await analyzer(ThreatAnalysisRequest(consent=True, text=case['text'], url=case.get('url')))
            row.update(level=result.threat_level,
                       agreed=result.threat_level in case['expected_levels'],
                       assessment=result.model_dump())
        except Exception as exc:
            # Never persist raw provider errors, credentials or HTTP request objects.
            row.update(agreed=False, error_type=type(exc).__name__)
        row['seconds'] = round(time.monotonic() - start, 3)
        report['results'].append(row)
        # Stop on provider/network errors instead of consuming quota on repeated failures.
        if 'error_type' in row:
            break
    rows = report['results']
    completed = [row for row in rows if 'error_type' not in row]
    latencies = sorted(row['seconds'] for row in completed)
    report['summary'] = {
        'attempted': len(rows), 'completed': len(completed),
        'errors': len(rows) - len(completed), 'unattempted': len(selected) - len(rows),
        'level_agreements': sum(row['agreed'] for row in completed),
        'p50_seconds': latencies[math.ceil(len(latencies) * .5) - 1] if latencies else None,
        'p95_seconds': latencies[math.ceil(len(latencies) * .95) - 1] if latencies else None,
        'by_group': {group: {
            'completed': sum(row['group'] == group for row in completed),
            'agreements': sum(row['group'] == group and row['agreed'] for row in completed),
        } for group in ('scam', 'benign', 'ambiguous', 'injection')},
    }
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default=os.getenv('DHALAM_API_URL', 'http://127.0.0.1:18765'))
    parser.add_argument('--setup-reputation', action='store_true', help='Configure restricted Safe Browsing key and Cloud Run secret for the non-commercial demo')
    parser.add_argument('--setup-whatsapp', action='store_true', help='Attach Meta WhatsApp webhook credentials from backend/.env using Secret Manager')
    parser.add_argument('--live', action='store_true', help='Make one synthetic Vertex AI request (uses quota)')
    parser.add_argument('--deploy', action='store_true', help='Deploy a private Cloud Run demo (uses billing)')
    parser.add_argument('--mobile-auth', action='store_true', help='Deploy public HTTPS transport with required verified Google user authentication')
    parser.add_argument('--cloud-auth', action='store_true', help='Use gcloud identity in memory to check private Cloud Run')
    parser.add_argument('--build-index', action='store_true', help='Embed curated public references into local SQLite (uses Google quota)')
    parser.add_argument('--evaluate', action='store_true', help='Validate synthetic cases; add --live to call Gemini')
    parser.add_argument('--limit', type=int, default=6, help='Maximum evaluation calls (default 6; bounded by corpus size)')
    parser.add_argument('--output', type=Path, help='Write evaluation JSON to a new file (never overwrite)')
    args = parser.parse_args()
    if args.build_index:
        from .triage.retrieval import build_index
        print(json.dumps(asyncio.run(build_index()), indent=2))
        return 0
    if args.evaluate:
        if args.deploy or args.setup_reputation or args.cloud_auth:
            parser.error('Evaluation cannot be combined with deployment or service setup.')
        if args.output and args.output.exists():
            parser.error('Output already exists; select a new file.')
        try:
            report = asyncio.run(evaluate(args.limit, run_live=args.live))
        except ValueError as exc:
            parser.error(str(exc))
        encoded = json.dumps(report, ensure_ascii=False, indent=2)
        if args.output:
            with args.output.open('x', encoding='utf-8') as destination:
                destination.write(encoded + '\n')
        print(json.dumps(report.get('summary', report), ensure_ascii=False, indent=2))
        return int(args.live and (report['summary']['completed'] != args.limit or
                                 report['summary']['level_agreements'] != args.limit))
    if args.setup_reputation:
        setup_reputation()
        return 0
    if args.setup_whatsapp:
        setup_whatsapp()
        return 0
    if args.deploy:
        deploy(mobile_auth=args.mobile_auth)
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
