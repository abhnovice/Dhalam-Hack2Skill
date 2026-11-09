# Dhalam: research and visualisation proposal

> Research and validation snapshot from 8 November 2026. Its test counts and
> build status are historical; see [the repository README](../README.md) and
> [the app runbook](../view_app.md) for the current demo state.

Research date: 8 November 2026. Scope: native Android (Compose), Flutter Android, Flutter iOS.

## Implementation update

The first five visual improvements are implemented in both UI codebases:

- Scam results show up to three warning-sign cards, with extracted evidence in a separate expandable section. Full explanations remain available in the existing details view.
- Security tools → Findings shows counted, selectable status categories, observation time, an empty-category message and a reset filter.
- History shows expandable observation cards and comparisons between each adjacent retained pair. Native history contains summary changes; Flutter can also show stored per-app access changes. The oldest retained entry is explicitly labelled as the baseline.
- Android permission entries show grant bars and app drill-downs; native Android now exposes the overview before searching. Each bar measures granted apps among the apps listed for that permission, not activity or danger. iOS offers a three-step manual review instead.
- Link inspection highlights the hostname, scheme and path, keeps structural warnings visible, and clears the previous result when input changes. Links are not fetched.

The remaining sections describe the broader proposal. Dashboard-level summary shortcuts, more extensive guided reporting, capture-strip redesign, screenshot annotations and source-linked evidence remain future work. Existing consent, reporting and capture behaviour is preserved. No new charting dependency or backend endpoint was added.

Validation: Flutter analysis is clean; all 22 Flutter tests and 24 native Android unit tests pass. Native Android and Flutter Android debug builds succeed. The default combined-architecture iOS simulator build encountered a Flutter framework packaging error; an ARM64-only Xcode simulator build succeeded with `ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO`. Physical-device visual review and signed release packaging were not performed. Existing files under `build/apks/` were not replaced.

## Recommendation

Make each screen answer three questions: **What did Dhalam observe? Why does it matter? What can I do next?** Add visual explanations around those decisions. Prioritise warning-sign cards, finding summaries, and observed-change timelines before adding charts.

The existing triage result already has a prominent headline, contextual icon, short summary, first recommended action and expandable details. Preserve that hierarchy. Most of the opportunity is in the dense findings, history, permissions and explanation views.

## Applications and patterns to learn from

These are documented product patterns, not independent proof that a redesign improves usability. The proposed benefits for Dhalam are hypotheses to test. Sources are official product documentation or first-party design accounts; no hands-on competitor usability study was conducted.

