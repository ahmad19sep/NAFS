# AI in NAFS

Everything the app generates — coach replies, verdicts, goal plans, starter packs,
scheduled reports — goes through one model behind one façade, with one deliberate
exception: the growth review, which uses Claude when a key is set. See
[The deep path](#the-deep-path).

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
[`src/lib/cloudflare-ai.ts`](../web-legacy/src/lib/cloudflare-ai.ts) and
[`src/lib/anthropic-ai.ts`](../web-legacy/src/lib/anthropic-ai.ts) are the only files
that touch the network or a key.

| Function | Use for | Returns |
|---|---|---|
| `aiText(task, prompt, system?)` | one-shot generation | `string` |
| `aiChat(messages, opts?)` | multi-turn conversation | `string` |
| `aiStructured<T>(prompt, schema, system?)` | structured output | `AiResult<T>` |
| `aiDeep(messages, opts?)` | month-long analysis only — today, the growth review | `{ text, provider, model, fellBack }` |

There is deliberately **no image input** — see [Why there is no vision](#why-there-is-no-vision).

### The deep path

`aiDeep` is the one place a second model is allowed. It exists for analysis over a
month of records, where a stronger model is the point of the feature; today that is
only the growth review. When `ANTHROPIC_API_KEY` is set it calls Claude
(`ANTHROPIC_MODEL`, default `claude-sonnet-5`); when it is not, the Worker. If Claude
is configured but fails on the day — 429, 5xx, 529, network — the call falls back to
the Worker and reports `fellBack: true`, and the card says so. A rejected key (401) is
not hidden that way: it surfaces as an error the owner needs to see.

It is paid per call, so it runs on demand and once a day per user, never on a cron.
Nothing a chat turn can do may route here. The key is read only in `anthropic-ai.ts`,
sent only as `x-api-key`, and never logged.

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

> **The Worker has its own ceiling, and it wins.** The Worker clamps
> `max_tokens` with `Math.min(requested, CEILING)`. That ceiling was **1000**
> and is now **2000**, matching the `json` budget below. Verified live on
> 2026-09-07: before the change, requests for 2000 and 3000 both came back
> `completion_tokens: 1000` with an empty reply; after it, a long generation
> ran to 1877 tokens and returned real text. A reasoning model that hits the
> ceiling mid-thought returns **nothing**, not a shorter answer — which is why
> `aiStructured` makes one extra attempt on an empty reply, and why lowering
> the ceiling again would silently break structured output.

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
| `ANTHROPIC_API_KEY` | **yes** | Vercel env vars, `.env.local` | enables the deep path (growth review); absent means the Worker answers |
| `ANTHROPIC_MODEL` | no | optional override | defaults to `claude-sonnet-5` |
| `ANTHROPIC_WORKSPACE_ID` | no | optional | **required when the API key is not itself workspace-scoped** — without it Anthropic rejects every call with a 400 and the deep path falls back silently |

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
| timeout (30s default, 45s per structured attempt) | The AI request timed out. Please try again. |
| bad JSON body | Received an invalid response from the AI service. |
| `response` empty | The AI service returned an empty response. |

A missing key throws before any request is made.

### How long a call may take

A chat turn is quick; a structured plan is not. Measured live, an "overcome"
plan took **36.6s** end to end, because the model reasons for longer when the
context is rich — the old flat 30s timeout would have killed a correct answer.

So `AiOptions.timeoutMs` overrides the 30s default per call, and `aiStructured`
runs on a **50s total budget** across both attempts: 45s for one attempt, and
the retry is skipped when less than 10s remains (`nextAttemptBudget`). The AI
routes carry `maxDuration = 60`, so the budget always expires before the
platform kills the request — one readable failure instead of a dead connection.

---

## Every AI feature today

One call site in each of 15 routes, plus one route that stores what the user tells
the coach and calls no model.

| Route | Call | What it produces |
|---|---|---|
| `ai/chat` | `aiChat` | the coach, answering from 30 days of real data, what keeps not happening and what was different on those days, and what the user told it before — same context for the Coach page and the floating bubble |
| `ai/growth-review` | `aiDeep` | improving, lacking, the pattern underneath, three rules for the week, one question — on demand, once a day, this week and this month against last |
| `ai/plan-from-chat` | `aiStructured({ deep: true })` | reads a whole coach conversation and proposes the tasks, habits and challenges that answer it — **proposes only**; the user ticks and confirms, and each step is created through the normal route |
| `ai/report-review` | `aiDeep` | the written read printed on the weekly/monthly report: in plain words, what improved, where to improve, the pattern, three things to do, one question. Stored per (period, start date), so a report printed weeks later carries the same words |
| `coach/notes` | — | saves a reason for a miss, a bad-day note, or a life answer, in the user's words |
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
| `ai/plan` | `aiStructured` | mode `plan`: turns "work 12 hours a day for 30 days" into a proposed task, habit or challenge. Mode `overcome`: turns "I've been scrolling 3 hours a night" into a small way out — a first move for today, a habit to fill the gap, maybe a short challenge — grounded in existing habits, repeated misses, measured causes, screen time, sleep and coach memory. Both **propose only**; the user ticks what to add, and each step is created through the normal route with its own request id |
| `cron/daily-report` | `aiText('verdict')` | nightly verdict, delivered by push |
| `screentime/analyze` | `aiText('verdict')` | verdict on the screen-time numbers you entered |

---

## The coach's memory

The coach reads three things nobody types into a prompt:

- **Repeated misses** ([`misses.ts`](../web-legacy/src/lib/misses.ts)) — what keeps
  not happening over the last seven days, with counts and the dates behind them.
  Unrecorded is never missed: a prayer counts only when recorded as missed, a habit
  only on a day something else was logged. Today is excluded as still in progress.
- **Possible causes** ([`correlates.ts`](../web-legacy/src/lib/correlates.ts)) — for
  each repeated miss, sleep and phone screen time on the days it was missed against
  the days it was done. Reported only with two or more days on each side and a gap of
  at least 45 min (sleep) or 30 min (screen); a day with no value is unknown, not
  zero. Worded as "recorded that day", not "the night before". Evidence for a
  question, never a verdict.
- **Coach notes** ([`coach-notes.ts`](../web-legacy/src/lib/coach-notes.ts), table
  `coach_notes`) — what the user told the coach, in their own words: a reason for a
  miss ("Why?" under the pattern on Home, which shows the last answer first the next
  time it repeats), what was going on on a bad day (asked once under the quote), and
  answers to four life questions on Profile. The latest per subject is what the
  coach quotes; every save is kept so a changed answer is visible. Nothing here is
  generated.

All three are built once in [`coach-context.ts`](../web-legacy/src/lib/coach-context.ts)
and shared by the chat, the floating bubble and the growth review, so the surfaces
cannot disagree about the facts. The prompt rules that go with them: quote reasons
back rather than paraphrase, name a repeated reason as a pattern, offer a measured
cause as a possibility only, and when there is neither, ask one question and say the
answer will be remembered. If what the user said they want does not match the
records, say so with the numbers and ask them to choose.

The app does not punish. The only consequence in it is one the user sets on
themselves — a sadqa pledge on a challenge. The pattern card names the evidence
and points at the coach.

---

## When the coach is talked to, and when it reports

`ai/chat` used to rebuild the user's turn as `USER DATA: {5000 tokens of JSON}` followed by `USER QUESTION: <one line>`, and `ASK_ASCEND_SYSTEM` opened with "answer using THEIR data" and "always cite at least one specific number". Both were wrong in the same direction. Someone who wrote "I have fallen into this habit, help me" had their words buried at the bottom of a wall of statistics and got a reading of their consistency back. The model was obeying.

Now the records go in as their own **system** turn, labelled background, and the user's message is left exactly as written so it is the last thing the model reads. The prompt's first rule is to answer what was actually said, and it branches on the kind of message: a situation gets a conversation, a question about the data gets numbers, anything else gets a normal reply.

The conversation is where a plan starts, not where it ends. **Build a plan from this** under both chat surfaces sends the transcript to `ai/plan-from-chat`, which reads what was said and proposes real steps through the shared `PlanSteps` component. Nothing is created until the user ticks and confirms.

---

## One JSON repair, and why only one

`parseJson` makes a single narrow repair before giving up: gpt-oss-20b intermittently wraps an object inside an array in quotes, so a list of steps arrives as `[{...}, "{...}]`. Observed twice in live testing on plans of three or more steps, with the content correct both times and only the punctuation wrong.

The repair is purely syntactic and never edits content, the result still has to parse, and if it does not the caller's corrective retry runs exactly as before. `repairStrayObjectQuotes` is covered by tests that pin what it must NOT touch: braces inside strings, escaped quotes, and ordinary string elements that follow a comma.

---

## Prose is an interpretation; tables are the record

The written surfaces — the growth review and the report review — are the only
places a model's words carry numbers, and a model can get a number wrong. Live
on the free 20b model, a review read "missed 5 of 7 days" back as "not done on
2 of 7". Claude handles it; the fallback does not always.

Three things follow from that, and none of them is "trust the prose":

1. **The prompts forbid arithmetic.** Numbers are to be copied exactly, never
   recomputed, rounded or reversed.
2. **The printed report states the hierarchy.** The read is introduced as *one
   reading* of the numbers, with the tables named as the record. The
   deterministic "Where to improve next" list sits below it with the true
   figures, computed by `buildReport` and covered by tests.
3. **Neither surface generates scripture.** Both prompts forbid quoting or
   citing Quran or hadith, including chapter-and-verse references — a review
   once closed with a generated ayah, and a misattributed line on a page
   someone prints and keeps would outlive any correction. Every scripture the
   app shows comes from the curated list in
   [`quotes.ts`](../web-legacy/src/lib/quotes.ts), and the printed report's
   footer carries a verified ayah of its own.

`TRIBUNAL_SYSTEM` still permits a generated scripture line. It predates this
rule and is unchanged here; it should get the same treatment.

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
**Partly addressed — deliberately narrow**

There is still no general fallback: if the Worker or Workers AI is down, chat,
verdicts and structured calls fail. What exists is the [deep path](#the-deep-path):
one call site, Claude when configured, the Worker otherwise, the Worker again if
Claude fails on the day. Widening that to every call is a cost decision, not a code
change — `aiDeep` already shows the shape, and the owner's rule is that the paid
model is for complex analysis only.

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
