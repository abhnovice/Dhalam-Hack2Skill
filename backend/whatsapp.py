"""Small, direct Meta Cloud API adapter for the consented Dhalam demo."""

import base64
from collections import OrderedDict, deque
from dataclasses import dataclass
import hashlib
import hmac
import logging
import os
import re
import time
from urllib.parse import urlsplit

import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import PlainTextResponse
from pydantic import ValidationError

from .triage.schemas import ThreatAnalysisRequest

logger = logging.getLogger("dhalam.whatsapp")
MAX_MEDIA_BYTES = 4 * 1024 * 1024
GRAPH_VERSION = re.compile(r"v[0-9]+\.[0-9]+\Z")
MEDIA_ID = re.compile(r"[0-9]{1,30}\Z")
PHONE = re.compile(r"[0-9]{7,20}\Z")


@dataclass(frozen=True)
class WhatsAppConfig:
    verify_token: str = ""
    app_secret: str = ""
    access_token: str = ""
    phone_number_id: str = ""
    graph_version: str = ""
    allowed_senders: tuple[str, ...] = ()

    @classmethod
    def from_env(cls):
        return cls(
            os.getenv("WHATSAPP_VERIFY_TOKEN", "").strip(),
            os.getenv("WHATSAPP_APP_SECRET", "").strip(),
            os.getenv("WHATSAPP_ACCESS_TOKEN", "").strip(),
            os.getenv("WHATSAPP_PHONE_NUMBER_ID", "").strip(),
            os.getenv("WHATSAPP_GRAPH_VERSION", "").strip(),
            tuple(item.strip().lstrip("+") for item in
                  os.getenv("WHATSAPP_ALLOWED_SENDERS", "").split(",") if item.strip()),
        )

    @property
    def ready(self):
        return (all((self.verify_token, self.app_secret, self.access_token,
                     self.phone_number_id, self.allowed_senders))
                and bool(MEDIA_ID.fullmatch(self.phone_number_id))
                and bool(GRAPH_VERSION.fullmatch(self.graph_version))
                and all(PHONE.fullmatch(sender) for sender in self.allowed_senders))


def _check_command(message):
    kind = message.get("type")
    if kind == "text":
        body = message.get("text", {}).get("body", "")
        if not isinstance(body, str):
            return None
        match = re.fullmatch(r"CHECK\s*:\s*(.+)", body.strip(), re.IGNORECASE | re.DOTALL)
        return (match.group(1).strip(), None) if match else None
    if kind == "image":
        image = message.get("image", {})
        caption = image.get("caption", "")
        media_id = image.get("id", "")
        if not isinstance(caption, str) or not isinstance(media_id, str):
            return None
        match = re.fullmatch(r"CHECK(?:\s*:\s*(.*))?", caption.strip(), re.IGNORECASE | re.DOTALL)
        if match and MEDIA_ID.fullmatch(media_id):
            return (match.group(1) or "").strip(), media_id
    return None


def _graph_base(config):
    return f"https://graph.facebook.com/{config.graph_version}"


async def _send_text(client, config, recipient, body):
    response = await client.post(
        f"{_graph_base(config)}/{config.phone_number_id}/messages",
        headers={"Authorization": f"Bearer {config.access_token}"},
        json={"messaging_product": "whatsapp", "to": recipient, "type": "text",
              "text": {"preview_url": False, "body": body[:3900]}},
        timeout=15,
    )
    response.raise_for_status()


async def _get_image(client, config, media_id):
    headers = {"Authorization": f"Bearer {config.access_token}"}
    response = await client.get(
        f"{_graph_base(config)}/{media_id}",
        params={"phone_number_id": config.phone_number_id}, headers=headers, timeout=15,
    )
    response.raise_for_status()
    metadata = response.json()
    url = metadata.get("url", "")
    mime = metadata.get("mime_type", "")
    size = metadata.get("file_size", MAX_MEDIA_BYTES + 1)
    host = urlsplit(url).hostname or ""
    if (urlsplit(url).scheme != "https" or
            not (host == "lookaside.fbsbx.com" or host.endswith(".fbcdn.net")) or
            mime not in ("image/jpeg", "image/png", "image/webp") or
            not isinstance(size, int) or size > MAX_MEDIA_BYTES):
        raise ValueError("Unsupported media")
    media = await client.get(url, headers=headers, timeout=20)
    media.raise_for_status()
    if not media.content or len(media.content) > MAX_MEDIA_BYTES:
        raise ValueError("Unsupported media")
    return base64.b64encode(media.content).decode("ascii"), mime


