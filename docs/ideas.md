# Dhalam — repository overview and ideas

Updated: 9 November 2026.

This document records implemented capabilities and the remaining product ideas. Source implementation, build verification, and device validation are separate milestones.

## 1. Product idea

**Dhalam combines consent-based scam triage with local device-access review.** Users can ask about suspicious messages, URLs, or images, understand the reasons behind an assessment, and review the device observations available on their platform.

The chatbot name remains **Ask Dhalam**. Native Android also exposes a separate **Scam check** flow for suspicious content. Its Ask Dhalam tab combines permission summaries with an expandable device-question interface backed by local facts and the `/ask` service. Flutter opens into scam chat and offers device checks through chat options.

Dhalam does not prove a device or URL is safe, authenticate documents, intercept calls, stop payments, or determine that a granted permission is being abused. Finding counts and charts are not a safety score. Model concern scores are not calibrated probabilities.

## 2. Repository layout

Paths below are relative to this repository.

| Location | Purpose |
| --- | --- |
| `app/src/main/` | Native Android Compose UI, scanning, authentication, ObjectBox facts, background checks, and exports |
| `app/src/test/`, `app/src/androidTest/` | JVM and device/instrumentation tests |
| `flutter/lib/` | Shared Android/iOS chat, dashboards, security tools, models, and theme |
| `flutter/android/`, `flutter/ios/` | Native hosts and platform bridges |
| `flutter/test/` | Dart domain and widget tests |
| `flutter/lib/theme/app_theme.dart` | Noto Sans through the `google_fonts` package |
| `backend/` | FastAPI service, native `/ask` contract, scam triage, Gemini integration, retrieval, and tests |
| `ml/` | Synthetic-data exploration; not a trained production classifier |
| `docs/interact-with-app/` | Demo instructions, sample content, and screenshots |
| `build/apks/` | Previously prepared distributable artifacts; not automatically refreshed by debug builds |

Generated desktop/web Flutter targets do not establish security-scanner support on those platforms.

## 3. Implemented capabilities

| Capability | Native Android | Flutter Android | Flutter iOS |
| --- | --- | --- | --- |
| Text/URL/image scam chat with consent | Yes | Yes | Shared Flutter flow |
| Warning-sign evidence and contextual follow-ups | Yes | Yes | Shared Flutter flow |
| Device-fact questions through `/ask` | Ask Dhalam, with evidence citations | No equivalent ObjectBox question flow | No equivalent cross-app inventory |
| Device-lock and biometric configuration observations | Yes | Yes | Platform-limited observations |
| Visible-app permission and accessibility inventory | Yes | Yes | Unavailable; guided review instead |
| Permission search with app labels and package IDs | Yes | Yes | Inventory unavailable |
| Sensitive-permission chart | Five-row summary | Five-row expandable summary | Guided settings review |
| Findings donut, count filters, and expandable details | Yes | Yes | Shared layout with platform-specific findings |
| Local observation history and change timeline | Yes | Yes | Available readings only |
| Manual privacy checklist and local link inspector | Yes | Yes | Yes |
| Report preview and user-initiated sharing | Yes | Yes | Yes |
| Foreground capture awareness | Supported Android APIs | Supported Android APIs | Supported iOS APIs |
| Periodic local checks | Native WorkManager | Opt-in Android worker and controls | Not implemented |
| Latest device facts in ObjectBox | Yes | Not migrated | Not implemented |
| Detailed permission CSV exports | Yes | Not migrated | No cross-app inventory to export |
| Six-language interface (English, Hindi, Marathi, Bengali, Tamil, Telugu) | Shared Compose language state across sign-in, dashboard, chat and tools | Shared Flutter language notifier across chat, dashboard and tools | Same Flutter interface; platform signals differ |
| Voice input and read-aloud in scam chat | Android speech services, subject to installed voices | Platform speech services, subject to installed voices | Platform speech services, subject to installed voices |

Android inventories cover visible packages, not every installed app. Both Android manifests include launcher-app visibility queries, but matching query declarations do not guarantee identical observed inventories. Grants describe access, not actual use.

Local history retains the latest 30 successful foreground observations. Comparisons preserve unknown/unavailable states and do not treat missing readings as improvement. Summary history, ObjectBox facts, and background-worker baselines have different storage and deletion scopes.

## 4. Chat and backend

The backend is implemented, not a proposed feature:

- `POST /api/v1/analyze-threat` accepts consented text, URL text, and supported images for structured scam triage.
- `POST /ask` answers native device questions using supplied evidence and validated citations.
- Gemini on Vertex AI is the default provider; configuration and credentials stay on the server.
- Scam-triage reference retrieval can attach relevant guidance from five bundled RBI advisory summaries covering impersonation, advance fees, KYC phishing, remote-access requests, and UPI collect scams. This is a small curated pattern reference, not a comprehensive past-incident database or automatic learning from user reports. Retrieval may be unavailable or find no relevant guidance; an advisory match is not proof of fraud. Native `/ask` remains scoped to device facts.
- The optional reputation adapter reports unavailable/unconfigured states explicitly. The local link inspector operates separately and does not fetch websites or follow redirects.
- Native authentication includes Google sign-in, local biometric unlock, and a debug-only test login. Authentication and worker lifecycles differ between native and Flutter clients.