| Reference | Documented pattern | Adaptation for Dhalam | Boundary |
| --- | --- | --- | --- |
| [1Password Watchtower](https://support.1password.com/watchtower/) | Groups security issues into categories and lets users open affected items. | Show counted finding categories with a direct route to each issue and its action. | A category count is not a device safety score. |
| [Android Privacy Dashboard](https://support.google.com/android/answer/13530434?hl=en) | Permission-centred views show apps and access timing, with a route to change permissions. | Group visible apps by permission; show observed changes and link to relevant settings. | Dhalam's permission snapshots do not establish actual camera/microphone usage events. |
| [Apple Safety Check](https://support.apple.com/en-ca/guide/iphone/iph42f0b2d53/27/ios/27) | Guided review of sharing and access, plus a separate emergency reset path. | Offer a short, clearly sequenced privacy review with one decision per step. | Dhalam cannot reproduce Apple's privileged reset capabilities. Manual confirmations must stay labelled as such. |
| [Bitdefender Scamio Pro](https://www.bitdefender.com/consumer/support/answer/103356/) | Conversational scam assistance accepts links, messages, images and screenshots. | Keep the entry screen focused on the suspicious item, then present the assessment and actionable explanation. | Input support is not evidence of detection accuracy; do not copy unsupported competitor capabilities. |
| [Duolingo home-screen redesign](https://blog.duolingo.com/new-duolingo-home-screen-design/) | Replaced the learning tree with a guided path intended to clarify the next step. | Use a compact numbered path for reviewing a suspicious request and preparing a report. | Avoid streaks, rewards or compulsory lengthy journeys during a stressful scam check. |
| [Apple Watch Activity](https://support.apple.com/en-bh/guide/watch/apd3bf6d85a6/watchos) | Rings expose progress toward explicit activity goals, with numerical detail available. | Use a small progress indicator for a finite manual checklist, such as “3 of 5 review steps completed.” | Completion measures review activity, not protection. Do not recreate branded Activity rings or imply “100% safe.” |

## What the current code supports

- `flutter/lib/screens/triage_screen.dart`: colour/icon verdict card and expandable details; use the existing result fields to expose a few warning signs earlier.
- `app/src/main/java/com/dhalam/ap/ScamTriage.kt`: native equivalent; keep verdict wording and evidence ordering aligned with Flutter.
- `flutter/lib/screens/dashboard_screen.dart`: many vertically stacked explanatory sections; add a compact summary above them.
- `flutter/lib/screens/security_tools_screen.dart`: Findings, History, Privacy review, Link inspector and Report tabs. Findings and history are mostly textual cards/lists.
- `flutter/lib/features/security_tools.dart`: finding states, local check records, comparison logic, manual reviews and offline hostname inspection already exist. This supplies most initial visuals without a new backend.
- `app/src/main/java/com/dhalam/ap/SecurityDashboard.kt` and `SecurityToolsPanel.kt`: native surfaces for the equivalent changes.
- `backend/triage/schemas.py`: threat levels, coercion markers and extracted evidence exist, but extracted evidence is a list of strings rather than validated screenshot coordinates or source-text offsets. The schema also has `scam_probability`; its name alone does not establish calibrated probability.

Code was inspected statically, not through a running-device visual audit. Historical strategy-document claims were not treated as implementation evidence.

## Prioritised screen proposals

### 1. Scam result: warning signs with evidence — highest priority

Retain the current verdict and immediate action. Under them, show up to three warning-sign cards: an icon, a plain-language label and the relevant explanation. Example labels include “Pressure to act quickly” and “Request for payment,” only when supported by the assessment. Expand to see all evidence and citations.

Use existing `coercion_markers` and `evidence_extracted`. Initially display these in separate labelled groups: the schema does not guarantee a one-to-one relationship. To connect a marker to a specific quotation later, add validated evidence references. Highlight a quotation inside pasted text only when it matches exactly; screenshot overlays require reliable bounding boxes that the current response does not supply.

Do not turn the numeric model output into an “88% certain” gauge. A labelled threat category plus explanation is clearer about what the system actually returns. Keep “No clear scam signs” distinct from a guarantee of safety.

Illustrative layout, using synthetic content:

```text
┌──────────────────────────────────────┐
│ [Stop icon] Stop. This looks risky.  │
│ The request pressures you to pay.   │
│                                      │
│ Next step                            │
│ Verify through a trusted channel.    │
│                                      │
│ Warning signs                        │
│ [Clock] Pressure to act quickly      │
│ [Money] Request for payment          │
│                                      │
│ What you shared                      │
│ “Pay within 20 minutes”              │
│                                      │
│ [Why? Evidence and guidance]         │
│ AI can be wrong. Check independently.│
└──────────────────────────────────────┘
```

### 2. Device overview: counted status tiles — highest priority

Add four compact, tappable tiles: **Needs attention**, **Configured**, **Manual review**, **Unavailable**. Each includes an icon, count and label; selecting it filters the findings below. Display the observation time and a refresh action nearby. Allow wrapping into a single column at large text sizes.

Use actual finding states, not a weighted aggregate score. Before the first check, show “Run your first check” rather than four reassuring zeroes. Keep unavailable readings visible. If a check fails, mark retained results as previous readings. Do not sum app permission counts together with device findings as though they were equivalent risks.

### 3. History: an observed-change timeline — highest priority

Replace the wall of timestamps with a vertical timeline. Each entry shows check time, a plain-language change and expandable before/after values. Example: “Microphone permission: not granted → granted” only when both comparable readings support that claim.

Say “Observed at 10:32,” not “Changed at 10:32.” Show “First observation” for the baseline. Missing apps may reflect visibility changes. Retain the current comparison rules and 30-check limit. Richer historical comparisons require extracting reusable comparison logic; the current `changes` getter compares the latest pair only. Separate periodic checks from foreground history unless the storage explicitly integrates them.

### 4. Permissions: horizontal bars with drill-down — second priority

For Android, group visible apps by permission category and show labelled horizontal bars such as “Microphone: 4 visible apps with permission.” Tapping reveals the apps, grant states and settings action. Count distinct observed apps per category and show observation time and scope.

These bars describe grants, not use, danger or spying. On iOS, substitute a guided permission-review card because cross-app inventories are unavailable. Do not render zero-length bars that suggest no apps have access.

### 5. Link inspector: visual URL anatomy — second priority

Give the parsed hostname a prominent, selectable field, with scheme and path displayed separately. Explain user-info text before `@` and structural warnings next to the relevant part. Keep “Inspected locally; link not opened” visible.

The existing inspector returns a hostname and notes. A richer segmented display needs parser output fields. Identifying a registrable domain requires a maintained public-suffix-aware implementation; do not assume the final two labels always identify it. Avoid a green lock implying trust. Distinguish local structural analysis from any optional reputation lookup.

### 6. Privacy review and reporting: compact guided steps — second priority

Use a numbered review sequence with labelled completion ticks. Show “You marked this reviewed” and its timestamp. Opening Settings must not automatically complete the step. A fresh automated reading and a manual confirmation are different states.

Present reporting as **Review observations → Review report → Choose where to share**. The native share sheet remains the final step; do not show “Complaint filed” or “Report delivered” after opening it. Citation cards should say “Guidance source,” not imply that a regulator verified this particular assessment.

### 7. Capture awareness: a persistent status strip — second priority

Use a screen icon, explicit state and concise scope: “Capture reported while Dhalam is visible.” Active, inactive and unavailable need distinct labels. Expand to see what the platform can observe. Do not animate a radar or suggest Dhalam can stop another app's recording.

## Delivery across all three versions

| Component | Native Android | Flutter Android | Flutter iOS |
| --- | --- | --- | --- |
| Verdict and warning-sign cards | Compose component | Shared Flutter component | Same shared component |
| Finding summary | Native finding model | Shared finding model | Same layout, platform-specific availability |
| Change timeline | Native journal | Flutter journal | Flutter journal, available readings only |
| Permission bars | Visible-app grants | Visible-app grants | Manual review substitute |
| Link anatomy and review steps | Compose components | Shared Flutter components | Same components, iOS guidance |
| Capture strip | Android capability states | Android bridge states | iOS bridge states |

Implement shared visual semantics in two UI codebases, not three separate designs. Keep wording, status meanings, ordering and synthetic test scenarios aligned. Use existing Flutter and Compose primitives for these small visuals; a charting dependency is unnecessary for the first iteration. Preserve platform navigation, accessibility and native share behaviour.

Start with result evidence cards, finding summaries and the change timeline. Then add permission bars, link anatomy and the guided review. Defer risk trend lines, network graphs, threat maps and radar charts: the inspected data does not support their implied precision or relationships.

## Accessibility and validation

Every status needs text and an icon in addition to colour. Use at least 4.5:1 contrast for ordinary text and 3:1 for large text, following [W3C contrast guidance](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum). Avoid colour-only distinctions, as described in [WCAG](https://www.w3.org/TR/WCAG21/). Charts need accessible text equivalents and logical reading order. Support large text, reduced motion, TalkBack and VoiceOver; use generous touch targets.

Test the proposed screens against the existing screens with 5–8 representative users, including less technical users. This is formative feedback, not statistical proof. Counterbalance the order to reduce learning effects. Use synthetic suspicious requests only.

Measure whether users can identify the next action, explain one warning sign, locate a changed setting and distinguish “unavailable” from “configured.” Also ask whether a low concern result guarantees safety. Record completion time, wrong taps, misunderstandings and requests for help; do not rely only on visual preference ratings.

Implementation checks should cover empty history, first observation, failed/stale readings, unavailable platform features, long translated text, unknown result values, and large text. Confirm identical status semantics across all three builds. Add targeted tests for count/filter correctness and state transitions when implemented; no runtime tests are needed for this research-only document.


## Layout redesign completed — November 8, 2026

- Native Ask Dhalam leads with five sensitive-permission bars, with chat collapsed behind “Ask Dhalam”. A compact header provides one Account control.
- Findings in both clients use a donut, count filters, and expandable details. The counts describe recorded checks, not a safety score.
- Flutter Android places the sensitive-permission chart directly below the scan action; rows expand to show apps. Detailed settings panels are collapsed by default on both Flutter platforms. iOS retains guided reviews and explicit platform limitations.
- Both clients use Noto Sans from Google Fonts (native bundles it; Flutter fetches and caches it on first launch). Teal/neutral theme roles replace the default purple card, outline, and selected-navigation colors.
- Validation: Flutter analysis clean; 22 Flutter tests pass; native debug build and JVM tests pass; Flutter Android debug APK and ARM64 iOS simulator build pass.
- Visually reviewed the rebuilt native Device and Tools screens on the API 36 emulator. Screenshots: [Device](interact-with-app/screenshots/native-device-redesign.png), [Tools](interact-with-app/screenshots/native-tools-redesign.png). Flutter runtime visual review and physical-device review remain pending.
- Debug APKs rebuilt in their normal build directories. Distributable packages in `build/apks/` were not replaced.
