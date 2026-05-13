// Groq Cloud (cloud.groq.com) — free LLM inference, OpenAI-compatible.
// NOT the same as xAI Grok. Keys start with `gsk_`.
//
// Free tier (as of 2026): 14,400 requests/day, 30 RPM for Llama 3.3 70B.
// Used as a fallback when Gemini hits its rate limit.

const GROQ_BASE = 'https://api.groq.com/openai/v1'
const DEFAULT_MODEL = 'llama-3.3-70b-versatile'

function getKey(): string | null {
  // Support either GROQ_API_KEY (correct name) or legacy GROK_API_KEY
  return process.env.GROQ_API_KEY || process.env.GROK_API_KEY || null
}

export function hasGroq(): boolean {
  return !!getKey()
}

function headers(key: string) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${key}`,
  }
}

export async function chatGroq(
  messages: { role: 'system' | 'user' | 'assistant'; content: string }[],
  model = DEFAULT_MODEL,
): Promise<string> {
  const key = getKey()
  if (!key) throw new Error('No GROQ_API_KEY in env')
  const res = await fetch(`${GROQ_BASE}/chat/completions`, {
    method: 'POST',
    headers: headers(key),
    body: JSON.stringify({ model, messages, temperature: 0.7 }),
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(`Groq error ${res.status}: ${err}`)
  }
  const data = await res.json()
  return data.choices?.[0]?.message?.content ?? ''
}

export async function generateGroq(
  prompt: string,
  system?: string,
  model = DEFAULT_MODEL,
): Promise<string> {
  const messages: { role: 'system' | 'user'; content: string }[] = []
  if (system) messages.push({ role: 'system', content: system })
  messages.push({ role: 'user', content: prompt })
  return chatGroq(messages, model)
}
