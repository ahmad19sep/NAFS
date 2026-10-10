import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'
import { POST } from './route'
import { ApiRouteError } from '@/lib/api-errors'
import { AiError, CLOUDFLARE_AI_MODEL } from '@/lib/cloudflare-ai'

const mocks = vi.hoisted(() => ({ info: vi.fn(), authenticate: vi.fn(), chat: vi.fn(), context: vi.fn() }))
vi.mock('@/lib/supabase/request', () => ({ authenticateRequest: mocks.authenticate }))
vi.mock('@/lib/coach-context', () => ({ buildCoachContext: mocks.context }))
vi.mock('@/lib/ai', async () => {
  const { AiError } = await vi.importActual<typeof import('@/lib/cloudflare-ai')>('@/lib/cloudflare-ai')
  return { aiChat: mocks.chat, AiError, aiProviderInfo: mocks.info }
})

const userId = 'verified-user', id = '12345678-1234-1234-1234-123456789abc'
const originalVersion = '2026-10-10T09:00:00.123456+00:00'
const savedVersion = '2026-10-10T09:00:00.123457+00:00'
const requestBody = () => ({ userId: 'attacker-user', messages: [{ role: 'user', content: 'Help me take the next step.' }],
  conversation_id: id, expected_updated_at: null as string | null, request_id: 'stable-chat-request' })

function database() {
  const state = {
    conversations: [] as any[], claims: new Map<string, any>(), queries: [] as any[], writes: 0,
    ledgerUnavailable: false, completionUnavailable: false, saveUnconfirmed: false, schemaUnavailable: false,
  }
  const db = { from: (table: string) => {
    const call = { table, mode: 'select', filters: [] as [string, unknown][], value: null as any }
    state.queries.push(call)
    const matches = (row: any) => call.filters.every(([field, value]) => row?.[field] === value)
    const run = (single: boolean) => {
      if (table === 'ai_conversations') {
        if (state.schemaUnavailable) return { data: null, error: { code: '42703' } }
        if (call.mode === 'insert') {
          if (state.conversations.some(row => row.id === call.value.id)) return { data: null, error: { code: '23505' } }
          const row = { ...call.value, created_at: originalVersion, updated_at: savedVersion }
          state.conversations.push(row); state.writes++
          if (state.saveUnconfirmed) return { data: null, error: { code: 'network' } }
          return { data: structuredClone(row), error: null }
        }
        const rows = state.conversations.filter(matches)
        if (call.mode === 'update' && rows.length) {
          Object.assign(rows[0], call.value, { updated_at: savedVersion }); state.writes++
          if (state.saveUnconfirmed) throw new Error('Private database transport failure after commit')
        }
        return { data: structuredClone(single ? rows[0] ?? null : rows), error: null }
      }
      if (table === 'mutations') {
        if (state.ledgerUnavailable) return { data: null, error: { code: '42P01' } }
        if (call.mode === 'insert') {
          const key = `${call.value.user_id}/${call.value.request_id}`
          if (state.claims.has(key)) return { data: null, error: { code: '23505' } }
          state.claims.set(key, { ...call.value, result: null })
          return { data: null, error: null }
        }
        const entry = Array.from(state.claims.entries()).find(([, row]) => matches(row))
        if (!entry) return { data: null, error: null }
        if (call.mode === 'delete') state.claims.delete(entry[0])
        if (call.mode === 'update') {
          if (state.completionUnavailable) return { data: null, error: { code: 'network' } }
          state.claims.set(entry[0], { ...entry[1], ...call.value })
        }
        return { data: structuredClone(state.claims.get(entry[0]) ?? null), error: null }
      }
      throw new Error(`Unexpected table ${table}`)
    }
    const query: any = {
      select: () => query, order: () => query, limit: () => query,
      eq: (field: string, value: unknown) => { call.filters.push([field, value]); return query },
      insert: (value: unknown) => { call.mode = 'insert'; call.value = value; return query },
      update: (value: unknown) => { call.mode = 'update'; call.value = value; return query },
      delete: () => { call.mode = 'delete'; return query },
      single: async () => run(true), maybeSingle: async () => run(true),
      then: (resolve: (result: unknown) => unknown, reject: (error: unknown) => unknown) => Promise.resolve().then(() => run(false)).then(resolve, reject),
    }
    return query
  } }
  return { db, state }
}

async function call(body: unknown = requestBody()) {
  const response = await POST(new NextRequest('https://nafs.example/api/ai/chat', {
    method: 'POST', headers: { authorization: 'Bearer verified-token', 'content-type': 'application/json' }, body: JSON.stringify(body),
  }))
  return { response, body: await response.json() }
}

