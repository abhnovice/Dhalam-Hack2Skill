import hashlib
import hmac
import json
from io import BytesIO

import httpx
from fastapi.testclient import TestClient
from PIL import Image

from backend.app import Settings, create_app
from backend.triage.schemas import ThreatAnalysisResponse
from backend.whatsapp import WhatsAppConfig

CONFIG = WhatsAppConfig(
    verify_token="test-verification", app_secret="test-app-secret",
    access_token="test-access-token", phone_number_id="123456789",
    graph_version="v26.0", allowed_senders=("919876543210",),
)
RESULT = ThreatAnalysisResponse(
    scam_probability=82, threat_level="HIGH", scam_category="Impersonation",
    summary="A caller is pressuring you to send money.",
    coercion_markers=["Urgency", "Payment demand"],
    recommended_actions=["Pause and verify with an official number."],
)


def event(message):
    return {"object": "whatsapp_business_account", "entry": [{"changes": [{"value": {
        "metadata": {"phone_number_id": CONFIG.phone_number_id}, "messages": [message],
    }}]}]}


def signed(body, secret=CONFIG.app_secret):
    raw = json.dumps(body, separators=(",", ":")).encode()
    signature = hmac.new(secret.encode(), raw, hashlib.sha256).hexdigest()
    return raw, {"x-hub-signature-256": f"sha256={signature}"}


def make_client(handler, analyze, config=CONFIG):
    return TestClient(create_app(
        Settings(provider="gemini"), httpx.MockTransport(handler),
        whatsapp_config=config, whatsapp_analyzer=analyze,
    ))


def test_webhook_is_closed_until_configured_and_verifies_handshake():
    async def analyze(_):
        raise AssertionError("No AI call expected")

    with make_client(lambda _: httpx.Response(200), analyze, WhatsAppConfig()) as api:
        assert api.get("/webhooks/whatsapp").status_code == 503
        assert api.post("/webhooks/whatsapp", json={}).status_code == 503
    with make_client(lambda _: httpx.Response(200), analyze) as api:
        query = {"hub.mode": "subscribe", "hub.verify_token": CONFIG.verify_token,
                 "hub.challenge": "12345"}
        response = api.get("/webhooks/whatsapp", params=query)
        assert response.status_code == 200 and response.text == "12345"
        query["hub.verify_token"] = "wrong"
        assert api.get("/webhooks/whatsapp", params=query).status_code == 403


def test_unsigned_and_wrong_sender_never_reach_ai_or_graph():
    async def analyze(_):
        raise AssertionError("No AI call expected")

    def handler(_):
        raise AssertionError("No Graph call expected")

    message = {"id": "wamid.1", "from": "919876543210", "type": "text",
               "text": {"body": "CHECK: send money"}}
    with make_client(handler, analyze) as api:
        assert api.post("/webhooks/whatsapp", json=event(message)).status_code == 403
        wrong = event({**message, "from": "919999999999"})
        raw, headers = signed(wrong)
        assert api.post("/webhooks/whatsapp", content=raw, headers=headers).json() == {"status": "ok"}


def test_text_requires_per_submission_consent_then_analyzes_once():
    calls, replies = [], []

    async def analyze(payload):
        calls.append(payload)
        return RESULT

    def handler(request):
        assert request.url.path.endswith("/123456789/messages")
        assert request.headers["Authorization"] == "Bearer test-access-token"
        replies.append(json.loads(request.content)["text"]["body"])
        return httpx.Response(200, json={"messages": [{"id": "outbound"}]})

    with make_client(handler, analyze) as api:
        for message in (
            {"id": "wamid.1", "from": "919876543210", "type": "text",
             "text": {"body": "I got a suspicious message"}},
            {"id": "wamid.2", "from": "919876543210", "type": "text",
             "text": {"body": "CHECK: A caller wants money now"}},
        ):
            raw, headers = signed(event(message))
            assert api.post("/webhooks/whatsapp", content=raw, headers=headers).status_code == 200
            assert api.post("/webhooks/whatsapp", content=raw, headers=headers).status_code == 200
    assert len(replies) == 2
    assert "CHECK:" in replies[0] and "Google AI" in replies[0]
    assert "Concerning" in replies[1] and "Verify independently" in replies[1]
    assert len(calls) == 1 and calls[0].text == "A caller wants money now"
    assert calls[0].consent is True


def test_consented_image_downloads_only_meta_media_and_checks_size():
    buffer = BytesIO()
    Image.new("RGB", (2, 2), "red").save(buffer, format="PNG")
    data = buffer.getvalue()
    calls = []

    async def analyze(payload):
        calls.append(payload)
        return RESULT

    def handler(request):
        if request.url.path == "/v26.0/1234567890":
            return httpx.Response(200, json={"url": "https://lookaside.fbsbx.com/media",
                                              "mime_type": "image/png", "file_size": len(data)})
        if request.url.host == "lookaside.fbsbx.com":
            return httpx.Response(200, content=data)
        if request.url.path.endswith("/messages"):
            return httpx.Response(200, json={"messages": [{"id": "outbound"}]})
        raise AssertionError("Unexpected Graph request")

    message = {"id": "wamid.image", "from": "919876543210", "type": "image",
               "image": {"id": "1234567890", "caption": "CHECK"}}
    raw, headers = signed(event(message))
    with make_client(handler, analyze) as api:
        assert api.post("/webhooks/whatsapp", content=raw, headers=headers).status_code == 200
    assert len(calls) == 1 and calls[0].image_mime_type == "image/png"
    assert calls[0].image_base64


def test_secret_setup_passes_values_only_on_stdin(monkeypatch):
    from backend import check

    values = {
        "WHATSAPP_GRAPH_VERSION": CONFIG.graph_version,
        "WHATSAPP_PHONE_NUMBER_ID": CONFIG.phone_number_id,
        "WHATSAPP_ALLOWED_SENDERS": CONFIG.allowed_senders[0],
        "WHATSAPP_VERIFY_TOKEN": CONFIG.verify_token,
        "WHATSAPP_APP_SECRET": CONFIG.app_secret,
        "WHATSAPP_ACCESS_TOKEN": CONFIG.access_token,
        "GOOGLE_CLOUD_PROJECT": "test-project",
    }
    for name, value in values.items():
        monkeypatch.setenv(name, value)
    commands, inputs = [], []

    def fake_gcloud(*args, capture=False):
        commands.append(args)
        return "" if capture else None

    def fake_run(args, **kwargs):
        commands.append(args)
        inputs.append(kwargs["input"])
        return None

    monkeypatch.setattr(check, "gcloud", fake_gcloud)
    monkeypatch.setattr(check.subprocess, "run", fake_run)
    check.setup_whatsapp()
    assert set(inputs) == {CONFIG.verify_token, CONFIG.app_secret,
                           CONFIG.access_token, CONFIG.allowed_senders[0]}
    argv = repr(commands)
    assert all(secret not in argv for secret in inputs)
