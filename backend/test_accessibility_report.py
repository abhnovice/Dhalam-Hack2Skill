import json

import pytest
from fastapi.testclient import TestClient

from backend.app import Settings, create_app
from backend.accessibility_report import AccessibilityReport, validate_report

PAYLOAD = {
    "consent": True,
    "observedAt": 1700000000000,
    "settings": [{"name": "Press Power button to end calls", "status": "Check in Settings"}],
    "enabledServices": [],
    "serviceListAvailable": False,
}
REPORT = (
    "Accessibility features can make your phone easier to use. The current scan could not "
    "confirm your power button preference or retrieve the enabled service list, so those "
    "details need checking in Android Settings. This does not show that your phone is unsafe. "
    "Review unfamiliar enabled services and their access, but keep tools you depend on. "
    "Some device-specific settings are not available to this scan."
)

def test_valid_report_and_word_limits():
    assert validate_report(json.dumps({"report": REPORT})).report == REPORT
    for size in (0, 49, 101):
        with pytest.raises(ValueError):
            validate_report(json.dumps({"report": " ".join(["word"] * size)}))


def test_access_report_requires_consent_and_auth(monkeypatch):
    async def fake(payload, model):
        assert payload.serviceListAvailable is False
        assert payload.enabledServices == []
        return AccessibilityReport(report=REPORT)
    monkeypatch.setattr("backend.accessibility_report.generate_report", fake)
    with TestClient(create_app(Settings(provider="gemini"))) as client:
        assert client.post("/accessibility-report", json=PAYLOAD).json() == {"report": REPORT}
        assert client.post("/accessibility-report", json={**PAYLOAD, "consent": False}).status_code == 422
        assert client.post("/accessibility-report", json={**PAYLOAD, "apps": ["not-allowed"]}).status_code == 422
        assert client.post("/accessibility-report", json={**PAYLOAD, "settings": []}).status_code == 422
    with TestClient(create_app(Settings(provider="gemini", require_google_signin=True))) as client:
        assert client.post("/accessibility-report", json=PAYLOAD).status_code == 401


def test_missing_cloud_credentials_is_actionable(monkeypatch):
    from google.auth.exceptions import DefaultCredentialsError
    async def missing(payload, model):
        raise DefaultCredentialsError("private path")
    monkeypatch.setattr("backend.accessibility_report.generate_report", missing)
    with TestClient(create_app(Settings(provider="gemini"))) as client:
        response = client.post("/accessibility-report", json=PAYLOAD)
        assert response.status_code == 503
        assert "not configured yet" in response.json()["detail"]
        assert "private path" not in response.text


def test_upstream_errors_do_not_echo_observations(monkeypatch):
    async def broken(payload, model):
        raise RuntimeError("private upstream details")
    monkeypatch.setattr("backend.accessibility_report.generate_report", broken)
    with TestClient(create_app(Settings(provider="gemini"))) as client:
        response = client.post("/accessibility-report", json=PAYLOAD)
        assert response.status_code == 502
        assert "private upstream details" not in response.text
