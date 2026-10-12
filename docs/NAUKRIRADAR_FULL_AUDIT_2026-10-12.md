# NaukriRadar Full-Stack Risk-Based Audit

**Review date:** 2026-10-12  
**Repositories reviewed:**  
- Backend: https://github.com/Satya2008/autoapply-backend  
- Frontend: https://github.com/Satya2008/autoapply-frontend  

**Backend main HEAD reviewed:** `55572bd6895026946d03fbc12fbc109f14c94af9`  
**Frontend main HEAD reviewed:** `ee90b5fe7d3a3dc1b20a45c4ec8845df98820e53`

## Executive summary

The repositories show a substantial implementation: Java/Spring Boot microservices, session authentication, profile/resume workflows, job discovery and matching, application planning, a Selenium worker, AI-assisted fill/review, an admin UI, and a React/Vite frontend. The frontend has CI for type-checking, unit tests, and a production build; the backend CI provisions MySQL, Redis, Kafka and S3-compatible storage and runs Gradle build.

This is a **source-level, risk-based review**, not a claim that every mathematically possible permutation has been executed. The codebase has hundreds of paths and external dependencies; reliable exhaustive coverage requires automated scenario generation and runtime testing. The repository files and selected critical flows were inspected, but CI was not run by this review. Findings below distinguish confirmed code observations from risks that still need runtime verification.

## Findings and recommended order

### P0 — No confirmed P0 finding from this source inspection

No claim is made that the application is production-safe merely because no P0 was confirmed. Production deployment, environment configuration, secret rotation, authorization behavior and live browser flows need separate verification.

### P1 — High priority

#### P1-01: Browser session token is persisted in localStorage
**Evidence:** Frontend `src/app/session.tsx` saves the complete `SessionUser`, including its bearer token, in `localStorage` under `naukriradar.session.v2`. `src/api/client.ts` sends that token in the Authorization header.

**Risk:** Any successful same-origin XSS can read and exfiltrate a persistent bearer token. This is not automatically a vulnerability by itself, but the token is exposed to JavaScript and the session is persisted across browser restarts.

**Recommendation:**
- Prefer a server-managed, HttpOnly, Secure, SameSite cookie/BFF design if architecture permits.
- If bearer tokens remain in localStorage, shorten lifetime, ensure server-side revocation, enforce CSP, avoid unsafe HTML injection, audit dependencies, and never log tokens.
- Add security tests for logout, logout-everywhere, disabled accounts, role changes and expired/revoked sessions.
- Confirm production uses HTTPS.

#### P1-02: Automatic submission relies on configurable portal risk/selector configuration
**Evidence:** Backend `core-api/.../RiskClassifier.java` gives precedence to enabled portal configuration, then runtime high-risk and low-risk domain lists. `ApplyPlanner.java` queues only LOW-risk applications when auto-apply is enabled and limits allow. `apply-worker/.../BrowserApplyEngine.java` can press Submit when called with `submit=true` and configured selectors.

**Risk:** A mistaken admin portal risk setting or bad selector mapping could enable automatic submission for an unintended domain or click the wrong control. A configurable LOW risk band is not the same as a hard-coded allowlist.

**Recommendation:**
- If product policy is “auto-submit only on Greenhouse, Lever and Ashby,” enforce a canonical, server-side allowlist independently of editable portal settings.
- Validate normalized hostname and HTTPS; reject userinfo, IP literals and deceptive lookalike domains unless explicitly approved.
- Require portal-specific success verification and a safe dry-run before enabling submission.
- Add a hard safety gate immediately before submit, not just when planning the application.
- Keep CAPTCHA and unknown form states as NEEDS_YOU; do not try to bypass anti-bot controls.

#### P1-03: Auto-submit and Fill & Review are distinct paths and need separate safety tests
**Evidence:** `BrowserApplyEngine` has an explicit `submit` parameter and can submit; `FormReviewer` documents and implements a user-reviewed flow that never presses Submit.

**Risk:** Safety guarantees for Fill & Review do not automatically apply to the separate auto-submit path. A caller/configuration regression could unintentionally use the submit-capable engine.

**Recommendation:**
- Use separate types/interfaces or explicit capabilities for “fill-only” and “submit-enabled”.
- Default every new portal and test fixture to dry-run/fill-only.
- Add tests proving the review endpoint never submits, including error and fallback branches.
- Add a final state/authorization check immediately before a submission action.

