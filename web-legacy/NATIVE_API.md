# Native API integration

The native Android client uses the public HTTPS origin in `NAFS_API_BASE_URL` (or `EXPO_PUBLIC_API_BASE_URL`), defaulting to `https://nafs-one.vercel.app`. The origin must have no path, credentials, query, or fragment. The client disables redirects, refreshes near-expiry Supabase sessions, and retries a 401 once with a refreshed token and the same request body. Network and provider failures require an explicit retry.

## Deployment dependencies

Deploy the updated Next.js application before using native evening reviews, coach chat, or future-self replies against that origin. Configure the existing public `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` and the existing server-only `CLOUDFLARE_APP_KEY`; the optional `CLOUDFLARE_AI_URL` overrides the configured worker endpoint. These routes use no service-role key.

The Supabase project must have `users.timezone`, `users.deen_enabled`, `daily_checkins`, `habit_logs`, `goals`, `ai_reports`, and the user-scoped ledger from `supabase/mutations.sql`, with their ownership policies. Evening review needs no additional migration when those contracts are deployed. Coach chat additionally requires the additive `supabase/coach_conversation_versions.sql` migration **before deploying the updated route**, including on a fresh schema install. It backfills `ai_conversations.updated_at`, preserves existing rows and creation dates, and stamps a strictly advancing database version on every conversation update. The routes refuse generation when required storage is unavailable.

Future-self replies additionally require `supabase/future_self_replies.sql` **before deploying the updated future-self route**, including on a fresh schema install. The function uses the authenticated caller's RLS policies, checks the current account-calendar date, and updates only reply/delivery fields while comparing every original letter field. No existing row or table column changes. A read-only RPC preflight refuses generation when the migration or guarded eligibility is unavailable. Letter archive reads and four-field native creation remain independent of this migration.

Native media additionally requires `supabase/native_storage_policies.sql`. It creates the missing public `log-photos` bucket and constrains writes to the authenticated user's paths in `avatars`, `challenge-photos`, `screentime-shots`, and `log-photos`, including when older permissive policies remain. Legacy web avatars named `<user-id>.<extension>` remain writable by their owner. Existing public visibility and stored files are retained. Apply the missing `app_notes.sql` prerequisite before `native_records.sql`; it explicitly grants authenticated access behind its ownership policy.

## Evening review contract

`POST /api/ai/evening-verdict` accepts `Authorization: Bearer <Supabase access token>`. The server verifies the token with Supabase Auth and scopes all reads and writes to the verified user. A supplied invalid bearer header cannot fall back to browser cookies. An absent bearer header retains the existing verified browser-cookie path.

Native requests contain:

```json
{
  "date": "2026-10-10",
  "eveningText": "Finished my main priority.",
  "tasks": [{ "text": "Read", "done": true }],
  "checkin_id": "12345678-1234-1234-1234-123456789abc",
  "expected_updated_at": "2026-10-10T09:00:00.123456+00:00",
  "request_id": "a-stable-UUID-for-this-intent"
}
```

Use the exact `updated_at` string returned by Supabase, preserving fractional precision. Notes and priorities must match the saved check-in for the current account-timezone date. Notes require 1–10,000 characters, with at most seven priorities of 1–200 characters each. No supplied user ID controls ownership.

The server claims the stable intent, generates using recorded context and the saved faith-mode preference, then updates only `ai_verdict` and `updated_at` under the original version filter. A peer edit before or during generation produces a conflict. The response contains `verdict`, the saved `checkin`, `provider`, `model`, `history_saved`, and `replayed`, with `Cache-Control: no-store`. Secondary `ai_reports` failure does not turn a saved native review into a failed generation.

An identical retry replays the completed result without another provider call. A replay whose saved check-in has since changed returns a conflict instead of an obsolete record. Reusing an intent for different content is rejected. If generation fails before persistence, its claim is released. If the authoritative write succeeds but ledger completion cannot be confirmed, the claim remains and the client must reload the saved check-in before starting another intent.

Existing cookie clients may continue sending only `date`, `eveningText`, and `tasks`; they retain their existing `{ verdict }` consumption and client save flow. The native-supported starter, alignment, health, report-review and screen-analysis handlers also accept verified bearer sessions.

## Coach chat contract

`POST /api/ai/chat` verifies bearer sessions with the same no-cookie-fallback rule. Native clients send the full saved transcript plus one new user message:

