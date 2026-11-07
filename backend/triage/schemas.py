import base64
import binascii
from io import BytesIO
from typing import Literal
from urllib.parse import urlsplit

from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

MAX_IMAGE_BYTES = 4 * 1024 * 1024


class ThreatAnalysisRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)
    consent: bool = False
    text: str | None = Field(None, max_length=12000)
    url: str | None = Field(None, max_length=4096)
    image_base64: str | None = Field(None, max_length=5592408)
    image_mime_type: Literal['image/jpeg', 'image/png', 'image/webp'] = 'image/jpeg'

    @field_validator('url')
    @classmethod
    def validate_url(cls, value):
        if not value:
            return None
        try:
            parts = urlsplit(value)
            port = parts.port
            if parts.scheme not in ('http', 'https') or not parts.hostname or any(c.isspace() or ord(c) < 32 for c in value) or '\\' in value:
                raise ValueError()
            if port is not None and not 1 <= port <= 65535:
                raise ValueError()
        except ValueError:
            raise ValueError('Enter a valid HTTP or HTTPS URL') from None
        return value

    @model_validator(mode='after')
    def validate_submission(self):
        if not self.consent:
            raise ValueError('Explicit consent is required for cloud analysis')
        if not any((self.text, self.url, self.image_base64)):
            raise ValueError('Provide a message, URL or screenshot')
        if self.image_base64:
            try:
                data = base64.b64decode(self.image_base64, validate=True)
                if not data or len(data) > MAX_IMAGE_BYTES:
                    raise ValueError()
                with Image.open(BytesIO(data)) as image:
                    mime = Image.MIME.get(image.format)
                    if mime != self.image_mime_type or image.width * image.height > 20_000_000:
                        raise ValueError()
                    image.verify()
            except (ValueError, binascii.Error, UnidentifiedImageError, OSError, Image.DecompressionBombError):
                raise ValueError('Use a valid JPEG, PNG or WebP image under 4 MiB and 20 megapixels') from None
        return self


class ThreatAnalysisResponse(BaseModel):
    scam_probability: int = Field(ge=0, le=100, description='Uncalibrated AI concern score, not a measured probability')
    threat_level: Literal['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
    scam_category: str = Field(max_length=200)
    summary: str = Field(max_length=3000)
    coercion_markers: list[str] = Field(default_factory=list, max_length=12)
    recommended_actions: list[str] = Field(default_factory=list, max_length=12)
    evidence_extracted: list[str] = Field(default_factory=list, max_length=12)
    limitations: str = 'AI assessment may be wrong. Verify independently; a low score does not establish safety.'


class AnalysisResult(ThreatAnalysisResponse):
    model: str
    analyzed_at: str
    url_reputation: Literal['not_requested', 'not_configured', 'unavailable', 'no_match', 'matched']
    url_threat_types: list[str] = Field(default_factory=list)


class CoercionShieldRequest(BaseModel):
    screen_capture_active: bool | None = None
    accessibility_services_enabled: list[str] = Field(default_factory=list, max_length=100)
    foreground_app: str | None = Field(None, max_length=256)


class CoercionShieldResponse(BaseModel):
    alert_triggered: bool
    severity: str
    headline: str
    message: str
    emergency_steps: list[str] = Field(default_factory=list)