#### P1-04: Screenshots and application evidence may contain personal data
**Evidence:** Browser worker captures screenshots on errors; application worker includes a `ScreenshotStore` component. Screenshots from application forms can contain names, emails, phone numbers, resume details and answers.

**Risk:** Sensitive personal data may persist longer than intended or be exposed through an over-broad internal endpoint/log.

**Recommendation:**
- Verify screenshot access is owner/admin-authorized, storage is private, URLs expire, and retention/deletion policy is enforced.
- Redact or avoid storing form screenshots where possible.
- Never include credentials, session cookies, tokens or full resume text in logs.
- Add tests for cross-user access denial and expired download links.

#### P1-05: Repository README appears stale about authentication
**Evidence:** Backend `README.md` contains a “There is no login yet” passage describing an `X-User-Id` caller flow, while current repository includes `AuthController`, `AuthService`, frontend sign-in/session code, and the latest commits describe session authentication and gateway session invalidation.

**Risk:** Developers may follow obsolete insecure setup instructions, misunderstand the production auth model, or accidentally expose dev-only user endpoints.

**Recommendation:**
- Update README to describe current sign-up/sign-in, token/session handling, admin bootstrap, first-password change, and local test setup.
- Clearly label `/api/v1/dev/users` as test/development-only and verify it is disabled or inaccessible in production.
- Document which gateway routes are public, authenticated, admin-only, and internal-only.

### P2 — Important reliability and correctness improvements

#### P2-01: SSE reconnect behavior should distinguish transient failures from permanent auth/client failures
**Evidence:** Frontend `src/api/sse.ts` reconnects after any non-OK response and uses exponential delay capped at 30 seconds, but does not explicitly stop on 401/403 or honor server replay IDs.

**Risk:** A revoked session can produce a retry loop; events occurring during disconnection may be missed if the backend does not replay them.

**Recommendation:**
- Stop/re-authenticate on 401/403; surface a user-visible disconnected state.
- Add jitter to retry delay and respect Retry-After where appropriate.
- Implement event IDs and Last-Event-ID replay if the server supports durable replay; otherwise perform a state refresh after reconnect.
- Test chunk boundaries, CRLF line endings split across chunks, keep-alive comments, malformed events, reconnect, cancellation and unmount cleanup.

#### P2-02: Form scanning is generic but not universal
**Evidence:** `FormReviewer` scans visible `input`, `textarea`, and `select`; maps labels using regex rules; supports standard radio/select and certain combobox DOM patterns; sends unanswered required questions to AI. `BrowserApplyEngine` relies on configured CSS selectors.

**Risk:** Shadow DOM, custom controls without native inputs, nested iframes, dynamic multi-step forms, file-upload widgets, unusual ARIA labels, validation messages and portal-specific widgets may not be detected or filled reliably.

**Recommendation:**
- Keep the generic scanner as fallback, add tested adapters for Greenhouse/Lever/Ashby, and rescan after each step or DOM transition.
- Use explicit confidence scores and only auto-fill high-confidence mappings.
- Verify each field after filling; do not count “typed text” as a confirmed selection.
- Store sanitized diagnostics: portal, field type, mapping rule, confidence, failure reason and step number.
- Maintain synthetic test fixtures for native inputs, radios, checkboxes, selects, React-style comboboxes, hidden file inputs, iframes and multi-step forms.

#### P2-03: Option matching must avoid unsafe fuzzy selections
**Evidence:** `FormReviewer.bestOption` matches exact, containment and substring relationships.

**Risk:** A short answer such as “No” can match an unintended longer option; education, sponsorship, authorization, veteran and disability answers have meaningful consequences.

**Recommendation:**
- Prefer exact normalized match, then an explicit per-question synonym map.
- Do not use generic substring matching for legal/eligibility or self-identification questions.
- Require human confirmation when more than one option is plausible.
- Add adversarial tests for “No” vs “Not applicable”, “Master” vs “Master’s degree”, sponsorship, authorization and country-specific work eligibility.

#### P2-04: Required-field checks cannot guarantee the portal will accept a submission
**Evidence:** `BrowserApplyEngine.requiredButEmpty` inspects native required controls and skips CAPTCHA-related fields; a configured success selector is used after submission.

**Risk:** Many forms have JavaScript validation, server-side rejection, asynchronous uploads, custom required attributes or multi-step validation not represented by native `required`.

**Recommendation:**
- Detect visible validation messages and server errors after a submit attempt.
- Treat “submit clicked” as UNKNOWN until a portal-specific confirmation is observed.
- Avoid blind retries after an uncertain submit, because they may create duplicate applications.
- Add per-portal confirmation and error selectors, with explicit UNKNOWN recovery UX.