```json
{
  "messages": [{ "role": "user", "content": "Help me take the next step." }],
  "conversation_id": "12345678-1234-1234-1234-123456789abc",
  "expected_updated_at": null,
  "request_id": "a-stable-UUID-for-this-send"
}
```

A new conversation uses a client-generated UUID and a null version. A continuation uses the exact saved `updated_at` string, retaining PostgreSQL fractional precision. The transcript alternates user/assistant turns, ends with the new user turn, contains at most 79 outgoing messages, and is bounded to 180,000 content characters. User messages allow 4,000 characters; replies allow 12,000. The completed conversation can contain 80 messages; start another conversation when full. New message whitespace is trimmed; saved message content remains exact.

The server compares the saved history and version before generation, then updates only that conversation under the loaded version. It uses the verified account's records and faith preference, describes unavailable sources as unknown, and discloses partial context. The response includes `reply`, the authoritative saved `conversation`, `provider`, `model`, `context_partial`, and `replayed`, with `Cache-Control: no-store`. Native chat discusses plans; applying plans remains a separate workflow.

The mutations ledger claims a send before contacting AI. Identical retries replay the saved response, and stale replays are rejected after peer edits. Provider failures release the claim before a write; ambiguous save responses and unconfirmed ledger completion retain it and require reloading saved history. Native Android encrypts the exact pending request and recent conversations under an Android Keystore key before sending. It recovers a matching saved reply on reload, retains failed messages without adding an assistant error turn, blocks duplicate taps and offline generation, and clears the user cache on sign-out. Clearing a pending retry after a successful online reload is an explicit action that keeps its message as a draft; an unfinished reply may still appear in saved history.

Existing cookie clients can still submit only `messages` and read `reply`. Shortened cookie history is matched against the saved transcript suffix, retaining earlier saved turns. The web bubble sends seven recent turns so its slice starts with a user message; the Coach page returns failures to its composer rather than saving them as assistant replies. Conversation creation dates are preserved, and the latest web conversation is selected by update time.

## Future-self reply contract

`POST /api/ai/future-self` verifies bearer sessions with the same no-cookie-fallback rule. Native clients send:

```json
{
  "letter_id": "00000000-0000-4000-8000-000000000001",
  "expected_fingerprint": "4f0b538a4af26256b79aeba1290840986807b0b6775cc3c154a486a227493427",
  "request_id": "a-stable-UUID-for-this-intent"
}
```

The digest covers, in order, exact ID, user ID, original content, raw written timestamp, and target date. Encode each field as UTF-8 preceded by its four-byte unsigned big-endian byte length, concatenate, and SHA-256 hash. Timestamp strings retain PostgreSQL fractional precision. The request contains no plaintext letter or caller-controlled ownership value. Input is bounded to 4,000 characters; the server loads the verified account's saved original.

An original letter must have no reply or delivery stamp and its revisit date must be on or before the verified account day. The server uses the existing recorded coach context, preserves unknown/unavailable-data semantics, follows faith preference, and never invents lifetime progress from recent logs. Replies contain at most 30,000 characters. RPC parameters carry the original content in the POST body, avoiding private writing in query URLs and oversized URL filters. The database compares originals and empty delivery fields atomically at the final save and stamps delivery time itself.

Every device claims `future-self-v1:<letter UUID>:<fingerprint>` in the existing user-scoped mutations ledger, independently of its client request ID. An in-flight duplicate cannot contact the provider. Before-write provider failures release the claim; ambiguous writes and unconfirmed completion retain it. A committed saved reply is returned directly on retry, recovering an uncertain ledger completion without generating again. The normal response includes `reply`, `letter`, `provider`, `model`, `context_partial`, `history_saved`, and `replayed`; saved-row recovery needs only `reply`, `letter`, and `replayed`. All responses use `Cache-Control: no-store`. Secondary report-history failure retains the authoritative letter reply.

Android encrypts the full pending intent in its existing Room v12 snapshot before sending. Exact retry, offline/process-restart retention, online saved-reply recovery, current-detail refresh, clock rollback, account changes, and cache cleanup failure are covered. A successful online reload clears an obsolete intent if its original changed or disappeared, since the guarded server write can no longer affect that original. Existing cookie clients can still send `{ letterId }` and consume `{ reply }`; they share the same due-date and guarded-save rules. This is a user-requested reply after the revisit date; it does not schedule automatic delivery or notifications.

