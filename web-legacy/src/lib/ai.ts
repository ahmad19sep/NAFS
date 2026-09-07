// Single AI entry point for the whole app.
//
// Every AI feature goes through the functions below. Chat, verdicts, bulk
// and structured calls all resolve to one provider: the Cloudflare Worker
// running @cf/openai/gpt-oss-20b. Gemini and Groq were removed — there is no
// general provider routing, so a failure here is a real failure and surfaces
// as an AiError with a user-facing message.
//
// The one deliberate exception is aiDeep: a small number of call sites that
// do real analysis over a month of records use Claude when ANTHROPIC_API_KEY
// is set, and the Worker when it is not. Nothing else may route there.
//
// Keep this file as the only thing routes import; the transports live in
// ./cloudflare-ai and ./anthropic-ai.

import {
  cloudflareChat, cloudflareText, AiError,
  type ChatMessage, type AiOptions,
} from './cloudflare-ai'
import { anthropicChat, hasAnthropicAi, ANTHROPIC_MODEL } from './anthropic-ai'
import { CLOUDFLARE_AI_MODEL } from './cloudflare-ai'
import { parseJson, validate, type Schema } from './schema'

export type { Schema } from './schema'

export { AiError, safeParseJSON, hasCloudflareAi } from './cloudflare-ai'
export type { ChatMessage, AiOptions } from './cloudflare-ai'

/**
 * Kept so existing call sites read the same. It no longer picks a provider —
 * it only tunes how much output the task needs.
 */
export type AiTask = 'chat' | 'verdict' | 'bulk' | 'json' | 'deep'

/**
 * gpt-oss-20b is a reasoning model: it spends completion tokens thinking before
 * it emits the message, and the Worker only ever sees the message. If the
 * budget runs out mid-reasoning the reply comes back empty — not truncated,
 * *empty*. Measured against the live Worker on a realistic coaching prompt:
 *
 *   max_tokens 300  → 300 used, no message
 *   max_tokens 500  → 500 used, no message
 *   max_tokens 800  → 462 used, message returned
 *   max_tokens 1200 → 180 used, message returned
 *
 * Reasoning length swings a lot between identical calls, so these carry real
 * headroom rather than sitting near the observed minimum. They are ceilings,
 * not spend — billing follows tokens actually used, so generous is cheap.
 * Do not lower these without re-measuring.
 */
const MAX_TOKENS_FOR: Record<AiTask, number> = {
  chat: 1500,
  verdict: 1500,
  bulk: 1200,
  json: 2000,
  // A 250–400 word review. On Claude this is spend, not just a ceiling, so
  // it is sized to the answer rather than to reasoning headroom.
  deep: 2000,
}

/** Single-prompt text generation. */
export async function aiText(task: AiTask, prompt: string, system?: string): Promise<string> {
  return cloudflareText(prompt, system, { maxTokens: MAX_TOKENS_FOR[task] })
}

/** Multi-turn chat. History is trimmed to the recent turns before sending. */
export async function aiChat(messages: ChatMessage[], opts: AiOptions = {}): Promise<string> {
  return cloudflareChat(messages, { maxTokens: MAX_TOKENS_FOR.chat, ...opts })
}

export type DeepProvider = 'anthropic' | 'cloudflare'

/** Which model aiDeep will use right now. For the UI to say so honestly. */
export function deepProvider(): DeepProvider {
  return hasAnthropicAi() ? 'anthropic' : 'cloudflare'
}

/**
 * The deep path: analysis over a month of records, where the model's quality
 * is the point. Claude when ANTHROPIC_API_KEY is set; the Worker otherwise.
 *
 * If Claude is configured but fails on the day — overloaded, rate-limited,
 * unreachable — the call falls back to the Worker rather than failing, and
 * says which model actually answered. A rejected key (401) is not hidden
 * that way: it is a configuration problem the owner needs to see.
 *
 * Paid per call. Only routes that do a real month-long analysis may use
 * this; anything a chat turn can do stays on aiChat.
 */
export async function aiDeep(
  messages: ChatMessage[],
  opts: AiOptions = {},
): Promise<{ text: string; provider: DeepProvider; model: string; fellBack: boolean }> {
  const o = { maxTokens: MAX_TOKENS_FOR.deep, ...opts }
  if (hasAnthropicAi()) {
    try {
      return { text: await anthropicChat(messages, o), provider: 'anthropic', model: ANTHROPIC_MODEL, fellBack: false }
    } catch (err) {
      if (err instanceof AiError && err.status === 401) throw err
      // Fall through to the Worker.
    }
    return { text: await cloudflareChat(messages, o), provider: 'cloudflare', model: CLOUDFLARE_AI_MODEL, fellBack: true }
  }
  return { text: await cloudflareChat(messages, o), provider: 'cloudflare', model: CLOUDFLARE_AI_MODEL, fellBack: false }
}

// aiJSON was removed once every route moved to aiStructured. It returned null
// for every failure — bad JSON, wrong fields, a dead provider — so callers
// could not tell a model problem from an outage and a null became a silently
// broken feature. Use aiStructured; it validates and says what went wrong.

function jsonSystemPrompt(system?: string): string {
  return [
    system,
    'Reply with a single valid JSON value and nothing else. No markdown fences, no commentary before or after.',
  ].filter(Boolean).join('\n\n')
}