#### P2-05: Frontend API parsing should tolerate non-JSON error/success responses
**Evidence:** `src/api/client.ts` parses successful response bodies with `JSON.parse(text)`; errors are converted into an `ApiError`.

**Risk:** A proxy HTML error page or an empty/non-JSON response with an unexpected success status can cause an unhelpful JSON parse exception.

**Recommendation:**
- Check content type before parsing; produce a typed protocol error with a short safe message.
- Add tests for JSON, empty 204, malformed JSON, HTML 502, network failure, abort, 401 and 403.

#### P2-06: Direct resume upload needs end-to-end object ownership and integrity checks
**Evidence:** Frontend supports a presigned upload URL followed by a confirmation call.

**Risk:** The flow depends on backend validation of the object key, user ownership, declared content type/size, actual stored object size, expiration and content signature at confirmation time.

**Recommendation:**
- Ensure confirmation accepts only a key issued to the same authenticated user and verifies object metadata/content before making it the active resume.
- Reject mismatched size/type, expired uploads and keys belonging to another user.
- Test interrupted upload, expired URL, repeated confirmation, malicious filename and storage outage.

#### P2-07: The CI pipelines do not prove deployed integration health
**Evidence:** Frontend CI runs npm ci, typecheck, tests and Vite build. Backend CI starts MySQL, Redis, Kafka and SeaweedFS and runs Gradle build.

**Risk:** A green build does not prove Vercel environment variables, CORS, gateway routing, OAuth/session config, live AI provider credentials, browser binaries, cloud storage permissions or production portal behavior are correct.

**Recommendation:**
- Add a staging smoke test after deploy, using synthetic accounts/data and no real application submission.
- Add frontend-to-gateway contract tests and test API route coverage against OpenAPI.
- Track CI status and test count; add dependency and secret scanning.
- Keep live job portals out of destructive CI tests; use local fake portals for browser tests.

### P3 — Product quality and maintainability

- Add a single application lifecycle diagram and an authoritative status-transition table. Keep “opened job”, “filled”, “submitted”, “portal-confirmed” and “user-marked-applied” distinct.
- Add a stable correlation ID from frontend request → gateway → service/event → worker → notification.
- Add dead-letter queue inspection/replay controls with role checks and duplicate-safe replay.
- Make worker retries idempotent and bounded; never retry an uncertain submission automatically.
- Add per-user quotas and backpressure for fetch, match, AI and apply runs.
- Add a visible “why this match?” explanation and record the matching model/rules version for reproducibility.
- Add accessibility checks: keyboard navigation, focus management for dialogs, labels, error association, reduced motion, contrast and mobile overflow.
- Ensure loading, empty, stale, error, unauthorized and partial-success states are covered for every page.
- Update user-facing copy where it says “one click” if a CAPTCHA, account login, missing field or manual confirmation may still be required.

## Risk-based combination / probability matrix

Exhaustive enumeration of all combinations is not practical. Use pairwise coverage for independent factors, then add targeted high-risk multi-factor scenarios. “Likelihood” below is a qualitative engineering estimate, not measured production probability.