## Recovery-plan preview and apply

`POST /api/ai/plan` verifies bearer sessions and supports the existing cookie path. Native problem requests contain a stable UUID, a trimmed 3–500 character intent, and `mode: "overcome"`:

```json
{
  "request_id": "a-stable-UUID-for-this-preview",
  "intent": "I keep scrolling at night",
  "mode": "overcome"
}
```

`POST /api/ai/plan-from-chat` accepts a native saved conversation pointer rather than caller-supplied messages:

```json
{
  "request_id": "a-stable-UUID-for-this-preview",
  "conversation_id": "00000000-0000-4000-8000-000000000001",
  "expected_updated_at": "2026-10-10T12:00:00.123456+00:00"
}
```

The server loads the verified account's full saved transcript, preserving all turns and exact timestamp precision. It checks the version before and after generation and on replay. A missing or changed conversation returns a conflict; a missing version column refuses generation. Native requests are bounded to 12,000 characters for problem previews and 4,000 for chat pointers. Supplied ownership or native transcript fields do not control context. The shared context supplies existing habits, recorded misses and possible correlates, screen time, sleep, and coach memory; unavailable records remain unknown and faith preference is respected.

The response includes `recovery` (`problem`, `approach`, 1–4 `steps`), `dropped`, `provider`, `fellBack`, `context_partial`, `source_date`, `request_id`, and `replayed`, with `Cache-Control: no-store`. Each step has `kind`, `title`, `emoji`, `reason`, and only its task/habit/challenge block. Normalization accepts flattened provider blocks, duration units, and leading emoji titles; schema and native create limits are checked again afterward. Unusable steps are omitted with a disclosure, and a wholly unusable plan returns 502. Habits have names up to 80 characters and require usable units/targets; challenge duration follows the native cadence bounds. No task, habit, challenge, completion, check-in, or pledge is created by either preview endpoint.

The existing user-scoped mutations ledger claims native generation before contacting AI. An identical retry replays without another provider call; changed content under the same ID is rejected. Failed generation releases the claim. An unconfirmed ledger completion retains an in-flight claim; the UI can explicitly clear the preview request and describe another problem. Such a new request may generate again. Cookie clients retain the default single-item plan contract and bounded last-12-message chat contract without a mandatory request ID.

Android stores drafts, exact pending preview requests, selected steps, deterministic per-request/index UUIDs, and apply progress in a Keystore-encrypted Room v13 cache. It requires local persistence before generation and before every item create. The user reviews schedules, targets, reasons, and photo proof, then taps **Add selected steps**. Items use the existing native task/habit/challenge create repositories with their stable UUID retry checks and authenticated RLS paths. First-add dates are frozen per item, so retries retain their original account-calendar date after midnight or process recreation. Each attempt first inspects the owned UUID: matching records are adopted, missing ones can be created, and changed records are left for the user to inspect. Partial success preserves already-added steps and retries only remaining selected steps. Offline generation/apply is blocked; drafts and previews remain available locally. Unreadable local retry storage blocks writes instead of replacing IDs. Clearing the preview never deletes saved feature items.

Deploy the updated planning routes before native live use. Problem planning needs the existing context and mutations tables; saved-chat planning also needs `supabase/coach_conversation_versions.sql`. This slice adds no server SQL migration. The future-self reply migration remains a separate deployment dependency. Live RLS, Keystore, and device acceptance are still pending.

## Progress and recognition

POST /api/progress accepts a verified bearer session and a weekly/monthly period with an offset from 0 to 60. The server derives timezone and faith mode from the owned profile and uses the tested buildReport and history domains. Responses include current/previous-period report data, 30-day feature history, saved review and unavailable sources. Missing observations retain recorded/pending/unrecorded/unknown coverage; they are never counted as failed opportunities. The endpoint is read-only.

POST /api/recognition reads owned lifetime activity using stable paginated queries, preserves stored awards and hides faith-specific badges when disabled. It returns existing badge/level rules as a read-only projection; viewing badges does not update the profile. Incomplete lifetime data fails honestly rather than producing a partial lifetime score.

## Structured native advice

