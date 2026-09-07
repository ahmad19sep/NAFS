// Cloudflare Worker fronting Workers AI (@cf/openai/gpt-oss-20b).
//
//   Next.js route (server)  →  Worker /chat  →  Workers AI  →  gpt-oss-20b
//
// The Worker owns the Workers AI binding and normalises the reply to
// { response, usage, model }, so nothing here ever talks to Workers AI
// directly or sees Cloudflare's raw choices[] shape.
//
// The key is a server-side secret (CLOUDFLARE_APP_KEY). It is read only in
// this file, sent only as an Authorization header, and never logged — the
// error paths below deliberately report status codes, never the request.

export const CLOUDFLARE_AI_MODEL = '@cf/openai/gpt-oss-20b'

/** Not a secret. Override per-environment if the Worker route ever moves. */
export const CLOUDFLARE_AI_URL =
  process.env.CLOUDFLARE_AI_URL || 'https://my-gpt-api.ahmadwork665.workers.dev/chat'

/**
 * Optional second route on the same Worker, running a vision model
 * (gpt-oss-20b is text-only). Absent until that route is deployed, in which
 * case the Worker answers 404 and callers fall back to manual entry.
 */
export const CLOUDFLARE_VISION_URL =
  process.env.CLOUDFLARE_VISION_URL || CLOUDFLARE_AI_URL.replace(/\/chat$/, '/vision')

/** Vision needs a bigger budget: the image eats context before reasoning starts. */
const VISION_MAX_TOKENS = 2000
const VISION_TIMEOUT_MS = 60_000

// gpt-oss-20b burns completion tokens on hidden reasoning before it produces a
// message, so a small budget yields an empty reply rather than a short one.
// 1500 leaves room for both; see MAX_TOKENS_FOR in ./ai for the measurements.
export const DEFAULT_MAX_TOKENS = 1500
export const DEFAULT_TEMPERATURE = 0.6

const TIMEOUT_MS = 30_000
/** Free-tier allowance is small, so history is capped before it's sent. */
const MAX_HISTORY_MESSAGES = 20

export type ChatRole = 'system' | 'user' | 'assistant'
export interface ChatMessage {
  role: ChatRole
  content: string
}

export interface AiOptions {
  maxTokens?: number
  temperature?: number
  /** Caller-supplied cancellation, combined with the built-in timeout. */
  signal?: AbortSignal
}

/** Carries a user-facing message; `status` is the Worker's HTTP status if any. */
export class AiError extends Error {
  readonly status: number | null
  constructor(message: string, status: number | null = null) {
    super(message)
    this.name = 'AiError'
    this.status = status
  }
}

function getKey(): string | null {
  return process.env.CLOUDFLARE_APP_KEY || null
}

export function hasCloudflareAi(): boolean {
  return !!getKey()
}

function messageForStatus(status: number): string {
  if (status === 401) return 'Invalid or missing AI access key.'
  if (status === 403) return 'This request is not allowed by the Cloudflare Worker.'
  if (status === 429) return 'Free Cloudflare AI quota or rate limit has been reached. Try again later.'
  if (status >= 500) return 'The AI service is temporarily unavailable.'
  return `The AI service rejected the request (${status}).`
}

/**
 * Keeps the leading system prompt and the most recent turns, so a long
 * conversation doesn't burn the free allowance.
 */
export function trimHistory(messages: ChatMessage[], max = MAX_HISTORY_MESSAGES): ChatMessage[] {
  const system = messages.filter((m) => m.role === 'system')
  const turns = messages.filter((m) => m.role !== 'system')
  return [...system, ...turns.slice(-max)]
}

