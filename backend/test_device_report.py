import pytest
from fastapi.testclient import TestClient
from backend.app import Settings, create_app
from backend.device_report import DeviceReport

PAYLOAD = {
    "consent": True, "deviceName": "Test TV", "androidVersion": "14",
    "observedAt": 1700000000000, "totalVisibleApps": 1,
    "apps": [{"packageName": "example.tv", "label": "TV app",
              "permissions": [{"name": "android.permission.CAMERA", "status": "unknown"}]}],
}
BRIEF = "This partial device snapshot lists one app with unknown camera access. Check that permission on the television before deciding whether to change it."

def test_consent_auth_and_remote_only_schema(monkeypatch):
    async def fake(payload, model):
        assert payload.deviceName == "Test TV"
        assert payload.apps[0].permissions[0].status == "unknown"
        return DeviceReport(brief=BRIEF)
    monkeypatch.setattr("backend.device_report.generate_device_report", fake)
    with TestClient(create_app(Settings(provider="gemini"))) as client:
        assert client.post("/device-report", json=PAYLOAD).json() == {"brief": BRIEF}
        for extra in ({"consent": False}, {"address": "private"}, {"code": "123456"},
                      {"totalVisibleApps": 0}, {"apps": [PAYLOAD["apps"][0]] * 101}):
            assert client.post("/device-report", json={**PAYLOAD, **extra}).status_code == 422
    with TestClient(create_app(Settings(provider="gemini", require_google_signin=True))) as client:
        assert client.post("/device-report", json=PAYLOAD).status_code == 401

@pytest.mark.parametrize("missing", [True, False])
def test_errors_leave_local_review_available_without_leaking_details(monkeypatch, missing):
    from google.auth.exceptions import DefaultCredentialsError
    async def broken(payload, model):
        if missing:
            raise DefaultCredentialsError("private credential path")
        raise RuntimeError("private remote inventory")
    monkeypatch.setattr("backend.device_report.generate_device_report", broken)
    with TestClient(create_app(Settings(provider="gemini"))) as client:
        response = client.post("/device-report", json=PAYLOAD)
        assert response.status_code == (503 if missing else 502)
        assert "Local review still works" in response.text
        assert "private" not in response.text