POST /api/ai/native accepts requestId (a stable UUID), action and payload. Supported actions are habit-starter, challenge-starter, goal-starter, goal-alignment, goal-plan, health-recommend, report-review and screen-analysis. The server verifies the caller, bounds and validates input, and claims the exact action/payload in the existing mutations ledger before generation. An identical retry replays the completed result; changing the body under the same UUID is rejected. Stored advice stamps request_id in the saved pack so Android can recover a committed answer after response loss. Save errors retain an unconfirmed claim. Provider failures before a write and read-only generation failures release it. Server/provider secrets are never sent to Android.

Faith preference comes from the verified profile. Goal plans send names of actual owned linked habits. Report review freezes anchorDate in the account zone and reuses a saved review generated on the same account day. Screen analysis uses real entered totals/apps; Android retains the exact request/result and saves the summary through the guarded record RPC. Opening advice never creates feature records. The user can explicitly add validated task/habit/challenge suggestions; encrypted frozen IDs and dates, ownership checks and exact-definition inspection prevent duplicate creates on retry.

## Generic records and challenge restart

Apply supabase/native_records.sql after its existing dreams/schema-additions/app-notes prerequisites. The authenticated native_save_record function whitelists product fields for dreams, daily_logs, quran_log, screentime_logs and app_notes. Ownership is derived from auth.uid(), first inserts are serialized, and writes compare the exact loaded row before changing it. A matching existing patch is a replay; a changed row returns SQLSTATE 40001. Android stores encrypted drafts and exact pending intents before calls, uses a network-constrained account-scoped worker and adopts confirmed receipts without overwriting newer drafts. Unreadable retry storage blocks a write.

Apply supabase/native_challenge_restart.sql after challenges_v2.sql. The authenticated restart function checks the owned challenge/count and account date, keeps older history and best streaks, and archives old overlapping check-ins before a new attempt can reuse a day key. Neither migration has been applied to production in this session. Existing RLS and storage policies still require deployed-schema validation.

## Account deletion

POST /api/user/delete requires a verified bearer/cookie session and confirm set to DELETE. The server requires its existing service-role environment before any destructive action, removes only the verified account's objects from the known avatar/photo/screenshot buckets, then deletes the Auth identity and its cascaded public data. Missing optional buckets are tolerated. Storage failure or an unconfirmed Auth deletion is reported without claiming completion. The service-role key remains server-only. Tests use mocks; no live account was deleted.

## Error handling

Errors have a sanitized `{ "code": "...", "error": "..." }` body. Authentication uses 401; invalid input uses 400 or 413; peer edits, conflicting intents, and in-flight claims use 409; provider rate limits use 429; unusable provider output uses 502; temporary backend failures use 503. `result_unconfirmed` means reload the authoritative saved entry or conversation. Native UI keeps user text and offers retry/reload, and never displays raw infrastructure errors or credentials.

## Verification

Recovery-plan tests cover full saved transcript ownership/version checks, replay and in-flight claims, required storage, provider failures and fallback, normalization/native create limits, and cookie compatibility. Native tests also cover explicit selection/apply, partial saves, frozen dates/UUIDs across restart, response-loss inspection recovery, conflicting records, draft flush, account changes, bearer bodies, and actual SDK account/ID filters. The latest local gate passed 268 Android tests across 41 classes, 430 backend tests across 37 files and nine instrumented tests on Android 16. Debug/test APK assembly, unsigned R8 APK/AAB assembly, TypeScript checking and changed-file ESLint passed. Android lint reports 0 errors and 12 warnings.

Run `npm test` and `npx tsc --noEmit --incremental false` in `web-legacy`, and `testDebugUnitTest assembleDebug lintDebug` in `android`. Mocked route tests cover authenticated ownership, exact timestamp precision, concurrent duplicate claims, duplicate replay, pre-generation and concurrent-edit conflicts, ambiguous committed writes, ledger failures, secondary history failure, cookie compatibility, invalid input, and provider failures. Auth helper tests reject bearer-to-cookie fallback. Native MockEngine tests cover token refresh, origin restrictions, redirects, account changes, bounded long conversation responses, and response/error handling. Coach state tests cover exact retries, process restart, conflict/draft retention, offline blocking, saved-reply recovery, account changes, and lifecycle refresh.

On 2026-10-10, the signed-in phone was checked without changing account records. The production origin returned HTTP 404 for /api/progress, /api/recognition and /api/ai/native during unauthenticated availability probes; the phone's new report and badge screens could not refresh. The updated backend and prepared SQL migrations have not been deployed in this session. Authenticated generation, SQL/RLS execution, production signing and manual accessibility/delivery checks remain required for end-to-end acceptance.
