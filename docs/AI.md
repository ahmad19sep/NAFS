# AI in NAFS

Everything the app generates — coach replies, verdicts, goal plans, starter packs,
scheduled reports — goes through one model behind one façade.

```
Next.js route (Vercel, server-side)
        ↓  POST /chat, Bearer CLOUDFLARE_APP_KEY
Cloudflare Worker
        ↓  env.AI.run(...)
Cloudflare Workers AI
        ↓
@cf/openai/gpt-oss-20b
```

The app never calls Workers AI directly, and the browser never sees a key.

---

## The façade

Routes import from [`src/lib/ai.ts`](../web-legacy/src/lib/ai.ts) and nothing else.
[`src/lib/cloudflare-ai.ts`](../web-legacy/src/lib/cloudflare-ai.ts) is the only file
that touches the network or the key.

| Function | Use for | Returns |
|---|---|---|
| `aiText(task, prompt, system?)` | one-shot generation | `string` |
| `aiChat(messages, opts?)` | multi-turn conversation | `string` |
| `aiStructured<T>(prompt, schema, system?)` | structured output | `AiResult<T>` |

There is deliberately **no image input** — see [Why there is no vision](#why-there-is-no-vision).

### Structured output

`aiStructured` is the one to use. It calls, rejects an empty reply, parses
(stripping at most one complete markdown fence — it does **not** hunt for the
first `{` and last `}`, because that turns prose into a confident wrong object),
checks the value against a runtime [`Schema`](../web-legacy/src/lib/schema.ts),
and on a parse or shape failure makes exactly **one** corrective retry telling
the model what was wrong. Transport failures are not retried here; the Worker
and caller have their own handling, and stacking retries at three layers turns
one slow request into nine. Auth and configuration errors are never retried.

It returns a discriminated result, so a caller can tell a model problem from an
outage:

```ts
const r = await aiStructured<Plan>(prompt, PLAN_SCHEMA, SYSTEM)
if (!r.ok) return NextResponse.json({ error: r.message }, { status: statusFor(r.code) })
// r.data is validated — enums, ranges, required fields all checked
```

Failure codes: `not_configured`, `unauthorized`, `rate_limited`, `unavailable`,
`invalid_json`, `schema_mismatch`. Raw model text never reaches `message`.

Schemas live together in
[`ai-schemas.ts`](../web-legacy/src/lib/ai-schemas.ts), so the habit shape that
four routes suggest and one code path creates is defined once — a model that
invents a habit `type` is rejected identically everywhere.

`aiJSON` was removed once every route moved across. It returned `null` for every
failure, so callers could not tell a bad reply from an outage and a `null` became
a silently broken feature.

**To add an AI feature:** import a function from `lib/ai.ts`. Do not import
`cloudflare-ai.ts`, and do not add a second provider without reading
[Improvement 6](#6-no-fallback-provider) first.

---

## Token budgets — read this before changing them

`gpt-oss-20b` is a **reasoning model**. It spends completion tokens thinking
before it emits a message, and the Worker only ever returns the final message.
If the budget runs out mid-reasoning you get an **empty reply, not a short one** —
the call still returns HTTP 200, `usage.completion_tokens` equals your entire
`max_tokens`, and `response` is `null`.

Measured against the live Worker with a realistic coaching prompt:

| `max_tokens` | tokens used | result |
|---|---|---|
| 300 | 300 | **no message** |
| 500 | 500 | **no message** |
| 800 | 462 | message returned |
| 1200 | 180 | message returned |

Reasoning length swings hard between identical calls (462 vs 180 above), so the
budgets in `MAX_TOKENS_FOR` carry deliberate headroom:

| Task | Budget |
|---|---|
| `chat`, `verdict` | 1500 |
| `bulk` | 1200 |
| `json` | 2000 (the Worker's ceiling) |

These are **ceilings, not spend** — billing follows tokens actually used, so
generous costs nothing. A trivial prompt ("reply OK") succeeds at 100 tokens, so
a shallow smoke test will *not* catch a budget that's too low. Re-measure with a
realistic prompt before lowering any of these.

---

## Why there is no vision

`gpt-oss-20b` is text-only, so image reading would need a second Worker route
running a vision model. That was built and abandoned. Recorded here so it isn't
rebuilt from scratch:

- A `/vision` route was deployed calling `@cf/meta/llama-3.2-11b-vision-instruct`
  with `{ image: bytes, prompt, max_tokens }`. Every call returned **500**. That
  shape is llava's documented input, not llama-3.2-vision's, which expects a
  messages array — so at minimum the contract was wrong.
- Rather than keep guessing at contracts, the feature was dropped. The models
  available are small (7B–11B), and reading exact app names and durations off a
  dark-mode phone screenshot is essentially OCR — their weakest task. A
  confidently wrong `14m` is worse than no number at all, because it silently
  corrupts the history the coach reasons over.

So screen time is **typed in**, and the screenshot is kept only as a visual record.
If you revisit this, verify the model's input contract against Cloudflare's current
docs first, then measure accuracy on real screenshots before trusting any of it.

---

## Configuration

| Name | Secret? | Where | Purpose |
|---|---|---|---|
| `CLOUDFLARE_APP_KEY` | **yes** | Vercel env vars, `.env.local` | Bearer token the Worker checks |
| `CLOUDFLARE_AI_URL` | no | optional override | defaults to the deployed Worker `/chat` |
| `APP_KEY` | **yes** | Cloudflare Worker secret | must equal `CLOUDFLARE_APP_KEY` |

The key is server-side only, and has to be: the daily and weekly report crons run
with **no browser attached**. A key held in the browser would break them permanently.

The Worker authenticates *before* checking origin, so a server-side call with no
`Origin` header is allowed on a valid key, while browser calls stay origin-locked.

---

## Errors

`AiError` carries a message written for the user; anything else stays generic so
internals are never echoed to the client.

| Condition | Message |
|---|---|
| 401 | Invalid or missing AI access key. |
| 403 | This request is not allowed by the Cloudflare Worker. |
| 429 | Free Cloudflare AI quota or rate limit has been reached. Try again later. |
| 5xx | The AI service is temporarily unavailable. |
| network | Could not connect to the AI service. |
| timeout (30s) | The AI request timed out. Please try again. |
| bad JSON body | Received an invalid response from the AI service. |
| `response` empty | The AI service returned an empty response. |

A missing key throws before any request is made.

---

## Every AI feature today

One call site in each of 14 routes.

| Route | Call | What it produces |
|---|---|---|
| `ai/chat` | `aiChat` | the coach, answering from 30 days of real data |
| `ai/evening-verdict` | `aiText('verdict')` | end-of-day verdict |
| `ai/tribunal` | `aiText('verdict')` | weekly tribunal |
| `ai/pull-narrator` | `aiText('verdict')` | dream-trajectory narration |
| `ai/future-self` | `aiText('chat')` | reply from your future self |
| `ai/goal-plan` | `aiText('chat')` | plan for one goal |
| `ai/goal-starter` | `aiStructured` | starter pack for a new goal |
| `ai/goal-alignment` | `aiStructured` | how goals line up with the dream |
| `ai/habit-starter` | `aiStructured` | suggested habits |
| `ai/challenge-starter` | `aiStructured` | suggested challenge tactics |
| `ai/health-recommend` | `aiStructured` | health plan, goals and habits |
| `ai/plan` | `aiStructured` | turns "work 12 hours a day for 30 days" into a proposed task, habit or challenge — **proposes only**; the user confirms, and creation goes through the normal routes |
| `cron/daily-report` | `aiText('verdict')` | nightly verdict, delivered by push |
| `screentime/analyze` | `aiText('verdict')` | verdict on the screen-time numbers you entered |

---

## Where we can improve

Ranked by value against effort. Each is grounded in something actually in the code
today, not a wish list.

### ~~1. `health-recommend` is blind to the data we now collect~~ — DONE (AI-02)

It read `usual_sleep_time` / `usual_wake_time`, profile columns whose setup UI was
removed when per-day sleep sessions landed, so for anyone who signed up afterwards
the prompt read `Sleep schedule: ?–? (? hours)`.

It now builds a [health digest](../web-legacy/src/lib/health-digest.ts) from the
last 14 days of `health_logs` — real sleep sessions with nap and overlap handling,
meals grouped by food category, water, steps, exercise and weight. Every metric
carries its own observed count and eligible denominator, absent stays `null`, and
a `limitations` list states what is unknown so the model cannot fill the gaps.
Verified live: the model now opens with "recorded sleep on 3 of 14 nights with an
average of 6h 5m" and closes with "the remaining days are unknown, not zero".

### ~~2. Nothing analyses meals or nutrition~~ — DONE (AI-02)

The digest counts meals actually eaten (an empty meal slot is not a meal) and
groups foods by the [menu categories](../web-legacy/src/lib/food.ts), so the model
reasons over `fastfood 2, rice 1` rather than food names. Hand-typed foods count
as `unknownFoods` instead of being guessed at.

All three consumers now share it: `health-recommend`, the coach's context, and
the printable report, which gained a "What you ate" table and states sleep as
`7h 30m over 2 of 7 nights recorded` rather than a bare average. One definition,
so the three surfaces cannot disagree about the same week.

### ~~3. `aiJSON` gives up after one bad reply~~ — DONE (AI-01)

`aiStructured` validates at runtime and makes one corrective retry. See
[Structured output](#structured-output). All five structured routes are migrated and `aiJSON` has been removed.


### 4. The coach re-sends a 30-day data dump every message
**Partly improved**

The health slice is now the digest rather than raw rows, so it is both smaller
and coverage-aware. The rest — habits, prayers, tasks, goals, challenges, dream,
screen time — is still serialised in full on every turn.
**Medium value · medium effort**

[`ai/chat`](../web-legacy/src/app/api/ai/chat/route.ts) serialises habits, prayers,
tasks, health, goals, challenges, dream and screen time into the prompt on *every*
turn. Observed prompt sizes are modest today, but this grows with usage and is
charged every message. Options: summarise to a compact digest, send the full dump
only on the first turn, or select context based on what was asked.

### 5. `reasoning_effort` is never set
**Medium value · small effort · needs testing**

Workers AI may accept a reasoning-effort setting for gpt-oss. The Worker doesn't
forward one, so every call reasons at the default depth — which is why budgets need
1500 tokens. If `low` is supported, it would cut latency and token use across all
14 call sites. Requires a Worker change plus a measurement pass.

### 6. No fallback provider
**Medium value · medium effort**

The old code had Gemini→Groq fallback; this has none by design. If the Worker or
Workers AI is down, every AI feature fails. `lib/ai.ts` is the right seam to add a
second provider behind — the four functions wouldn't change.

### 7. The `bulk` task is defined but unused
**Low value · trivial effort**

`AiTask` includes `'bulk'` (1200 tokens) and nothing calls it. The two cron reports
use `'verdict'` (1500). Either point the crons at `bulk` to trim scheduled spend, or
delete the branch.

### 8. Screen time still has to be typed in
**Low value · large effort**

Reading the numbers off a screenshot would remove the only manual data entry left
in the app, but it needs a vision model — see [Why there is no vision](#why-there-is-no-vision)
for what was tried. Revisit only if a stronger vision model becomes available on
Workers AI, and measure accuracy against real screenshots before trusting it.

### 9. No caching or streaming
**Low value · medium effort**

Some results are already persisted (`users.ai_health_recommendation`), but verdicts
and plans regenerate on demand. Caching per-day results would cut quota use.
Separately, the coach reply arrives in one block — the Worker doesn't stream, so
streaming means a Worker change too. Don't fake it client-side.

---

## Verifying a change

There is no test suite. After touching anything in the AI path:

```bash
cd web-legacy
npx tsc --noEmit     # must be clean
npm run build        # must compile
```

Then exercise the real Worker with a **realistic** prompt, not a trivial one — see
the token-budget table above for why. Never print or commit the key;
`.env.local` is gitignored.
