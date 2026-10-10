import { AiError, type AiOptions, type ChatMessage } from './cloudflare-ai'
import type { Schema } from './schema'

export const DEFAULT_GEMINI_MODEL = 'gemini-3.8-flash'

export function geminiModel(): string {
  return process.env.GEMINI_MODEL?.trim() || DEFAULT_GEMINI_MODEL
}

export function hasGeminiAi(): boolean {
  return !!process.env.GEMINI_API_KEY?.trim()
}

/** Google's JSON Schema subset; local validation still checks every response. */
export function geminiSchema(schema: Schema): Record<string, unknown> {
  switch (schema.kind) {
    case 'string': return { type: 'string', ...(schema.minLength == null ? {} : { minLength: schema.minLength }), ...(schema.maxLength == null ? {} : { maxLength: schema.maxLength }) }
    case 'number': return { type: schema.int ? 'integer' : 'number', ...(schema.min == null ? {} : { minimum: schema.min }), ...(schema.max == null ? {} : { maximum: schema.max }) }
    case 'boolean': return { type: 'boolean' }
    case 'enum': return { type: 'string', enum: [...schema.values] }
    case 'nullable': return { anyOf: [geminiSchema(schema.of), { type: 'null' }] }
    case 'array': return { type: 'array', items: geminiSchema(schema.of), ...(schema.minItems == null ? {} : { minItems: schema.minItems }), ...(schema.maxItems == null ? {} : { maxItems: schema.maxItems }) }
    case 'object': return {
      type: 'object', properties: Object.fromEntries(Object.entries(schema.fields).map(([name, field]) => [name, geminiSchema(field)])),
      required: Object.keys(schema.fields).filter(name => !schema.optional?.includes(name)),
    }
  }
}

function statusMessage(status: number): string {
  if (status === 401 || status === 403) return 'Gemini access was denied. Check the server API key and its permissions.'
  if (status === 429) return 'Gemini quota or rate limit has been reached. Try again later.'
  if (status >= 500) return 'Gemini is temporarily unavailable. Please try again.'
  return `Gemini rejected the request (${status}). Check the server model configuration.`
}

/** Stateless text calls. The server key stays in a header and never reaches clients. */
export async function geminiChat(messages: ChatMessage[], opts: AiOptions & { schema?: Schema } = {}): Promise<string> {
  const key = process.env.GEMINI_API_KEY?.trim()
  if (!key) throw new AiError('Gemini AI is not configured. Set GEMINI_API_KEY on the server.')
  if (opts.signal?.aborted) throw new AiError('The AI request was cancelled.')
  const model = geminiModel()
  if (!/^gemini-[a-zA-Z0-9._-]+$/.test(model)) throw new AiError('Gemini model is not configured correctly. Check GEMINI_MODEL.')

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), opts.timeoutMs ?? 30_000)
  const onAbort = () => controller.abort()
  opts.signal?.addEventListener('abort', onAbort, { once: true })
  try {
    const system = messages.filter(m => m.role === 'system').map(m => m.content).join('\n\n')
    const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: 'POST', redirect: 'error', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({
        contents: messages.filter(m => m.role !== 'system').map(m => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] })),
        ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
        generationConfig: {
          // Thinking shares the output budget. Leave room for reasoning and the answer.
          maxOutputTokens: (opts.maxTokens ?? 1500) + 4096, temperature: opts.temperature ?? 1,
          ...(/^gemini-3[.-]/.test(model) ? { thinkingConfig: { thinkingLevel: 'LOW' } } : {}),
          ...(opts.schema ? { responseMimeType: 'application/json', responseJsonSchema: geminiSchema(opts.schema) } : {}),
        },
      }),
      signal: controller.signal,
    })
    if (!res.ok) {
      // Google can report an invalid key as HTTP 400. Inspect only its machine code.
      if (res.status === 400) {
        const error = await res.json().catch(() => null)
        if (error?.error?.details?.some((detail: { reason?: string }) => detail.reason === 'API_KEY_INVALID')) {
          throw new AiError('The Gemini API key was rejected. Check GEMINI_API_KEY on the server.', 401)
        }
      }
      throw new AiError(statusMessage(res.status), res.status)
    }
    const data = await res.json()
    if (data?.promptFeedback?.blockReason) throw new AiError('Gemini could not answer this request. Try rephrasing it.')
    const candidate = data?.candidates?.[0]
    if (candidate?.finishReason === 'MAX_TOKENS') throw new AiError('Gemini returned an incomplete answer. Please try again.')
    if (candidate?.finishReason && candidate.finishReason !== 'STOP') throw new AiError('Gemini could not answer this request. Try rephrasing it.')
    if (!candidate || candidate.finishReason !== 'STOP' || !Array.isArray(candidate.content?.parts)) throw new AiError('Received an invalid response from Gemini.')
    const text = candidate.content.parts.filter((part: { thought?: boolean; text?: unknown }) => !part.thought && typeof part.text === 'string')
      .map((part: { text: string }) => part.text).join('').trim()
    if (!text) throw new AiError('Gemini returned an empty response.')
    return text
  } catch (error) {
    if (opts.signal?.aborted) throw new AiError('The AI request was cancelled.')
    // Operational metadata only: never log the key, prompts or upstream response body.
    console.warn('Gemini request failed', {
      model, status: error instanceof AiError ? error.status : null,
      reason: controller.signal.aborted ? 'timeout' : error instanceof AiError ? error.message : 'network_or_invalid_response',
    })
    if (controller.signal.aborted) throw new AiError('The Gemini request timed out. Please try again.')
    if (error instanceof AiError) throw error
    throw new AiError('Could not read a response from Gemini. Please try again.')
  } finally {
    clearTimeout(timer)
    opts.signal?.removeEventListener('abort', onAbort)
  }
}