| Scenario | Factors combined | Risk | Likelihood estimate | Required expected behavior |
|---|---|---:|---|---|
| Session expires during SSE stream | expired token + active page + reconnect | High | Medium | Stop retry loop, clear session, show sign-in |
| Role changed while admin UI open | cached frontend role + revoked backend session | High | Low–Medium | Backend denies; frontend refreshes/clears session |
| Duplicate apply run | double click + concurrent request + retry | High | Medium | One application per user/job; no duplicate submit |
| Portal misconfigured as LOW | admin config + new hostname + submit enabled | Critical | Low but high impact | Server-side allowlist blocks submission |
| CAPTCHA appears after prefill | dynamic page + challenge + submit path | High | Medium | Pause NEEDS_YOU; never bypass |
| Upload succeeds but confirm fails | S3 PUT + timeout + retry | Medium–High | Medium | Idempotent confirmation; verify owner/object |
| Submit succeeds but response times out | browser click + lost response + worker retry | Critical | Medium | Mark UNKNOWN; verify before any retry |
| AI gives ambiguous eligibility answer | unclear label + fuzzy options + auto-submit | Critical | Medium | Do not guess; require user review |
| Multi-step form adds required field | async DOM change + stale scan + submit | High | High | Rescan each step; don't claim complete |
| API gateway returns HTML 502 | proxy failure + JSON parser | Medium | Medium | Clear network error; safe retry only for idempotent reads |
| Browser worker restarts mid-run | queued event + partially completed form + retry | High | Medium | Recover state; no blind duplicate submission |
| Screenshot contains candidate PII | browser failure + stored screenshot + broad access | High | Medium | Owner/admin access, retention and redaction |
| Job listing changes after matching | stale job + apply attempt + closed role | Medium–High | Medium | Revalidate active status before queue/submit |
| Same job has tracking URL variants | URL normalization + concurrent fetch | Medium | High | Canonicalize and deduplicate by source ID/canonical URL |
| AI provider unavailable | rate limit + timeout + queued application | Medium | Medium | Fallback or NEEDS_YOU; bounded retries and status |
| Frontend deployed with wrong API base | Vercel env + gateway route + CORS | High | Medium | Deployment smoke test fails clearly |
| Redis unavailable | lock/cache failure + concurrent workers | High | Low–Medium | Fail closed for critical dedupe/coordination |
| Kafka unavailable | outbox pending + worker idle | High | Low–Medium | Outbox backlog visible; resume after recovery |
| User changes profile during run | stale answer snapshot + queued apply | Medium–High | Medium | Snapshot/version answers; require review for stale sensitive fields |
| Download link expires | resume download + expired presigned URL | Low–Medium | Medium | Request fresh URL; don't expose storage internals |

## Test strategy to close the gaps

### 1. Frontend
- Run `npm ci && npm run typecheck && npm test && npm run build`.
- Add tests for session restoration, logout, 401/403, admin routes, empty/error/loading states, SSE reconnect/cancel and direct upload failure.
- Add a route/API contract test that checks each page's endpoint and expected DTO against the gateway/OpenAPI contract.

### 2. Backend
- Run `./gradlew build` in the same dependency environment documented in README.
- Run `python scripts/smoke-test.py` after local services are healthy.
- Add tests for auth/role changes, dev endpoints disabled in production, cross-user resource access, URL allowlisting, duplicate requests, uncertain submit recovery, outbox/Kafka outage and Redis outage.
- Run concurrency tests repeatedly, not just once.

### 3. Browser automation
Use a local fake application portal and synthetic profiles only:
- standard HTML fields;
- radio/checkbox/select;
- custom combobox;
- delayed fields and multi-step forms;
- hidden file upload;
- validation failure;
- CAPTCHA placeholder that must pause (no solving/bypass);
- submit timeout with unknown result;
- portal confirmation and duplicate prevention.

### 4. Deployment
- Verify Vercel API base and rewrite behavior.
- Verify gateway CORS, HTTPS, secrets and service health.
- Run a staging synthetic flow from signup → profile → resume → job search → matching → fill/review, stopping before real submission.
- Confirm logs, metrics and alerting show failures without exposing personal data.

## Suggested implementation backlog

1. **P1:** Enforce a server-side auto-submit allowlist and safety gate.
2. **P1:** Secure session token handling and verify production cookie/HTTPS/CSP strategy.
3. **P1:** Reconcile README with current authentication and development endpoints.
4. **P1:** Add uncertain-submit recovery and screenshot privacy controls.
5. **P2:** Fix SSE permanent-failure handling and define replay/state-refresh semantics.
6. **P2:** Add form-field confidence and exact/synonym matching for sensitive answers.
7. **P2:** Add upload ownership/integrity tests and malformed API response tests.
8. **P2:** Build a synthetic portal test suite for dynamic/multi-step forms.
9. **P2:** Add post-deploy staging smoke tests and API contract checks.
10. **P3:** Improve accessibility, observability, status explanations and operational dashboards.

## What is and is not verified

**Verified by source inspection:** repository HEADs above, frontend build/test scripts, backend CI configuration, localStorage token persistence, SSE retry behavior, configurable risk classification, separate review and submission engines, generic form scanner behavior, and stale README wording.

**Not verified in this review:** CI pass/fail state, runtime test results, live Vercel configuration, production secrets, real browser behavior against job portals, real CAPTCHA/session recovery, deployment security headers, and all source files/paths. Do not interpret qualitative likelihood estimates as incident statistics.

## Conclusion

The highest-value work is not a wholesale rewrite or an immediate Selenium replacement. Keep the existing architecture, close the submission safety and session-security gaps first, then improve form intelligence with confidence scoring, portal adapters, step-by-step rescanning, and synthetic end-to-end tests. This will produce a more reliable system than adding another AI browser tool without measuring current failure cases.
