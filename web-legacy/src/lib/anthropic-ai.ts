// Anthropic Messages API — the deep path.
//
//   Next.js route (server)  →  api.anthropic.com/v1/messages  →  Claude
//
// Used by exactly the call sites in ./ai that opt into it (aiDeep): analysis
// over a month of records, where a stronger model is the point of the
// feature. Everything else stays on the free Worker. See docs/AI.md.
//
// The key is a server-side secret (ANTHROPIC_API_KEY). It is read only in
// this file, sent only as the x-api-key header, and never logged — the error
// paths below deliberately report status codes, never the request.

import { AiError, type ChatMessage, type AiOptions } from './cloudflare-ai'

/** Not a secret. Override per environment to pin or move models. */
export const ANTHROPIC_MODEL = process.env.ANTHROPIC_MODEL || 'claude-sonnet-5'

const ANTHROPIC_URL = 'https://api.anthropic.com/v1/messages'
const ANTHROPIC_VERSION = '2023-06-01'

/**
 * Only needed for an API key that is not itself scoped to a workspace. Such a
 * key is rejected with a 400 until the workspace is named, which reads like a
 * malformed request rather than the configuration problem it is — so the
 * message for that status says exactly this.
 */
function getWorkspaceId(): string | null {
  return process.env.ANTHROPIC_WORKSPACE_ID || null
}
/** A month of data and a 400-word answer: slower than a chat turn. */
const TIMEOUT_MS = 60_000
const DEFAULT_MAX_TOKENS = 2000

function getKey(): string | null {
  return process.env.ANTHROPIC_API_KEY || null
}

export function hasAnthropicAi(): boolean {
  return !!getKey()
}

function messageForStatus(status: number): string {
  if (status === 400) {
    return 'Anthropic rejected the request (400). The usual cause is an API key that is not '
      + 'scoped to a workspace: either create a workspace-scoped key, or set ANTHROPIC_WORKSPACE_ID.'
  }
  if (status === 401) return 'The Anthropic API key was rejected.'
  if (status === 429) return 'The Anthropic rate limit has been reached. Try again in a minute.'
  if (status === 529 || status >= 500) return 'Anthropic is temporarily overloaded.'
  return `Anthropic rejected the request (${status}).`
}

/**
 * Anthropic takes the system prompt apart from the turns, and the turns must
 * alternate user/assistant starting with user. Consecutive same-role messages
 * are joined; a leading assistant turn is dropped.
 */
export function toAnthropicShape(messages: ChatMessage[]): {
  system: string | undefined
  messages: { role: 'user' | 'assistant'; content: string }[]
} {
  const system = messages
    .filter((m) => m.role === 'system')
    .map((m) => m.content)
    .join('\n\n') || undefined

  const turns: { role: 'user' | 'assistant'; content: string }[] = []
  for (const m of messages) {
    if (m.role === 'system') continue
    const last = turns[turns.length - 1]
    if (last && last.role === m.role) last.content += `\n\n${m.content}`
    else turns.push({ role: m.role, content: m.content })
  }
  while (turns.length && turns[0].role === 'assistant') turns.shift()

  return { system, messages: turns }
}

/** Multi-turn chat against Claude. Returns the assistant's text. */
export async function anthropicChat(
  messages: ChatMessage[],
  opts: AiOptions = {},
): Promise<string> {
  const key = getKey()
  if (!key) throw new AiError('Anthropic is not configured. Set ANTHROPIC_API_KEY.')

  const shaped = toAnthropicShape(messages)
  if (shaped.messages.length === 0) throw new AiError('Nothing to send to the AI service.')

  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(), opts.timeoutMs ?? TIMEOUT_MS)
  const onCallerAbort = () => timeout.abort()
  opts.signal?.addEventListener('abort', onCallerAbort)

  let res: Response
  try {
    res = await fetch(ANTHROPIC_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': key,
        'anthropic-version': ANTHROPIC_VERSION,
        ...(getWorkspaceId() ? { 'anthropic-workspace-id': getWorkspaceId() as string } : {}),
      },
      // No `temperature`. Newer Claude models reject it outright — measured
      // live on claude-sonnet-5: `400 temperature is deprecated for this
      // model` — and since aiDeep falls back on any non-401 failure, sending
      // it meant every deep call quietly ran on the free Worker instead.
      // Omitting it lets the model use its own default, which is what the
      // deprecation intends and what older models did with our 0.5 anyway.
      body: JSON.stringify({
        model: ANTHROPIC_MODEL,
        max_tokens: opts.maxTokens ?? DEFAULT_MAX_TOKENS,
        ...(shaped.system ? { system: shaped.system } : {}),
        messages: shaped.messages,
      }),
      signal: timeout.signal,
    })
  } catch {
    if (opts.signal?.aborted) throw new AiError('The AI request was cancelled.')
    if (timeout.signal.aborted) throw new AiError('The AI request timed out. Please try again.')
    throw new AiError('Could not connect to Anthropic.')
  } finally {
    clearTimeout(timer)
    opts.signal?.removeEventListener('abort', onCallerAbort)
  }

  if (!res.ok) throw new AiError(messageForStatus(res.status), res.status)

  let data: { content?: { type?: string; text?: string }[] }
  try {
    data = await res.json()
  } catch {
    throw new AiError('Received an invalid response from Anthropic.')
  }

  const text = (data.content ?? [])
    .filter((b) => b.type === 'text' && typeof b.text === 'string')
    .map((b) => b.text as string)
    .join('')
    .trim()
  if (!text) throw new AiError('Anthropic returned an empty response.')
  return text
}
