import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { geminiChat, geminiSchema } from './gemini-ai'
import { aiDeep, aiStructured, aiText, hasAi } from './ai'

const fetchMock = vi.fn()
const answer = (text = 'Take one small step.') => new Response(JSON.stringify({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text }] } }] }))

beforeEach(() => {
  vi.stubEnv('GEMINI_API_KEY', 'test-key-private')
  vi.stubEnv('GEMINI_MODEL', '')
  vi.stubGlobal('fetch', fetchMock)
  vi.spyOn(console, 'warn').mockImplementation(() => undefined)
  fetchMock.mockReset().mockResolvedValue(answer())
})
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.useRealTimers(); vi.restoreAllMocks() })

describe('Gemini transport', () => {
  it('preserves system instructions and full conversation with correct roles and header authentication', async () => {
    const turns = Array.from({ length: 24 }, (_, i) => ({ role: i % 2 ? 'assistant' as const : 'user' as const, content: `turn ${i}` }))
    expect(await geminiChat([{ role: 'system', content: 'Coach' }, { role: 'system', content: 'Account context' }, ...turns])).toBe('Take one small step.')
    const [url, request] = fetchMock.mock.calls[0]
    expect(url).toBe('https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent')
    expect(url).not.toContain('test-key-private')
    expect(request.headers['x-goog-api-key']).toBe('test-key-private')
    expect(request.redirect).toBe('error')
    const body = JSON.parse(request.body)
    expect(body.systemInstruction.parts[0].text).toBe('Coach\n\nAccount context')
    expect(body.contents).toHaveLength(24)
    expect(body.contents[1]).toEqual({ role: 'model', parts: [{ text: 'turn 1' }] })
    expect(body.generationConfig.thinkingConfig).toEqual({ thinkingLevel: 'LOW' })
  })

  it('sends the response schema and configurable model', async () => {
    vi.stubEnv('GEMINI_MODEL', 'gemini-2.5-flash')
    await geminiChat([{ role: 'user', content: 'Plan' }], { schema: { kind: 'object', fields: { title: { kind: 'string' } } } })
    expect(fetchMock.mock.calls[0][0]).toContain('gemini-2.5-flash:generateContent')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).generationConfig).toMatchObject({ responseMimeType: 'application/json', responseJsonSchema: { type: 'object', required: ['title'] } })
  })

  it('excludes thinking parts and joins answer text', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ candidates: [{ finishReason: 'STOP', content: { parts: [{ thought: true, text: 'private thought' }, { text: 'Hello ' }, { text: 'there.' }] } }] })))
    expect(await geminiChat([])).toBe('Hello there.')
  })

  it.each([401, 403, 429, 500])('sanitizes HTTP %s errors', async status => {
    fetchMock.mockResolvedValue(new Response('private upstream message test-key-private', { status }))
    await expect(geminiChat([])).rejects.toMatchObject({ name: 'AiError', status })
    await expect(geminiChat([])).rejects.not.toThrow(/private upstream|test-key-private/)
  })

  it('recognizes Google invalid-key errors reported as HTTP 400', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ error: { message: 'private', details: [{ reason: 'API_KEY_INVALID' }] } }), { status: 400 }))
    await expect(geminiChat([])).rejects.toMatchObject({ status: 401 })
  })

  it.each([
    { promptFeedback: { blockReason: 'SAFETY' } },
    { candidates: [{ finishReason: 'MAX_TOKENS', content: { parts: [{ text: 'partial' }] } }] },
    { candidates: [{ finishReason: 'SAFETY', content: { parts: [{ text: 'blocked' }] } }] },
    { candidates: [{ content: { parts: [{ text: 'unfinished' }] } }] },
    { candidates: [{ finishReason: 'STOP', content: { parts: [{ text: '' }] } }] },
    null,
  ])('rejects blocked, incomplete, empty and malformed responses', async payload => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(payload)))
    await expect(geminiChat([])).rejects.toMatchObject({ name: 'AiError' })
  })

  it('fails before contacting Google when the key is missing or model is invalid', async () => {
    vi.stubEnv('GEMINI_API_KEY', '')
    await expect(geminiChat([])).rejects.toThrow('not configured')
    vi.stubEnv('GEMINI_API_KEY', 'test-key-private')
    vi.stubEnv('GEMINI_MODEL', '../other-host')
    await expect(geminiChat([])).rejects.toThrow('model is not configured')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('an explicit Gemini selection without a key never contacts legacy providers', async () => {
    vi.stubEnv('AI_PROVIDER', 'gemini'); vi.stubEnv('GEMINI_API_KEY', '')
    vi.stubEnv('CLOUDFLARE_APP_KEY', 'legacy-key'); vi.stubEnv('ANTHROPIC_API_KEY', 'legacy-key')
    expect(hasAi()).toBe(false)
    await expect(aiText('chat', 'hello')).rejects.toThrow('not configured')
    await expect(aiDeep([{ role: 'user', content: 'review' }])).rejects.toThrow('not configured')
    expect(await aiStructured('plan', { kind: 'string' })).toMatchObject({ ok: false, code: 'not_configured', attempts: 1 })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('honors cancellation before a request and while reading the body', async () => {
    const before = new AbortController(); before.abort()
    await expect(geminiChat([], { signal: before.signal })).rejects.toThrow('cancelled')
    expect(fetchMock).not.toHaveBeenCalled()
    const during = new AbortController()
    fetchMock.mockImplementation(async (_url, options) => ({ ok: true, json: () => new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new Error('private')), { once: true })) }))
    const result = geminiChat([], { signal: during.signal })
    const assertion = expect(result).rejects.toThrow('cancelled')
    await Promise.resolve(); during.abort()
    await assertion
  })

  it('keeps the timeout active while reading the response body', async () => {
    vi.useFakeTimers()
    fetchMock.mockImplementation(async (_url, options) => ({ ok: true, json: () => new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new Error('private')), { once: true })) }))
    const assertion = expect(geminiChat([], { timeoutMs: 25 })).rejects.toThrow('timed out')
    await vi.advanceTimersByTimeAsync(25)
    await assertion
  })

  it('converts nested nullable fields, optional properties and validation constraints', () => {
    expect(geminiSchema({ kind: 'object', optional: ['note'], fields: {
      count: { kind: 'number', int: true, min: 1, max: 4 }, note: { kind: 'nullable', of: { kind: 'string', minLength: 3, maxLength: 20 } },
      items: { kind: 'array', minItems: 1, maxItems: 2, of: { kind: 'enum', values: ['daily', 'weekly'] } }, enabled: { kind: 'boolean' },
    } })).toMatchObject({ required: ['count', 'items', 'enabled'], properties: {
      count: { type: 'integer', minimum: 1, maximum: 4 }, note: { anyOf: [{ type: 'string', minLength: 3, maxLength: 20 }, { type: 'null' }] },
      items: { type: 'array', minItems: 1, maxItems: 2, items: { enum: ['daily', 'weekly'] } }, enabled: { type: 'boolean' },
    } })
  })
})
