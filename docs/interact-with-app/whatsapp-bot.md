# Dhalam WhatsApp bot: direct Meta test setup

This is a **developer demo** using Meta's WhatsApp Cloud API test number. It does
not use Twilio. The webhook code is in `backend/whatsapp.py`. On 9 November 2026,
the backend and Meta credentials were deployed to Cloud Run, the webhook
verification challenge returned successfully, and Meta accepted the configured
test phone ID and token. The **messages** field and app-to-account subscriptions
were completed. A real `hello` received the instruction reply, and one
consented synthetic `CHECK` received an AI assessment from the test number.
The test number is for approved testers, not a public launch. Cloud Run, Secret Manager,
and Gemini usage may consume Google Cloud credits or incur charges.

## 1. Create the Meta test channel

1. Open [Meta for Developers](https://developers.facebook.com/apps/) and create
   an app with **Connect with customers through WhatsApp** selected. In the app
   dashboard, open **Use cases → Customize → Connect on WhatsApp → Step 1: Try it
   out** (some accounts show **WhatsApp → API Setup**). Use the provided test
   number. Add your own WhatsApp number under the **To** test recipients and
   complete Meta's verification.
2. On that page, copy the test number's **Phone number ID** and **WhatsApp
   Business Account ID**. Click **Generate access token** for a temporary token.
   The **Graph version** is the `vXX.0`
   segment of Meta's sample `graph.facebook.com/vXX.0/...` API URL. In **App
   Settings → Basic**,
   reveal and copy the **App Secret**. Do not paste these into chat or commit
   them to Git. Temporary access tokens expire; replace one when it does.
3. Generate a webhook verify token locally with
   `python -c 'import secrets; print(secrets.token_urlsafe(32))'`. You choose
   this value; it is different from the App Secret and access token.

Meta provides [test numbers and webhooks](https://whatsappbusiness.com/developers/developer-hub/)
for direct development. [Meta's pricing FAQ](https://whatsappbusiness.com/resources/faq/)
describes the current free service-message allowance; check it before a wider
trial because rates and eligibility can change.

## 2. Configure Dhalam privately (completed for this demo)

In the ignored `backend/.env`, fill these names using the values above:

```dotenv
WHATSAPP_GRAPH_VERSION=v26.0
WHATSAPP_PHONE_NUMBER_ID=<Meta Phone number ID>
WHATSAPP_BUSINESS_ACCOUNT_ID=<Meta WhatsApp Business Account ID>
WHATSAPP_ALLOWED_SENDERS=<your approved WhatsApp number in international digits, without +>
WHATSAPP_VERIFY_TOKEN=<your generated verify token>
WHATSAPP_APP_SECRET=<Meta App Secret>
WHATSAPP_ACCESS_TOKEN=<Meta access token>
```

Keep the existing Google and Dhalam backend settings in that file. The sender
allowlist protects the demo from uninvited AI usage. For multiple testers, put
comma-separated international numbers with no spaces or `+` signs.

### Invite another test user

Sharing the test number alone does not give someone access. In Meta's **Step 1:
Try it out**, add the person's WhatsApp number to the **To** test recipients and
complete the verification sent to that person. Then append their number to
`WHATSAPP_ALLOWED_SENDERS` in `backend/.env`, retaining the existing number:

```dotenv
WHATSAPP_ALLOWED_SENDERS=919111111111,919222222222
```

Use international digits only, with no `+` or spaces. Apply the changed list to
Cloud Run from the repository root with `python -m backend.check --setup-whatsapp`.
Editing `.env` alone affects only local runs. Have the new tester send `hello`
and then a fictional `CHECK:` message. The test setup supports only a limited
set of approved testers. Public access requires a production WhatsApp number.

For a fresh setup or redeployment, run this from the repository root to attach
the Meta values through Google Secret Manager:

```bash
conda activate fastapi-rag
python -m backend.check --deploy --mobile-auth
```

`--deploy --mobile-auth` keeps the existing app's Google ID-token requirement.
When all WhatsApp values are present, the deployment helper adds four secret
versions, grants the Cloud Run service account access, and attaches them to the
new revision. It never includes their values in source uploads or command-line
arguments. To rotate only the Meta token or other WhatsApp values later, edit
`backend/.env` and run `python -m backend.check --setup-whatsapp`.

## 3. Connect the webhook in Meta

From the **Connect on WhatsApp** screen shown in Meta, click **Basic setup →
Step 2. Production setup** in the left sidebar, then scroll to **Configure
Webhooks**. Some accounts instead show **WhatsApp → Configuration → Webhooks**.
The webhook card is under a page named “Production setup”; you can still use
Meta's test number and do not need to register a production number for this demo.
Set:

- Callback URL: `<DHALAM_API_URL>/webhooks/whatsapp`, using the Cloud Run URL
  already in `backend/.env`.
- Verify token: the exact `WHATSAPP_VERIFY_TOKEN` value from your `.env`.
- Subscribe the WhatsApp Business Account to the **messages** field.

A green check on **Configure Webhooks** means the callback setup succeeded. Meta
may automatically subscribe **security**; that is a different field and does
not deliver incoming chat messages. Expand the card and confirm **messages**
specifically says **Subscribed**.
Meta's **Test** button sends sample data with sample phone IDs. A toast saying
**Successfully tested the messages webhook field** confirms delivery only; it
does **not** subscribe the field. In the webhook fields table, click **Subscribe**
on the **messages** row and confirm it changes to **Subscribed** or **Unsubscribe**.
Then use a WhatsApp message from the approved tester to verify the complete bot
flow.
The app must also be subscribed to the test WhatsApp Business Account itself.
Meta exposes this separately from the field toggle via
[`GET /<WABA-ID>/subscribed_apps`](https://www.postman.com/meta/whatsapp-business-platform/request/tl2wk2j/get-all-subscriptions-for-a-waba).
For this demo, Dhalam's app was added to the test account on 9 November 2026
and read back as subscribed. That account-level subscription should persist
across Cloud Run redeployments.

The GET callback answers Meta's verification challenge. Incoming POSTs require
Meta's `X-Hub-Signature-256` signature, and Dhalam accepts only the configured
Phone number ID and sender allowlist. Do not add Google authentication to this
webhook: Meta signs its requests; it cannot supply a Dhalam Google ID token.

## 4. Try it from an approved WhatsApp account

Send `hello` to the Meta test number. Dhalam should reply with instructions
without calling Gemini. Then send a synthetic example such as:

```text
CHECK: A caller says I must share my screen and send money within 20 minutes or I will be arrested.
```

For a screenshot, send a JPEG, PNG, or WebP image under 4 MiB with the caption
`CHECK`. Each `CHECK` is consent to send that one item to Google's AI service.
Remove personal details first. The bot replies with a short assessment and
first action; it does not read other WhatsApp chats or automatically inspect
forwarded messages. A message without `CHECK` gets instructions instead of an
AI analysis. The demo limits each allowed sender to 10 checks per hour.

If Meta says the webhook is verified but replies never arrive, check that the
**messages** field is subscribed, the recipient is approved for the test number,
the temporary token is still valid, and `WHATSAPP_PHONE_NUMBER_ID` matches the
test number. If Meta's sample **Test** event works and **messages** says
**Subscribed**, but a new real WhatsApp message produces no Cloud Run webhook
request, also inspect the test WhatsApp Business Account's app subscription.
Its **WhatsApp Business Account ID** appears on **Step 1. Try it out**, separately
from the Phone number ID. Meta exposes that account's subscriptions through
`GET /<WABA-ID>/subscribed_apps`; registering the callback and subscribing the
field alone may not subscribe the app to the account. Backend logs intentionally
omit submitted text and phone numbers.
This adapter handles text and images; voice notes and documents are outside the
current demo. Webhook deduplication and rate limits are per Cloud Run instance,
so a larger launch needs durable storage and a queue.
