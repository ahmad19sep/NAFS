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
| `aiJSON<T>(prompt, system?)` | structured output | `T \| null` |
| `aiVision(base64, prompt)` | reading an image | `string` — **throws** if the vision route isn't deployed |

`aiJSON` returns `null` rather than throwing when the reply isn't parseable JSON.
Callers must handle that — a `null` is a silent feature failure otherwise.

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

## Configuration

| Name | Secret? | Where | Purpose |
|---|---|---|---|
| `CLOUDFLARE_APP_KEY` | **yes** | Vercel env vars, `.env.local` | Bearer token the Worker checks |
| `CLOUDFLARE_AI_URL` | no | optional override | defaults to the deployed Worker `/chat` |
| `CLOUDFLARE_VISION_URL` | no | optional override | defaults to the same Worker's `/vision` |
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

14 call sites across 13 routes.

| Route | Call | What it produces |
|---|---|---|
| `ai/chat` | `aiChat` | the coach, answering from 30 days of real data |
| `ai/evening-verdict` | `aiText('verdict')` | end-of-day verdict |
| `ai/tribunal` | `aiText('verdict')` | weekly tribunal |
| `ai/pull-narrator` | `aiText('verdict')` | dream-trajectory narration |
| `ai/future-self` | `aiText('chat')` | reply from your future self |
| `ai/goal-plan` | `aiText('chat')` | plan for one goal |
| `ai/goal-starter` | `aiJSON` | starter pack for a new goal |
| `ai/goal-alignment` | `aiJSON` | how goals line up with the dream |
| `ai/habit-starter` | `aiJSON` | suggested habits |
| `ai/challenge-starter` | `aiJSON` | suggested challenge tactics |
| `ai/health-recommend` | `aiJSON` | health plan, goals and habits |
| `cron/daily-report` | `aiText('verdict')` | emailed daily summary |
| `cron/weekly-report` | `aiText('verdict')` | emailed weekly summary |
| `screentime/analyze` | `aiVision` + `aiText('verdict')` | reads a screen-time screenshot, or summarises manually entered numbers |

---

## Where we can improve

Ranked by value against effort. Each is grounded in something actually in the code
today, not a wish list.

### 1. `health-recommend` is blind to the data we now collect
**High value · small effort · has a live bug**

[`health-recommend`](../web-legacy/src/app/api/ai/health-recommend/route.ts) still
reads `usual_sleep_time` / `usual_wake_time` — columns whose setup UI was removed
when per-day sleep sessions landed. For any new user they are `NULL`, so the prompt
literally reads `Sleep schedule: ?–? (? hours)`.

Meanwhile the app now stores **real** sleep sessions (`health_logs.sleep_sessions`)
and **meals** (`health_logs.meals`) that no AI feature has ever seen. Feeding the
last 7–30 days of actual sleep totals, nap patterns, and what the user ate would
turn a generic BMI plan into a specific one. This is the single biggest gap.

### 2. Nothing analyses meals or nutrition
**High value · medium effort**

Meals are logged in detail — breakfast/lunch/dinner plus naps of eating, each with
named foods — and nothing reads them. Candidates: a nutrition verdict in the daily
report, "you ate 5 times, 3 were fast food" patterns, or meal suggestions in
`health-recommend`. The food catalogue in [`lib/food.ts`](../web-legacy/src/lib/food.ts)
already carries categories the model could reason over without any new data entry.

### 3. `aiJSON` gives up after one bad reply
**Medium value · small effort**

`aiJSON` parses once and returns `null` on failure. An open model without strict
JSON mode *will* occasionally wrap output in prose. One retry — same prompt, lower
temperature, "your last reply was not valid JSON" appended — would recover most of
those. Right now a single malformed reply silently kills a starter pack.

### 4. The coach re-sends a 30-day data dump every message
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

### 8. Screenshot reading accuracy is unproven
**Medium value · needs measurement**

`gpt-oss-20b` is text-only, so image reading goes to a **second, optional Worker
route** (`/vision`) running `@cf/meta/llama-3.2-11b-vision-instruct`. The app side
is wired: `aiVision(base64, prompt)` posts to `CLOUDFLARE_VISION_URL`, and when
that route isn't deployed the Worker answers 404, `aiVision` throws, and screentime
falls back to manual entry.

What is *not* established is whether an 11B vision model reads a Digital Wellbeing
or iOS Screen Time screenshot accurately — exact app names and durations are a hard
OCR-ish task, and a plausible-but-wrong number is worse than no number. Measure
against real screenshots before trusting it; `@cf/llava-hf/llava-1.5-7b-hf` is the
alternative. Manual entry stays a first-class path either way.

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
