import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { aiChat, aiDeep, aiProviderInfo, aiStructured, aiText, deepProvider } from './ai'
import { AiError } from './cloudflare-ai'

const mocks = vi.hoisted(() => ({ gemini: vi.fn(), cloudflare: vi.fn(), text: vi.fn(), anthropic: vi.fn() }))
vi.mock('./gemini-ai', async () => ({ ...await vi.importActual<typeof import('./gemini-ai')>('./gemini-ai'), geminiChat: mocks.gemini }))
vi.mock('./cloudflare-ai', async () => ({ ...await vi.importActual<typeof import('./cloudflare-ai')>('./cloudflare-ai'), cloudflareChat: mocks.cloudflare, cloudflareText: mocks.text }))
vi.mock('./anthropic-ai', async () => ({ ...await vi.importActual<typeof import('./anthropic-ai')>('./anthropic-ai'), anthropicChat: mocks.anthropic }))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubEnv('AI_PROVIDER', 'gemini'); vi.stubEnv('GEMINI_API_KEY', 'test-key'); vi.stubEnv('GEMINI_MODEL', '')
  vi.stubEnv('ANTHROPIC_API_KEY', 'legacy-key')
  mocks.gemini.mockResolvedValue('Gemini answer')
})
afterEach(() => vi.unstubAllEnvs())
const schema = { kind: 'object' as const, fields: { title: { kind: 'string' as const, minLength: 3 } } }

describe('shared provider routing', () => {
  it('uses Gemini for every text task, chat and deep reviews even with an Anthropic key', async () => {
    for (const task of ['chat', 'verdict', 'bulk', 'json', 'deep'] as const) expect(await aiText(task, 'prompt', 'system')).toBe('Gemini answer')
    expect(await aiChat([{ role: 'user', content: 'hello' }])).toBe('Gemini answer')
    expect(await aiDeep([{ role: 'user', content: 'review' }])).toMatchObject({ provider: 'gemini', model: 'gemini-3.5-flash-lite', fellBack: false })
    expect(deepProvider()).toBe('gemini')
    expect(mocks.cloudflare).not.toHaveBeenCalled(); expect(mocks.text).not.toHaveBeenCalled(); expect(mocks.anthropic).not.toHaveBeenCalled()
  })

  it.each([false, true])('validates structured Gemini output and reports its provider (deep=%s)', async deep => {
    mocks.gemini.mockResolvedValue('{"title":"Small step"}')
    expect(await aiStructured('plan', schema, 'coach', { deep })).toMatchObject({ ok: true, data: { title: 'Small step' }, provider: 'gemini', fellBack: false, attempts: 1 })
    expect(mocks.gemini.mock.calls[0][1].schema).toEqual(schema)
  })

  it('corrects invalid structured output once before returning validated data', async () => {
    mocks.gemini.mockResolvedValueOnce('{"title":1}').mockResolvedValueOnce('{"title":"Fixed plan"}')
    expect(await aiStructured('plan', schema)).toMatchObject({ ok: true, attempts: 2, provider: 'gemini' })
    expect(mocks.gemini.mock.calls[1][0][1].content).toContain('wrong shape')
  })

  it.each([401, 403, 429, 500])('does not fall back or retry a Gemini transport failure (%s)', async status => {
    mocks.gemini.mockRejectedValue(new AiError('Gemini unavailable', status))
    expect(await aiStructured('plan', schema)).toMatchObject({ ok: false, attempts: 1, code: status === 401 || status === 403 ? 'unauthorized' : status === 429 ? 'rate_limited' : 'unavailable' })
    await expect(aiDeep([])).rejects.toThrow('Gemini unavailable')
    expect(mocks.cloudflare).not.toHaveBeenCalled(); expect(mocks.anthropic).not.toHaveBeenCalled()
  })

  it('automatically selects Gemini with a key and preserves legacy routing without it', () => {
    vi.stubEnv('AI_PROVIDER', '')
    expect(aiProviderInfo().provider).toBe('gemini')
    vi.stubEnv('GEMINI_API_KEY', '')
    expect(aiProviderInfo().provider).toBe('cloudflare')
    expect(deepProvider()).toBe('anthropic')
  })

  it('supports an explicit Cloudflare selection and rejects provider typos', () => {
    vi.stubEnv('AI_PROVIDER', 'cloudflare')
    expect(aiProviderInfo().provider).toBe('cloudflare')
    vi.stubEnv('AI_PROVIDER', 'gmeini')
    expect(() => aiProviderInfo()).toThrow('not configured correctly')
  })
})