/** Multi-turn chat against the Worker. Returns the assistant's text. */
export async function cloudflareChat(
  messages: ChatMessage[],
  opts: AiOptions = {},
): Promise<string> {
  const key = getKey()
  if (!key) throw new AiError('Cloudflare AI key is not configured. Set CLOUDFLARE_APP_KEY.')

  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(), TIMEOUT_MS)
  const onCallerAbort = () => timeout.abort()
  opts.signal?.addEventListener('abort', onCallerAbort)

  let res: Response
  try {
    res = await fetch(CLOUDFLARE_AI_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify({
        messages: trimHistory(messages),
        max_tokens: opts.maxTokens ?? DEFAULT_MAX_TOKENS,
        temperature: opts.temperature ?? DEFAULT_TEMPERATURE,
      }),
      signal: timeout.signal,
    })
  } catch (err) {
    if (opts.signal?.aborted) throw new AiError('The AI request was cancelled.')
    if (timeout.signal.aborted) throw new AiError('The AI request timed out. Please try again.')
    throw new AiError('Could not connect to the AI service.')
  } finally {
    clearTimeout(timer)
    opts.signal?.removeEventListener('abort', onCallerAbort)
  }

  if (!res.ok) throw new AiError(messageForStatus(res.status), res.status)

  let data: { response?: unknown }
  try {
    data = await res.json()
  } catch {
    throw new AiError('Received an invalid response from the AI service.')
  }

  const text = typeof data.response === 'string' ? data.response.trim() : ''
  if (!text) throw new AiError('The AI service returned an empty response.')
  return text
}

/** Single-prompt generation with an optional system instruction. */
export async function cloudflareText(
  prompt: string,
  system?: string,
  opts: AiOptions = {},
): Promise<string> {
  const messages: ChatMessage[] = []
  if (system) messages.push({ role: 'system', content: system })
  messages.push({ role: 'user', content: prompt })
  return cloudflareChat(messages, opts)
}

/**
 * Send an image to the Worker's vision route. Throws AiError when that route
 * isn't deployed (404), so callers can fall back to asking the user directly.
 */
export async function cloudflareVision(
  base64: string,
  prompt: string,
  opts: AiOptions = {},
): Promise<string> {
  const key = getKey()
  if (!key) throw new AiError('Cloudflare AI key is not configured. Set CLOUDFLARE_APP_KEY.')

  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(), VISION_TIMEOUT_MS)
  const onCallerAbort = () => timeout.abort()
  opts.signal?.addEventListener('abort', onCallerAbort)

  let res: Response
  try {
    res = await fetch(CLOUDFLARE_VISION_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify({
        image: base64,
        prompt,
        max_tokens: opts.maxTokens ?? VISION_MAX_TOKENS,
      }),
      signal: timeout.signal,
    })
  } catch {
    if (opts.signal?.aborted) throw new AiError('The AI request was cancelled.')
    if (timeout.signal.aborted) throw new AiError('The image request timed out. Please try again.')
    throw new AiError('Could not connect to the AI service.')
  } finally {
    clearTimeout(timer)
    opts.signal?.removeEventListener('abort', onCallerAbort)
  }

  if (res.status === 404) {
    throw new AiError('Image reading is not available on this deployment.', 404)
  }
  if (!res.ok) throw new AiError(messageForStatus(res.status), res.status)

  let data: { response?: unknown }
  try {
    data = await res.json()
  } catch {
    throw new AiError('Received an invalid response from the AI service.')
  }

  const text = typeof data.response === 'string' ? data.response.trim() : ''
  if (!text) throw new AiError('The AI service could not read the image.')
  return text
}

/**
 * Parse a model reply as JSON. Strips markdown fences and surrounding prose,
 * since an open model won't always honour a "JSON only" instruction.
 * Returns null when the reply isn't usable JSON.
 */
export function safeParseJSON<T = unknown>(raw: string): T | null {
  if (!raw) return null
  let s = raw.trim()
  s = s.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '').trim()
  const firstBrace = s.indexOf('{')
  const lastBrace = s.lastIndexOf('}')
  const firstBracket = s.indexOf('[')
  const lastBracket = s.lastIndexOf(']')
  // Prefer whichever structure starts first, so top-level arrays parse too.
  if (firstBracket !== -1 && (firstBrace === -1 || firstBracket < firstBrace)) {
    if (lastBracket > firstBracket) s = s.slice(firstBracket, lastBracket + 1)
  } else if (firstBrace !== -1 && lastBrace > firstBrace) {
    s = s.slice(firstBrace, lastBrace + 1)
  }
  try {
    return JSON.parse(s) as T
  } catch {
    return null
  }
}