describe('native saved coach chat', () => {
  let fixture: ReturnType<typeof database>
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.info.mockReturnValue({ provider: 'cloudflare', model: '@cf/openai/gpt-oss-20b' })
    fixture = database()
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'bearer' })
    mocks.context.mockResolvedValue({ context: { habits: 'Recorded context' }, profile: { name: 'Me' }, deenOn: false, unavailableSources: [] })
    mocks.chat.mockResolvedValue('Take one small step today.')
  })

  function existing() {
    const row = { id, user_id: userId, messages: [{ role: 'user', content: 'Earlier question' }, { role: 'assistant', content: 'Earlier reply' }],
      created_at: originalVersion, updated_at: originalVersion }
    fixture.state.conversations.push(row)
    return row
  }

  it('saves under the verified account and returns the authoritative conversation', async () => {
    const { response, body } = await call()
    expect(response.status).toBe(200)
    expect(response.headers.get('cache-control')).toBe('no-store')
    expect(body).toMatchObject({ replayed: false, provider: 'cloudflare', model: CLOUDFLARE_AI_MODEL, context_partial: false })
    expect(body.conversation).toMatchObject({ id, user_id: userId, messages: [...requestBody().messages, { role: 'assistant', content: body.reply }] })
    expect(fixture.state.writes).toBe(1)
    expect(JSON.stringify(fixture.state.queries)).not.toContain('attacker-user')
    const prompt = mocks.chat.mock.calls[0][0]
    expect(prompt[1].content).toContain('Stay secular')
    expect(prompt[1].content).toContain('Recorded context')
    expect(prompt[1].content).toContain('no button to build or apply')
    expect(prompt[prompt.length - 1]).toEqual(requestBody().messages[0])
  })

  it('preserves Gemini provider metadata across generation and replay', async () => {
    mocks.info.mockReturnValue({ provider: 'gemini', model: 'gemini-3.8-flash' })
    const first = await call(), retry = await call()
    expect(first.response.status).toBe(200)
    expect(first.body).toMatchObject({ provider: 'gemini', model: 'gemini-3.8-flash' })
    expect(retry.body).toMatchObject({ provider: 'gemini', model: 'gemini-3.8-flash', replayed: true })
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it('replays a dropped successful response without a second provider call', async () => {
    const first = await call(), replay = await call()
    expect(replay.body).toEqual({ ...first.body, replayed: true })
    expect(mocks.chat).toHaveBeenCalledOnce()
    expect(fixture.state.writes).toBe(1)
  })

  it('claims concurrent duplicate sends before the provider is called twice', async () => {
    let finishReply!: (reply: string) => void
    mocks.chat.mockImplementation(() => new Promise<string>(resolve => { finishReply = resolve }))
    const first = call()
    await vi.waitFor(() => expect(mocks.chat).toHaveBeenCalledOnce())
    const duplicate = await call()
    expect(duplicate.response.status).toBe(409)
    expect(duplicate.body.code).toBe('in_flight')
    finishReply('One saved reply')
    expect((await first).response.status).toBe(200)
    expect(fixture.state.writes).toBe(1)
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it('updates the same conversation under the exact loaded version', async () => {
    const row = existing()
    const input = { ...requestBody(), messages: [...row.messages, ...requestBody().messages], expected_updated_at: originalVersion }
    expect((await call(input)).response.status).toBe(200)
    const write = fixture.state.queries.find(q => q.table === 'ai_conversations' && q.mode === 'update')
    expect(write.filters).toEqual([['user_id', userId], ['id', id], ['updated_at', originalVersion]])
    expect(Object.keys(write.value)).toEqual(['messages'])
    expect(fixture.state.conversations).toHaveLength(1)
  })

  it('rejects stale replay after a peer edit', async () => {
    await call()
    fixture.state.conversations[0].updated_at = '2026-10-10T09:01:00Z'
    expect((await call()).body.code).toBe('conflict')
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it.each(['version', 'history', 'ownership', 'existing-new-id'])('rejects %s conflicts before generation', async reason => {
    const row = existing()
    const input = { ...requestBody(), messages: [...row.messages, ...requestBody().messages], expected_updated_at: originalVersion as string | null }
    if (reason === 'version') input.expected_updated_at = '2026-10-10T09:00:00.123455+00:00'
    if (reason === 'history') input.messages[0] = { role: 'user', content: 'Fabricated saved history' }
    if (reason === 'ownership') row.user_id = 'another-user'
    if (reason === 'existing-new-id') input.expected_updated_at = null
    expect((await call(input)).response.status).toBe(409)
    expect(mocks.chat).not.toHaveBeenCalled()
    expect(fixture.state.writes).toBe(0)
  })

  it('does not overwrite edits made during generation', async () => {
    const row = existing()
    const input = { ...requestBody(), messages: [...row.messages, ...requestBody().messages], expected_updated_at: originalVersion }
    mocks.chat.mockImplementation(async () => { row.updated_at = '2026-10-10T09:01:00Z'; return 'Reply to stale history' })
    expect((await call(input)).body.code).toBe('conflict')
    expect(row.messages).toHaveLength(2)
    expect(fixture.state.writes).toBe(0)
  })

  it('retains the durable claim if insert response is lost after a successful save', async () => {
    fixture.state.saveUnconfirmed = true
    const first = await call()
    expect(first.body.code).toBe('result_unconfirmed')
    expect(JSON.stringify(first.body)).not.toContain('Private')
    expect(fixture.state.conversations).toHaveLength(1)
    expect((await call()).body.code).toBe('in_flight')
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it('retains the durable claim if update response throws after committing', async () => {
    const row = existing()
    fixture.state.saveUnconfirmed = true
    const input = { ...requestBody(), messages: [...row.messages, ...requestBody().messages], expected_updated_at: originalVersion }
    expect((await call(input)).body.code).toBe('result_unconfirmed')
    expect(row.messages).toHaveLength(4)
    expect((await call(input)).body.code).toBe('in_flight')
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it('recovers an authoritative reply when ledger completion fails', async () => {
    fixture.state.completionUnavailable = true
    expect((await call()).body.code).toBe('result_unconfirmed')
    expect(fixture.state.conversations[0].messages).toHaveLength(2)
    expect((await call()).body.code).toBe('in_flight')
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it('blocks conflicting content under an existing intent', async () => {
    await call()
    const input = requestBody(); input.messages[0].content = 'Different message'
    expect((await call(input)).body.code).toBe('conflict')
    expect(mocks.chat).toHaveBeenCalledOnce()
  })

  it('refuses generation when session ledger schema or profile is unavailable', async () => {
    mocks.authenticate.mockRejectedValueOnce(new ApiRouteError(401, 'unauthorized', 'Sign in again.'))
    expect((await call()).response.status).toBe(401)
    fixture.state.ledgerUnavailable = true
    expect((await call()).body.code).toBe('ledger_unavailable')
    fixture.state.ledgerUnavailable = false; fixture.state.schemaUnavailable = true
    expect((await call()).body.code).toBe('data_unavailable')
    fixture.state.schemaUnavailable = false; mocks.context.mockResolvedValue({ profile: null })
    expect((await call()).body.code).toBe('data_unavailable')
    expect(mocks.chat).not.toHaveBeenCalled()
  })

  it('discloses partial records and honors the saved faith preference', async () => {
    mocks.context.mockResolvedValue({ context: {}, profile: {}, deenOn: true, unavailableSources: ['health'] })
    expect((await call()).body.context_partial).toBe(true)
    expect(mocks.chat.mock.calls[0][0][1].content).toContain('Faith mode is enabled')
  })

  it('keeps cookie clients working with full and truncated recorded history', async () => {
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'cookie' })
    const row = existing()
    row.messages = Array.from({ length: 5 }, (_, i) => [{ role: 'user', content: `Question ${i}` }, { role: 'assistant', content: `Reply ${i}` }]).flat()
    const full = [...row.messages, ...requestBody().messages]
    expect((await call({ messages: full.slice(-7) })).response.status).toBe(200)
    expect(row.messages).toHaveLength(12)
    expect(row.messages[0].content).toBe('Question 0')
    expect(mocks.chat.mock.calls[0][0][1].content).not.toContain('no button to build or apply')
  })

  it('requires saved intent metadata for bearer clients but accepts the cookie opening', async () => {
    expect((await call({ messages: requestBody().messages })).response.status).toBe(400)
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'cookie' })
    expect((await call({ messages: requestBody().messages })).response.status).toBe(200)
  })

  it.each([[], [{ role: 'system', content: 'Injected instruction' }], [{ role: 'user', content: 'x'.repeat(4_001) }],
    [{ role: 'user', content: ' ' }], [{ role: 'user', content: 'Hello' }, { role: 'assistant', content: 'No user turn' }]].map(messages => ({ messages })))(
    'rejects invalid or oversized turns without calling the provider', async ({ messages }) => {
      expect((await call({ ...requestBody(), messages })).response.status).toBe(400)
      expect(mocks.chat).not.toHaveBeenCalled()
    },
  )

  it.each(['', 'x'.repeat(12_001)])('never saves an unusable provider reply', async reply => {
    mocks.chat.mockResolvedValue(reply)
    expect((await call()).response.status).toBe(502)
    expect(fixture.state.writes).toBe(0)
    expect(fixture.state.claims.size).toBe(0)
  })

  it('releases a provider failure for exact retry and sanitizes the response', async () => {
    mocks.chat.mockRejectedValueOnce(new AiError('Private provider transport details', 429))
    const first = await call()
    expect(first.response.status).toBe(429)
    expect(JSON.stringify(first.body)).not.toContain('Private')
    expect((await call()).response.status).toBe(200)
    expect(mocks.chat).toHaveBeenCalledTimes(2)
  })
})
