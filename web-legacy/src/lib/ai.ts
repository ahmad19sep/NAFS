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
  cloudflareChat, cloudflareText, cloudflareVision, safeParseJSON,
  AiError, type ChatMessage, type AiOptions,
} from './cloudflare-ai'

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

/**
 * JSON-output generation. gpt-oss-20b has no strict JSON mode, so the schema
 * instruction is reinforced in the system prompt and the reply is parsed
 * permissively. Returns null when the model doesn't produce usable JSON.
 */
export async function aiJSON<T = unknown>(prompt: string, system?: string): Promise<T | null> {
  const jsonSystem = [
    system,
    'Reply with a single valid JSON value and nothing else. No markdown fences, no commentary before or after.',
  ].filter(Boolean).join('\n\n')

  const raw = await cloudflareText(prompt, jsonSystem, {
    maxTokens: MAX_TOKENS_FOR.json,
    // Lower than the default so structured output stays on-format.
    temperature: 0.2,
  })
  return safeParseJSON<T>(raw)
}

/**
 * Image reading. gpt-oss-20b is text-only, so this goes to a separate vision
 * route on the same Worker. That route is optional — when it isn't deployed the
 * Worker answers 404 and this throws, so every caller must offer a manual path
 * rather than treating vision as guaranteed.
 */
export async function aiVision(base64: string, prompt: string): Promise<string> {
  return cloudflareVision(base64, prompt)
}
