"""Verify actual Gemini request wiring without sending device data to Google."""
import json
from types import SimpleNamespace
import pytest
from fastapi.testclient import TestClient
from backend.app import Settings, create_app, Insight
from backend.accessibility_report import AccessibilityReport
from backend.test_accessibility_report import PAYLOAD, REPORT

@pytest.mark.parametrize("override", [False, True])
def test_module_models_roles_and_schemas_share_client(monkeypatch, override):
    from backend.triage import gemini_service
    calls = []
    answer = {
        "answer": "Camera access is unknown in this scan.",
        "findings": [{"title": "Camera access", "severity": "low",
                      "explanation": "Check the app permission in Settings.", "evidenceIds": ["camera"]}],
        "recommendations": ["Review camera permission."],
        "limitations": ["Access does not establish actual use."],
        "exposureScore": None, "scoreExplanation": None,
    }
    class Client:
        @property
        def aio(self):
            return self
        async def __aenter__(self):
            return self
        async def __aexit__(self, *args):
            pass
        @property
        def models(self):
            return self
        async def generate_content(self, **kwargs):
            calls.append(kwargs)
            body = {"report": REPORT} if kwargs["config"].response_schema is AccessibilityReport else answer
            return SimpleNamespace(text=json.dumps(body))
    monkeypatch.setattr(gemini_service, "get_client", Client)
    settings = Settings(provider="gemini", model="shared-working-model",
                        ask_model="ask-model" if override else "",
                        accessibility_model="access-model" if override else "")
    with TestClient(create_app(settings)) as client:
        assert client.post("/accessibility-report", json=PAYLOAD).json() == {"report": REPORT}
        request = {"question": "Who can use my camera?", "coverage": "Partial visible app scan.",
                   "evidence": [{"id": "camera", "kind": "permission", "subjectId": "example.app",
                                 "detail": "Camera permission", "status": "unknown", "observedAt": 1}]}
        response = client.post("/ask", json=request)
        assert response.status_code == 200
        assert response.json()["findings"][0]["evidenceIds"] == ["camera"]
    access, ask = calls
    assert access["model"] == ("access-model" if override else "shared-working-model")
    assert ask["model"] == ("ask-model" if override else "shared-working-model")
    assert access["config"].response_schema is AccessibilityReport
    assert ask["config"].response_schema is Insight
    assert "accessibility reviewer" in access["config"].system_instruction
    assert "Ask Dhalam" in ask["config"].system_instruction
    assert access["config"].max_output_tokens == 2048
    assert ask["config"].max_output_tokens == 2048
    assert access["config"].temperature == ask["config"].temperature == 0.1
    assert "consent" not in json.loads(access["contents"])
    assert json.loads(ask["contents"])["evidence"][0]["status"] == "unknown"

def test_environment_model_overrides(monkeypatch):
    monkeypatch.setenv("GEMINI_MODEL", "base-model")
    monkeypatch.setenv("GEMINI_ASK_MODEL", " chat-model ")
    monkeypatch.setenv("GEMINI_ACCESSIBILITY_MODEL", " access-model ")
    settings = Settings.from_env()
    assert settings.model == "base-model"
    assert settings.ask_model == "chat-model"
    assert settings.accessibility_model == "access-model"
