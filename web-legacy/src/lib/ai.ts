// Smart AI router. Picks the right provider per task and falls back on failure.
//
// Routing:
//   chat / verdict → Groq Llama 3.3 70B (high RPM, fast)         → Gemini fallback
//   bulk           → Groq Llama 3.1 8B Instant (500K tokens/day) → Gemini fallback
//   json           → Gemini (best structured output / JSON mode) → Groq fallback (re-parsed)
//   vision         → Gemini (Groq has no vision)
//
// All paths automatically fall back if the primary fails (esp. 429 rate-limits).

import { generateText, generateJSON, geminiVision, chatGemini, safeParseJSON } from './gemini'
import { generateGroq, chatGroq, hasGroq } from './groq'

export type AiTask = 'chat' | 'verdict' | 'bulk' | 'json'
type Msg = { role: 'system' | 'user' | 'assistant'; content: string }

// ---------- core helper ----------
async function withFallback<T>(
  attempts: Array<{ name: string; fn: () => Promise<T> }>,
): Promise<T> {
  let lastErr: any
  for (const { name, fn } of attempts) {
    try {
      const out = await fn()
      console.log(`[ai] ${name} OK`)
      return out
    } catch (err: any) {
      console.warn(`[ai] ${name} failed: ${err?.status ?? ''} ${err?.message ?? err}`)
      lastErr = err
    }
  }
  throw lastErr ?? new Error('All providers failed')
}

// ---------- public API ----------

/** Single-prompt text generation. */
export async function aiText(task: AiTask, prompt: string, system?: string): Promise<string> {
  if (task === 'chat' || task === 'verdict') {
    return withFallback([
      ...(hasGroq() ? [{ name: 'groq-70b', fn: () => generateGroq(prompt, system, 'llama-3.3-70b-versatile') }] : []),
      { name: 'gemini-flash', fn: () => generateText(prompt, system) },
    ])
  }
  if (task === 'bulk') {
    return withFallback([
      ...(hasGroq() ? [{ name: 'groq-8b', fn: () => generateGroq(prompt, system, 'llama-3.1-8b-instant') }] : []),
      { name: 'gemini-flash', fn: () => generateText(prompt, system) },
    ])
  }
  // json
  return withFallback([
    { name: 'gemini-flash', fn: () => generateText(prompt, system) },
    ...(hasGroq() ? [{ name: 'groq-70b', fn: () => generateGroq(prompt, system, 'llama-3.3-70b-versatile') }] : []),
  ])
}

/** Multi-turn chat. Routes to Groq first (better RPM), Gemini fallback. */
export async function aiChat(messages: Msg[]): Promise<string> {
  return withFallback([
    ...(hasGroq() ? [{ name: 'groq-chat-70b', fn: () => chatGroq(messages) }] : []),
    { name: 'gemini-chat', fn: () => chatGemini(messages) },
  ])
}

/**
 * JSON-output generation. Tries Gemini's JSON mode first (most reliable),
 * falls back to Groq with permissive parsing.
 */
export async function aiJSON<T = unknown>(prompt: string, system?: string): Promise<T | null> {
  const attempts = [
    { name: 'gemini-json', fn: async () => generateJSON<T>(prompt, system) },
    ...(hasGroq() ? [{
      name: 'groq-json-70b',
      fn: async () => safeParseJSON<T>(await generateGroq(prompt, system, 'llama-3.3-70b-versatile')),
    }] : []),
  ]
  let lastErr: any
  for (const { name, fn } of attempts) {
    try {
      const out = await fn()
      if (out !== null) {
        console.log(`[ai] ${name} OK (json)`)
        return out
      }
      console.warn(`[ai] ${name} returned unparseable JSON, trying next`)
    } catch (err: any) {
      console.warn(`[ai] ${name} failed: ${err?.status ?? ''} ${err?.message ?? err}`)
      lastErr = err
    }
  }
  if (lastErr) throw lastErr
  return null
}

/** Vision — Gemini only (Groq has no vision yet). */
export async function aiVision(base64: string, mimeType: string, prompt: string): Promise<string> {
  return geminiVision(base64, mimeType, prompt)
}
