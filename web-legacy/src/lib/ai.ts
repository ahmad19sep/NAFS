// Single AI entry point for the whole app.
//
// Every AI feature goes through the four functions below, and they all resolve
// to one provider: the Cloudflare Worker running @cf/openai/gpt-oss-20b.
// Gemini and Groq were removed — there is no provider routing or fallback left,
// so a failure here is a real failure and surfaces as an AiError with a
// user-facing message.
//
// Keep this file as the only thing routes import; the transport lives in
// ./cloudflare-ai.

import {
  cloudflareChat, cloudflareText, AiError,
  type ChatMessage, type AiOptions,
} from './cloudflare-ai'
import { parseJson, validate, type Schema } from './schema'

export type { Schema } from './schema'

export { AiError, safeParseJSON, hasCloudflareAi } from './cloudflare-ai'
export type { ChatMessage, AiOptions } from './cloudflare-ai'

/**
 * Kept so existing call sites read the same. It no longer picks a provider —
 * it only tunes how much output the task needs.
 */
export type AiTask = 'chat' | 'verdict' | 'bulk' | 'json'

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
}

/** Single-prompt text generation. */
export async function aiText(task: AiTask, prompt: string, system?: string): Promise<string> {
  return cloudflareText(prompt, system, { maxTokens: MAX_TOKENS_FOR[task] })
}

/** Multi-turn chat. History is trimmed to the recent turns before sending. */
export async function aiChat(messages: ChatMessage[], opts: AiOptions = {}): Promise<string> {
  return cloudflareChat(messages, { maxTokens: MAX_TOKENS_FOR.chat, ...opts })
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
  let correction = ''
  let last: { code: AiFailureCode; message: string } = {
    code: 'unavailable',
    message: 'The AI service is temporarily unavailable.',
  }

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    let raw: string
    try {
      raw = await cloudflareText(
        correction ? `${prompt}\n\n${correction}` : prompt,
        jsonSystemPrompt(system),
        { maxTokens: MAX_TOKENS_FOR.json, temperature: 0.2 },
      )
    } catch (err) {
      const failure = classify(err)
      // A dead or refusing provider will not be fixed by asking again.
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
