import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'
import { POST } from './route'
import { ApiRouteError } from '@/lib/api-errors'
import { AiError, CLOUDFLARE_AI_MODEL } from '@/lib/cloudflare-ai'
import { FutureLetter, letterFingerprint } from '@/lib/future-self'

const mocks = vi.hoisted(() => ({ info: vi.fn(), authenticate: vi.fn(), aiText: vi.fn(), context: vi.fn() }))
vi.mock('@/lib/supabase/request', () => ({ authenticateRequest: mocks.authenticate }))
vi.mock('@/lib/coach-context', () => ({ buildCoachContext: mocks.context }))
vi.mock('@/lib/ai', async () => {
  const { AiError } = await vi.importActual<typeof import('@/lib/cloudflare-ai')>('@/lib/cloudflare-ai')
  return { aiText: mocks.aiText, AiError, aiProviderInfo: mocks.info }
})
const userId = 'user'
const letterId = '00000000-0000-4000-8000-000000000001'
const original = (): FutureLetter => ({ id: letterId, user_id: userId,
  content: 'Dear me: امید 🌱\nKeep going.', written_at: '2026-10-10T22:30:00.123456+00:00',
  target_deliver_date: '2026-10-10', delivered_at: null, ai_reply_text: null })
const requestBody = () => ({ letter_id: letterId, expected_fingerprint: letterFingerprint(original()), request_id: 'stable-letter-request', user_id: 'attacker' })

function database() {
  const state = { letter: original() as FutureLetter | null, claims: new Map<string, any>(), reports: [] as any[],
    queries: [] as any[], rpcCalls: [] as any[], writes: 0,
    rpcMissing: false, rpcUnavailable: false, ledgerMissing: false, completionFailed: false, historyFailed: false,
    readFailed: false, writeResponseLost: false, writeNeverConfirmed: false }
  const db = {
    rpc: async (name: string, args: any) => {
      state.rpcCalls.push({ name, args })
      if (state.rpcMissing) return { data: null, error: { code: 'PGRST202' } }
      if (state.rpcUnavailable) return { data: null, error: { code: 'network' } }
      const l = state.letter
      const matches = l && l.user_id === userId && l.id === args.p_letter_id && l.content === args.p_content &&
        l.written_at === args.p_written_at && l.target_deliver_date === args.p_target_date && l.ai_reply_text === null && l.delivered_at === null
      if (!matches) return { data: [], error: null }
      if (args.p_reply_text !== null) {
        if (state.writeNeverConfirmed) return { data: null, error: { code: 'network' } }
        state.letter = { ...l!, ai_reply_text: args.p_reply_text, delivered_at: '2026-10-10T23:00:00Z' }
        state.writes++
        if (state.writeResponseLost) throw new Error('Private network details')
      }
      return { data: [structuredClone(state.letter)], error: null }
    },
    from: (table: string) => {
      const call = { table, mode: 'select', filters: [] as [string, unknown][], value: null as any }
      state.queries.push(call)
      const matches = (row: any) => call.filters.every(([field, value]) => row?.[field] === value)
      const run = () => {
        if (table === 'future_self_letters') return { data: matches(state.letter) ? structuredClone(state.letter) : null, error: state.readFailed ? { code: 'network' } : null }
        if (table === 'mutations') {
          if (state.ledgerMissing) return { data: null, error: { code: '42P01' } }
          if (call.mode === 'insert') {
            const key = call.value.request_id
            if (state.claims.has(key)) return { data: null, error: { code: '23505' } }
            state.claims.set(key, { ...call.value, result: null })
            return { data: null, error: null }
          }
          const entry = Array.from(state.claims.entries()).find(([, row]) => matches(row))
          if (!entry) return { data: null, error: null }
          if (call.mode === 'delete') state.claims.delete(entry[0])
          if (call.mode === 'update') {
            if (state.completionFailed) return { data: null, error: { code: 'network' } }
            state.claims.set(entry[0], { ...entry[1], ...call.value })
          }
          return { data: structuredClone(state.claims.get(entry[0]) ?? null), error: null }
        }
        if (table === 'ai_reports') {
          if (state.historyFailed) throw new Error('Private history details')
          state.reports.push(call.value)
          return { data: null, error: null }
        }
        throw new Error(`Unexpected table ${table}`)
      }
      const query: any = {
        select: () => query,
        eq: (field: string, value: unknown) => { call.filters.push([field, value]); return query },
        insert: (value: any) => { call.mode = 'insert'; call.value = value; return query },
        update: (value: any) => { call.mode = 'update'; call.value = value; return query },
        delete: () => { call.mode = 'delete'; return query },
        maybeSingle: async () => run(),
        then: (resolve: any, reject: any) => Promise.resolve().then(run).then(resolve, reject),
      }
      return query
    },
  }
  return { state, db }
}
async function call(body: unknown = requestBody()) {
  const response = await POST(new NextRequest('https://nafs.example/api/ai/future-self', {
    method: 'POST', headers: { authorization: 'Bearer verified-token' }, body: JSON.stringify(body),
  }))
  return { response, body: await response.json() }
}

