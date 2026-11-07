import asyncio
import logging
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from google.genai import errors
from .config import settings
from .schemas import AnalysisResult, ThreatAnalysisRequest, CoercionShieldRequest, CoercionShieldResponse
from .gemini_service import analyze_threat, check_url_reputation

app = FastAPI(title='Dhalam AI — Scam triage', version='1.1.0')
logger = logging.getLogger('dhalam')
# Cloud Run IAM protects deployed requests. Local development binds to loopback.
slots = asyncio.Semaphore(4)


@app.middleware('http')
async def bound_request(request: Request, call_next):
    # Enforce streamed body size too, including requests without Content-Length.
    if request.method == 'POST':
        body = bytearray()
        async for chunk in request.stream():
            body.extend(chunk)
            if len(body) > 5_700_000:
                return JSONResponse(status_code=413, content={'detail': 'Submission too large'})
        request._body = bytes(body)
    response = await call_next(request)
    response.headers['Cache-Control'] = 'no-store'
    return response


@app.exception_handler(RequestValidationError)
async def invalid_request(request, exc):
    # Default validation errors echo input, including uploaded images/messages.
    return JSONResponse(status_code=422, content={'detail': 'Consent and a valid message, HTTP(S) URL, or JPEG/PNG/WebP image (up to 4 MiB) are required.'})


@app.get('/health')
def health():
    return {'status': 'ok'}


@app.get('/')
def root():
    return {'service': 'Dhalam AI', 'model': settings.DEFAULT_MODEL,
            'auth_mode': 'Vertex AI (ADC)' if settings.USE_VERTEX else 'Gemini Developer API'}


@app.post('/api/v1/analyze-threat', response_model=AnalysisResult)
async def analyze_threat_endpoint(request: ThreatAnalysisRequest):
    try:
        async with asyncio.timeout(65):
            async with slots:
                result, reputation = await asyncio.gather(analyze_threat(request), check_url_reputation(request.url))
        return AnalysisResult(**result.model_dump(), model=settings.DEFAULT_MODEL,
                              analyzed_at=datetime.now(timezone.utc).isoformat(),
                              url_reputation=reputation[0], url_threat_types=reputation[1])
    except TimeoutError:
        raise HTTPException(504, 'Analysis timed out. Please try again.') from None
    except errors.APIError as exc:
        # Never log upstream response text: it may contain submitted evidence.
        logger.warning('Model request failed: code=%s', exc.code)
        raise HTTPException(503, 'Cloud analysis is temporarily unavailable. Please retry later.') from None
    except Exception as exc:
        logger.warning('Analysis failed: type=%s', type(exc).__name__)
        raise HTTPException(502, 'Could not produce an assessment. Please retry later.') from None


@app.post('/api/v1/coercion-check', response_model=CoercionShieldResponse)
def coercion_check_endpoint(request: CoercionShieldRequest):
    # The mobile app evaluates the same condition locally, without uploading telemetry.
    active = request.screen_capture_active
    combined = active is True and bool(request.accessibility_services_enabled)
    return CoercionShieldResponse(
        alert_triggered=active is True,
        severity='HIGH' if combined else 'WARNING' if active else 'UNKNOWN' if active is None else 'NORMAL',
        headline='Review screen sharing and accessibility access' if combined else 'Screen capture active' if active else 'Capture status unavailable' if active is None else 'No active capture reported',
        message='Capture and accessibility access can be legitimate. If someone is pressuring you, stop sharing before entering private information. These signals do not prove fraud.' if active else 'This observation is limited to supported detection while Dhalam is visible; it is not a device safety verdict.',
        emergency_steps=['Stop recording or sharing using the system controls or sharing app.', 'Do not reveal OTPs, PINs or passwords.', 'Review unfamiliar accessibility access in Settings.'] if active else [],
    )