def _format_result(result):
    label = {"CRITICAL": "Very concerning", "HIGH": "Concerning",
             "MEDIUM": "Check carefully", "LOW": "Lower concern"}.get(result.threat_level, "Review")
    lines = [f"Dhalam: {label}", result.summary.strip()[:700]]
    if result.coercion_markers:
        lines.append("Warning signs: " + "; ".join(result.coercion_markers[:3])[:600])
    if result.recommended_actions:
        lines.append("First step: " + result.recommended_actions[0][:500])
    lines.append("AI can be wrong. Verify independently; a low-concern result does not prove safety.")
    return "\n\n".join(lines)


def create_router(config: WhatsAppConfig, analyze):
    router = APIRouter()
    # Demo-only, per-instance bounds. A durable queue/store is needed for scale.
    seen = OrderedDict()
    sender_history = {}

    @router.get("/webhooks/whatsapp")
    async def verify(request: Request):
        if not config.ready:
            raise HTTPException(503, "WhatsApp is not configured.")
        query = request.query_params
        token = query.get("hub.verify_token", "")
        challenge = query.get("hub.challenge", "")
        if (query.get("hub.mode") != "subscribe" or
                not hmac.compare_digest(token, config.verify_token) or
                not challenge.isdecimal() or len(challenge) > 100):
            raise HTTPException(403, "Verification rejected.")
        return PlainTextResponse(challenge)

    @router.post("/webhooks/whatsapp")
    async def receive(request: Request):
        if not config.ready:
            raise HTTPException(503, "WhatsApp is not configured.")
        body = await request.body()
        signature = request.headers.get("x-hub-signature-256", "")
        expected = "sha256=" + hmac.new(config.app_secret.encode(), body, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, expected):
            raise HTTPException(403, "Invalid webhook signature.")
        try:
            event = await request.json()
        except ValueError:
            raise HTTPException(400, "Invalid webhook body.") from None
        if not isinstance(event, dict) or event.get("object") != "whatsapp_business_account":
            return {"status": "ignored"}

        now = time.monotonic()
        while seen and next(iter(seen.values())) < now - 3600:
            seen.popitem(last=False)
        for entry in event.get("entry", [])[:10]:
            for change in entry.get("changes", [])[:10]:
                value = change.get("value", {})
                if value.get("metadata", {}).get("phone_number_id") != config.phone_number_id:
                    continue
                for message in value.get("messages", [])[:10]:
                    sender = message.get("from", "")
                    message_id = message.get("id", "")
                    if (sender not in config.allowed_senders or not PHONE.fullmatch(sender)
                            or not isinstance(message_id, str) or not message_id
                            or message_id in seen):
                        continue
                    command = _check_command(message)
                    if command is None:
                        reply = ("To check a message, send CHECK: followed by its text. "
                                 "To check a screenshot, send it with caption CHECK. "
                                 "Sending CHECK means you agree to send that item to Dhalam's Google AI service. "
                                 "Remove private details first.")
                    else:
                        history = sender_history.setdefault(sender, deque())
                        while history and history[0] < now - 3600:
                            history.popleft()
                        if len(history) >= 10:
                            reply = "Demo limit reached. Please try again later."
                        else:
                            history.append(now)
                            text, media_id = command
                            try:
                                image, mime = await _get_image(request.app.state.groq_client, config, media_id) if media_id else (None, "image/jpeg")
                                payload = ThreatAnalysisRequest(
                                    consent=True, text=text or None, image_base64=image,
                                    image_mime_type=mime,
                                )
                                result = await analyze(payload)
                                reply = _format_result(result)
                            except (ValueError, ValidationError):
                                reply = "Please send up to 12,000 characters or a JPEG, PNG or WebP image under 4 MiB."
                            except (httpx.HTTPError, HTTPException):
                                logger.warning("WhatsApp analysis or media fetch failed")
                                reply = "Dhalam could not complete the check. Please try again later."
                    try:
                        await _send_text(request.app.state.groq_client, config, sender, reply)
                    except httpx.HTTPError:
                        logger.warning("WhatsApp reply failed")
                        raise HTTPException(502, "WhatsApp reply failed.") from None
                    seen[message_id] = now
                    if len(seen) > 4096:
                        seen.popitem(last=False)
        return {"status": "ok"}

    return router