/** Why a structured request failed, so callers can react rather than guess. */
export type AiFailureCode =
  | 'not_configured'   // no key — never retried
  | 'unauthorized'     // key rejected — never retried
  | 'rate_limited'
  | 'unavailable'      // provider 5xx, network, timeout
  | 'invalid_json'     // survived retry and still would not parse
  | 'schema_mismatch'  // parsed, but the shape was wrong after retry

export type AiResult<T> =
  | { ok: true; data: T; attempts: number }
  | { ok: false; code: AiFailureCode; message: string; attempts: number }

/** Auth and configuration problems will not fix themselves on a second try. */
function isRetryable(code: AiFailureCode): boolean {
  return code !== 'not_configured' && code !== 'unauthorized'
}

/**
 * How long aiStructured may spend in total, across both attempts.
 *
 * A structured call can legitimately take half a minute — measured live, an
 * "overcome" plan took 36.6s, because the model thinks longer when the
 * context is rich. But the route that awaits it has its own ceiling
 * (`maxDuration = 60` on Vercel), and a request killed by the platform
 * returns no error the user can read. So the budget is set below that, and
 * the retry is skipped when there is not enough time left to finish it —
 * better one honest failure than two truncated ones.
 */
export const STRUCTURED_TOTAL_BUDGET_MS = 50_000
const ATTEMPT_TIMEOUT_MS = 45_000
const MIN_RETRY_BUDGET_MS = 10_000
const MIN_ATTEMPT_MS = 5_000

/**
 * The time budget for the next attempt, or null when starting one would only
 * guarantee a timeout.
 */
export function nextAttemptBudget(elapsedMs: number, attempt: number): number | null {
  const remaining = STRUCTURED_TOTAL_BUDGET_MS - elapsedMs
  if (attempt > 1 && remaining < MIN_RETRY_BUDGET_MS) return null
  return Math.max(MIN_ATTEMPT_MS, Math.min(ATTEMPT_TIMEOUT_MS, remaining))
}

function classify(err: unknown): { code: AiFailureCode; message: string } {
  if (err instanceof AiError) {
    if (err.status === 401) return { code: 'unauthorized', message: err.message }
    if (err.status === 429) return { code: 'rate_limited', message: err.message }
    if (err.message.includes('not configured')) return { code: 'not_configured', message: err.message }
    return { code: 'unavailable', message: err.message }
  }
  return { code: 'unavailable', message: 'The AI service is temporarily unavailable.' }
}

/**
 * Structured output that is actually checked before it reaches a caller.
 *
 * The pipeline is: call → reject empty → parse (one fence stripped, no brace
 * guessing) → runtime schema check → return validated data or an explicit
 * failure. On a parse or schema failure it makes exactly ONE corrective
 * retry, telling the model what was wrong without echoing its invalid output
 * back as instructions. Transport failures are not retried here — the Worker
 * and the caller already have their own handling, and stacking retries at
 * three layers turns one slow request into nine.
 *
 * Raw invalid output never reaches the returned message, only the validation
 * errors, so model text cannot leak into a client-facing string.
 */
export async function aiStructured<T>(
  prompt: string,
  schema: Schema,
  system?: string,
): Promise<AiResult<T>> {
  const MAX_ATTEMPTS = 2
  const startedAt = Date.now()
  let correction = ''
  let last: { code: AiFailureCode; message: string } = {
    code: 'unavailable',
    message: 'The AI service is temporarily unavailable.',
  }

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    // Stop rather than start a retry that cannot finish before the route is
    // killed; the failure already in `last` is the honest answer.
    const budget = nextAttemptBudget(Date.now() - startedAt, attempt)
    if (budget === null) break

    let raw: string
    try {
      raw = await cloudflareText(
        correction ? `${prompt}\n\n${correction}` : prompt,
        jsonSystemPrompt(system),
        { maxTokens: MAX_TOKENS_FOR.json, temperature: 0.2, timeoutMs: budget },
      )
    } catch (err) {
      const failure = classify(err)
      // A reasoning model that runs out of budget mid-thought returns nothing
      // at all, and its thinking length varies run to run — so one more try
      // often fits where the first did not. Anything else (a dead or
      // refusing provider) will not be fixed by asking again.
      if (err instanceof AiError && /empty response/i.test(err.message) && attempt < MAX_ATTEMPTS) {
        last = { code: 'unavailable', message: failure.message }
        continue
      }
      return { ok: false, ...failure, attempts: attempt }
    }

    const parsed = parseJson(raw)
    if (!parsed.ok) {
      last = { code: 'invalid_json', message: 'The AI service returned malformed output.' }
      correction = 'Your previous reply was not valid JSON. Reply with only the JSON value.'
      continue
    }

    const checked = validate<T>(parsed.value, schema)
    if (checked.ok) return { ok: true, data: checked.value, attempts: attempt }

    last = { code: 'schema_mismatch', message: 'The AI service returned unexpected data.' }
    correction =
      'Your previous reply had the wrong shape. Fix these problems and reply with only the JSON value:\n' +
      checked.errors.map((e) => `- ${e}`).join('\n')
  }

  return { ok: false, ...last, attempts: MAX_ATTEMPTS }
}

export { isRetryable }

// There is no image input. gpt-oss-20b is text-only, and the vision models
// available on Workers AI were tried and rejected — see docs/AI.md. Features
// that would want an image ask the user for the numbers instead.