describe('future-self replies', () => {
  let fixture: ReturnType<typeof database>
  let context: any
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.info.mockReturnValue({ provider: 'cloudflare', model: '@cf/openai/gpt-oss-20b' })
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T23:00:00Z'))
    fixture = database()
    context = { today: '2026-10-10', profile: { deen_enabled: false }, deenOn: false,
      context: { habit_logs: { recorded: 3, completed: 2 }, not_known: ['lifetime progress'] }, unavailableSources: [] }
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'bearer' })
    mocks.context.mockImplementation(async () => context)
    mocks.aiText.mockResolvedValue('A thoughtful saved reply.')
  })
  afterEach(() => vi.useRealTimers())

  it('agrees with Android on the Unicode fingerprint and exact timestamp precision', () => {
    expect(letterFingerprint(original())).toBe('4f0b538a4af26256b79aeba1290840986807b0b6775cc3c154a486a227493427')
    expect(letterFingerprint({ ...original(), written_at: '2026-10-10T22:30:00.123457+00:00' })).not.toBe(letterFingerprint(original()))
  })
  it('uses verified account data and a guarded body-based write', async () => {
    const { response, body } = await call()
    expect(response.status).toBe(200)
    expect(response.headers.get('cache-control')).toBe('no-store')
    expect(body).toMatchObject({ reply: 'A thoughtful saved reply.', replayed: false, history_saved: true, model: CLOUDFLARE_AI_MODEL })
    expect(fixture.state.writes).toBe(1)
    expect(fixture.state.rpcCalls[1].args).toEqual({ p_letter_id: letterId, p_content: original().content,
      p_written_at: original().written_at, p_target_date: original().target_deliver_date, p_reply_text: body.reply })
    expect(fixture.state.queries[0].filters).toEqual([['user_id', userId], ['id', letterId]])
    expect(mocks.context).toHaveBeenCalledWith(fixture.db, userId)
    expect(JSON.parse(mocks.aiText.mock.calls[0][1]).recorded_context).toEqual(context.context)
    expect(mocks.aiText.mock.calls[0][2]).toContain('Keep this reply secular')
    expect(fixture.state.reports[0]).toMatchObject({ user_id: userId, type: 'letter_reply', model_used: CLOUDFLARE_AI_MODEL })
  })
  it('records the Gemini model used for a saved letter reply', async () => {
    mocks.info.mockReturnValue({ provider: 'gemini', model: 'gemini-3.8-flash' })
    const result = await call()
    expect(result.response.status).toBe(200)
    expect(result.body).toMatchObject({ provider: 'gemini', model: 'gemini-3.8-flash' })
    expect(fixture.state.reports[0].model_used).toBe('gemini-3.8-flash')
  })
  it('recovers a saved reply without regeneration, even with another client request id', async () => {
    await call()
    const retry = await call({ ...requestBody(), request_id: 'another-device-request' })
    expect(retry.response.status).toBe(200)
    expect(retry.body.replayed).toBe(true)
    expect(mocks.aiText).toHaveBeenCalledOnce()
    expect(fixture.state.writes).toBe(1)
  })
  it('uses one durable generation claim across simultaneous devices', async () => {
    let finish!: (reply: string) => void
    let started!: () => void
    const generating = new Promise<void>(resolve => { started = resolve })
    mocks.aiText.mockImplementation(() => { started(); return new Promise<string>(resolve => { finish = resolve }) })
    const first = call()
    await generating
    const second = await call({ ...requestBody(), request_id: 'different-device-request' })
    expect(second.body.code).toBe('in_flight')
    finish('One saved reply.')
    expect((await first).response.status).toBe(200)
    expect(mocks.aiText).toHaveBeenCalledOnce()
  })
  it('checks the account calendar day, allowing due and past letters but refusing future dates', async () => {
    context.today = '2026-10-09'
    expect((await call()).body.code).toBe('not_due')
    expect(mocks.aiText).not.toHaveBeenCalled()
    context.today = '2026-10-11'
    expect((await call()).response.status).toBe(200)
  })
  it.each([{ expected_fingerprint: '0'.repeat(64) }, { letter_id: 'bad' }, { request_id: undefined }, { expected_fingerprint: undefined }])('refuses stale or malformed native requests before generation', async change => {
    expect((await call({ ...requestBody(), ...change })).response.status).toBe(change.expected_fingerprint?.length === 64 ? 409 : 400)
    expect(mocks.aiText).not.toHaveBeenCalled()
  })
  it('fails closed before paid generation if auth, profile, migration, or ledger is unavailable', async () => {
    mocks.authenticate.mockRejectedValueOnce(new ApiRouteError(401, 'unauthorized', 'Sign in again.'))
    expect((await call()).response.status).toBe(401)
    context.unavailableSources = ['profile']
    expect((await call()).body.code).toBe('data_unavailable')
    context.unavailableSources = []
    fixture.state.rpcMissing = true
    expect((await call()).body.code).toBe('not_configured')
    fixture.state.rpcMissing = false
    fixture.state.ledgerMissing = true
    expect((await call()).body.code).toBe('ledger_unavailable')
    expect(mocks.aiText).not.toHaveBeenCalled()
  })
  it('cannot overwrite a peer edit made during generation', async () => {
    mocks.aiText.mockImplementation(async () => { fixture.state.letter!.content = 'A peer edit'; return 'Old reply' })
    expect((await call()).body.code).toBe('conflict')
    expect(fixture.state.letter!.ai_reply_text).toBeNull()
    expect(fixture.state.writes).toBe(0)
    expect(fixture.state.claims.size).toBe(0)
  })
  it('reports a preflight outage without claiming that the database migration is missing', async () => {
    fixture.state.rpcUnavailable = true
    expect((await call()).body.code).toBe('data_unavailable')
    expect(mocks.aiText).not.toHaveBeenCalled()
    expect(fixture.state.claims.size).toBe(0)
  })
  it('retains an uncertain write claim and recovers a committed reply on retry', async () => {
    fixture.state.writeResponseLost = true
    expect((await call()).body.code).toBe('result_unconfirmed')
    expect(fixture.state.claims.size).toBe(1)
    expect((await call()).body.reply).toBe('A thoughtful saved reply.')
    expect(mocks.aiText).toHaveBeenCalledOnce()
  })
  it('retains an uncertain claim without regenerating when no reply is yet visible', async () => {
    fixture.state.writeNeverConfirmed = true
    expect((await call()).body.code).toBe('result_unconfirmed')
    fixture.state.writeNeverConfirmed = false
    expect((await call()).body.code).toBe('in_flight')
    expect(mocks.aiText).toHaveBeenCalledOnce()
  })
  it('keeps the authoritative reply if history or ledger completion fails', async () => {
    fixture.state.historyFailed = true
    fixture.state.completionFailed = true
    expect((await call()).body.code).toBe('result_unconfirmed')
    expect(fixture.state.letter!.ai_reply_text).toBe('A thoughtful saved reply.')
    expect((await call()).response.status).toBe(200)
    expect(mocks.aiText).toHaveBeenCalledOnce()
  })
  it('discloses partial context and follows faith preference, preserving the legacy cookie response', async () => {
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'cookie' })
    context.deenOn = true
    context.unavailableSources = ['health']
    fixture.state.historyFailed = true
    const result = await call({ letterId })
    expect(result.response.status).toBe(200)
    expect(result.body).toMatchObject({ reply: 'A thoughtful saved reply.', context_partial: true, history_saved: false })
    expect(mocks.aiText.mock.calls[0][2]).toContain('enabled faith mode')
  })
  it('refuses inaccessible rows, incomplete delivery metadata, empty replies and sanitized provider errors', async () => {
    fixture.state.letter!.user_id = 'other'
    expect((await call()).response.status).toBe(404)
    fixture.state.letter = { ...original(), delivered_at: '2026-10-10T23:00:00Z' }
    expect((await call()).response.status).toBe(409)
    fixture.state.letter = original()
    mocks.aiText.mockResolvedValueOnce(' ')
    expect((await call()).response.status).toBe(502)
    mocks.aiText.mockRejectedValueOnce(new AiError('private credential', 401))
    const failure = await call()
    expect(failure.response.status).toBe(503)
    expect(JSON.stringify(failure.body)).not.toContain('credential')
    expect(fixture.state.writes).toBe(0)
  })
})