Chat has explicit submission consent, follow-up context, error states, and report preview. Local device scanning is separate from scam-chat uploads. Consult [backend documentation](../backend/README.md) for request limits, authentication modes, provider configuration, and deployment behavior.

The Python ML exploration remains a prototype. It does not train, evaluate, or ship an on-device classifier; the working AI service is the backend integration.

## 5. Visual redesign implemented

The redesign makes useful summaries visible before detailed explanations:

- **Ask Dhalam on native Android:** compact header, one Account control, prominent camera/microphone/location/contacts/messages bars, and a collapsed Ask Dhalam chat entry.
- **Flutter device dashboard:** permission chart immediately below the scan action on Android; settings and detailed observations expand on demand. iOS retains explicit limitations and guided privacy review.
- **Findings:** donut showing the composition of recorded checks, tappable category counts, and expandable findings. Categories remain Needs attention, Configured, Manual review, and Unavailable.
- **Other visual explanations:** warning-sign cards, observation-change timelines, and URL scheme/hostname/path breakdowns. Pasting HTTP(S) links into native Ask Dhalam or either scam-chat client exposes an “Inspect link locally” action before submission. Scam-chat messages retain this action after submission; up to five distinct links are inspected without opening them or sending a request.
- **Typography and color:** Noto Sans from Google Fonts (native Android bundles it with its OFL license; Flutter downloads and caches it through `google_fonts`); consistent teal/neutral light and dark theme roles replace default purple accents.

See [visualization research and delivery notes](UX_VISUALISATION_RESEARCH.md). The 468 interface strings come from `tools/i18n/*.json`; `python3 tools/i18n/generate.py` rebuilds both client tables. English journal values and filter keys are translated when drawn. Hostnames, package names, model names and OS values retain their original text.

## 6. Validation and current artifacts

Latest checks on 9 November 2026:

| Check | Result |
| --- | --- |
| Flutter analysis | Clean |
| Flutter tests | 30 passed |
| Backend tests | 61 passed |
| Hosted language request | Private Cloud Run revision deployed; synthetic Hindi request returned HTTP 200 and a Hindi assessment |
| Native JVM tests | 34 passed |
| Native Android debug APK | Built and installed in API 36 emulator |
| Flutter Android debug APK | Built |
| Flutter iOS ARM64 simulator build | Passed using Xcode build after Flutter configuration |
| Native visual review | Device-access chart and Tools findings reviewed in emulator |
| Native chat link integration | Emulator test passed: correct hostname, no submission, stale action cleared |
| Flutter runtime visual review | Pending |
| Physical-device review of redesign | Pending |

Tests include language switching, table completeness, placeholders and draw-time finding translation. The Flutter and native Android release APKs were rebuilt with the six-language UI. Both use the Android debug signing key for demo distribution; they are not production signed.

Current debug outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `flutter/build/app/outputs/flutter-apk/app-debug.apk`

The Android packages in `build/apks/` contain the current interface. The iOS `.ipa` predates the voice and language work and needs an Apple signing identity for a current device build. See [demo instructions](interact-with-app/README.md) and [Flutter setup](../flutter/README.md).

## 7. Remaining work

### Next validation and release tasks

- [ ] Visually review Flutter Android and iOS, including charts, empty states, expanded details, and guided-review screens.
- [ ] Check small screens, large text, dark mode, TalkBack/VoiceOver, contrast, and long inventories across both UI implementations.
- [ ] Run physical-device checks for capture start/stop, notification denial, settings return, process restart, biometrics, and share sheets; record device/OS/build evidence.
- [ ] Test whether users understand the next action and distinguish unavailable from configured. Confirm they do not interpret chart totals or low-concern assessments as safety guarantees.
- [ ] Prepare a current signed iOS device package and production Android signing before public distribution.
- [ ] Validate voice recognition and speech output on physical devices in each supported language; automated tests currently use fakes.

### Engineering follow-ups

- [ ] Port detailed CSV exports to Flutter Android if needed, preserving timestamps, escaping, formula protection, and user-controlled sharing.
- [ ] Decide whether Flutter needs a latest-facts question interface equivalent to native Ask Dhalam; do not imply that its scam chat already queries ObjectBox facts.
- [ ] Define installation/data continuity between the separate native and Flutter Android package IDs.
- [ ] Audit backup, full-reset, and retention behavior across journal, ObjectBox, and worker baseline stores.
- [ ] Strengthen native worker/platform-channel integration tests and add reproducible CI checks.
- [ ] Prevent drift between the two Android collectors through shared fixtures or a shared implementation.
- [ ] Specify retention, deletion, and unknown/visibility-change handling before adding detailed per-app permission history.

### Optional ideas requiring separate scope

- Richer explanations tied to observed facts and reference-backed guidance, with uncertainty and data-sharing boundaries visible.
- A broader, evaluated guidance corpus and scam-triage evaluation set; measure quality rather than claim an unsupported accuracy percentage.
- Production URL reputation integration with appropriate service terms and explicit handling of submitted URLs.
- On-device ML only after useful features, reliable labels, and evaluation criteria are established.
- Desktop/web support only with platform-specific capability definitions and implementations.

**Suggested next slice:** finish Flutter visual/accessibility review, validate on physical devices, then refresh distributable artifacts. The chatbot, backend, permission search, and Flutter Android background controls already exist and should not be treated as unimplemented ideas.
