import { GoogleGenerativeAI } from '@google/generative-ai'

let _client: GoogleGenerativeAI | null = null

export function getGemini() {
  if (!_client) {
    _client = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!)
  }
  return _client
}

export function getModel(modelName = 'gemini-2.0-flash') {
  return getGemini().getGenerativeModel({ model: modelName })
}

export async function generateText(prompt: string, systemInstruction?: string): Promise<string> {
  const model = getGemini().getGenerativeModel({
    model: 'gemini-2.0-flash',
    systemInstruction,
  })
  const result = await model.generateContent(prompt)
  return result.response.text()
}

/**
 * Generate a JSON response from Gemini and parse it.
 * Strips Markdown code fences and trailing junk before parsing.
 * Returns the parsed object, or null if Gemini's reply isn't valid JSON.
 */
export async function generateJSON<T = unknown>(
  prompt: string,
  systemInstruction?: string,
): Promise<T | null> {
  const model = getGemini().getGenerativeModel({
    model: 'gemini-2.0-flash',
    systemInstruction,
    generationConfig: { responseMimeType: 'application/json' },
  })
  const result = await model.generateContent(prompt)
  const text = result.response.text()
  return safeParseJSON<T>(text)
}

/** Detect a Gemini rate-limit (429) error. Returns retry seconds if known. */
export function isRateLimit(err: any): { rateLimited: boolean; retryAfterSec: number | null } {
  const status = err?.status ?? err?.response?.status
  if (status !== 429) return { rateLimited: false, retryAfterSec: null }
  // Try to pull retryDelay out of Gemini's error details
  const details: any[] = err?.errorDetails ?? []
  for (const d of details) {
    const delay: string | undefined = d?.retryDelay
    if (delay && /^\d+s$/.test(delay)) {
      return { rateLimited: true, retryAfterSec: parseInt(delay, 10) }
    }
  }
  return { rateLimited: true, retryAfterSec: null }
}

export function safeParseJSON<T = unknown>(raw: string): T | null {
  if (!raw) return null
  // Strip markdown fences if Gemini still includes them
  let s = raw.trim()
  s = s.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '').trim()
  // Take from first '{' to last '}'
  const first = s.indexOf('{'), last = s.lastIndexOf('}')
  if (first !== -1 && last !== -1 && last > first) s = s.slice(first, last + 1)
  try { return JSON.parse(s) as T } catch { return null }
}

/**
 * Multi-turn chat. Mirrors grokChat() so existing callers can swap easily.
 * `system` is sent as systemInstruction. Other messages map to Gemini's role
 * convention: 'user' → 'user', 'assistant' → 'model'.
 */
export async function chatGemini(
  messages: { role: 'system' | 'user' | 'assistant'; content: string }[],
): Promise<string> {
  const systemInstruction = messages.find((m) => m.role === 'system')?.content
  const turns = messages
    .filter((m) => m.role !== 'system')
    .map((m) => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }],
    }))
  const model = getGemini().getGenerativeModel({
    model: 'gemini-2.0-flash',
    systemInstruction,
  })
  const result = await model.generateContent({ contents: turns as any })
  return result.response.text()
}

/** Send an image + prompt to Gemini. Returns the text response. */
export async function geminiVision(
  base64: string,
  mimeType: string,
  prompt: string,
): Promise<string> {
  const model = getGemini().getGenerativeModel({ model: 'gemini-2.0-flash' })
  const result = await model.generateContent([
    { inlineData: { data: base64, mimeType } },
    { text: prompt },
  ])
  return result.response.text()
}
