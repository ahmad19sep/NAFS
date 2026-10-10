import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'
import { POST } from './route'
import { ApiRouteError } from '@/lib/api-errors'
import { AiError, CLOUDFLARE_AI_MODEL } from '@/lib/cloudflare-ai'

const mocks = vi.hoisted(() => ({ info: vi.fn(), authenticate: vi.fn(), aiText: vi.fn() }))
vi.mock('@/lib/supabase/request', () => ({ authenticateRequest: mocks.authenticate }))
vi.mock('@/lib/ai', async () => {
  const { AiError } = await vi.importActual<typeof import('@/lib/cloudflare-ai')>('@/lib/cloudflare-ai')
  return { aiText: mocks.aiText, AiError, aiProviderInfo: mocks.info }
})

const userId = 'verified-user'
const checkinId = '12345678-1234-1234-1234-123456789abc'
const reviewDate = '2026-10-10'
const savedVersion = '2026-10-10T09:00:00.123456+00:00'
const requestBody = () => ({
  userId: 'attacker-chosen-user', date: reviewDate, eveningText: 'Finished my main priority.',
  tasks: [{ text: 'Read', done: true }], checkin_id: checkinId,
  expected_updated_at: savedVersion, request_id: 'stable-review-request',
})

function database() {
  const state = {
    checkin: { id: checkinId, user_id: userId, date: reviewDate, tasks: [{ text: 'Read', done: true }], evening_text: 'Finished my main priority.', ai_verdict: null as string | null, updated_at: savedVersion },
    profile: { timezone: 'Asia/Karachi', deen_enabled: false },
    claims: new Map<string, any>(), reports: [] as any[], queries: [] as any[],
    ledgerUnavailable: false, completionUnavailable: false, historyUnavailable: false, contextUnavailable: false,
    checkinWrites: 0,
  }
  const db = { from: (table: string) => {
    const call = { table, mode: 'select', filters: [] as [string, unknown][], value: null as any }
    state.queries.push(call)
    const matches = (row: any) => call.filters.every(([field, value]) => row?.[field] === value)
    const run = () => {
      if (table === 'users') return { data: matches({ id: userId }) ? state.profile : null, error: null }
      if (table === 'daily_checkins') {
        if (!matches(state.checkin)) return { data: null, error: null }
        if (call.mode === 'update') { state.checkin = { ...state.checkin, ...call.value }; state.checkinWrites++ }
        return { data: structuredClone(state.checkin), error: null }
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
      if (table === 'ai_reports') {
        if (state.historyUnavailable) throw new Error('Private history database details')
        state.reports.push(call.value)
        return { data: null, error: null }
      }
      if (table === 'habit_logs' || table === 'goals') return {
        data: table === 'habit_logs' ? [{ completed: true, habits: { name: 'Walk' } }] : [],
        error: state.contextUnavailable ? { code: 'network' } : null,
      }
      throw new Error(`Unexpected table ${table}`)
    }
    const query: any = {
      select: () => query,
      eq: (field: string, value: unknown) => { call.filters.push([field, value]); return query },
      limit: () => query,
      insert: (value: unknown) => { call.mode = 'insert'; call.value = value; return query },
      update: (value: unknown) => { call.mode = 'update'; call.value = value; return query },
      delete: () => { call.mode = 'delete'; return query },
      single: async () => run(), maybeSingle: async () => run(),
      then: (resolve: (result: unknown) => unknown, reject: (error: unknown) => unknown) => Promise.resolve().then(run).then(resolve, reject),
    }
    return query
  } }
  return { state, db }
}

async function call(body: unknown = requestBody()) {
  const response = await POST(new NextRequest('https://nafs.example/api/ai/evening-verdict', {
    method: 'POST', headers: { authorization: 'Bearer verified-token', 'content-type': 'application/json' }, body: JSON.stringify(body),
  }))
  return { response, body: await response.json() }
}

describe('authenticated evening review writes', () => {
  let fixture: ReturnType<typeof database>
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.info.mockReturnValue({ provider: 'cloudflare', model: '@cf/openai/gpt-oss-20b' })
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-10T12:00:00Z'))
    fixture = database()
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'bearer' })
    mocks.aiText.mockResolvedValue('A useful saved review.')
  })
  afterEach(() => vi.useRealTimers())

  it('saves under the verified user, uses recorded context, and retains exact version precision', async () => {
    const { response, body } = await call()
    expect(response.status).toBe(200)
    expect(response.headers.get('cache-control')).toBe('no-store')
    expect(body).toMatchObject({ verdict: 'A useful saved review.', replayed: false, model: CLOUDFLARE_AI_MODEL, history_saved: true })
    expect(body.checkin).toMatchObject({ user_id: userId, ai_verdict: body.verdict, tasks: requestBody().tasks })
    expect(fixture.state.checkinWrites).toBe(1)
    const write = fixture.state.queries.find(q => q.table === 'daily_checkins' && q.mode === 'update')
    expect(write.filters).toEqual([['user_id', userId], ['id', checkinId], ['date', reviewDate], ['updated_at', savedVersion]])
    expect(Object.keys(write.value).sort()).toEqual(['ai_verdict', 'updated_at'])
    expect(fixture.state.reports[0]).toMatchObject({ user_id: userId, model_used: CLOUDFLARE_AI_MODEL })
    const prompt = JSON.parse(mocks.aiText.mock.calls[0][1])
    expect(prompt).toMatchObject({ completedPriorities: 1, recordedHabitLogs: 1, completedHabitLogs: 1 })
    expect(mocks.aiText.mock.calls[0][2]).toContain('Keep this review secular')
    expect(JSON.stringify(fixture.state.queries)).not.toContain('attacker-chosen-user')
  })

  it('preserves Gemini provider metadata across generation and replay', async () => {
    mocks.info.mockReturnValue({ provider: 'gemini', model: 'gemini-3.8-flash' })
    const first = await call(), retry = await call()
    expect(first.response.status).toBe(200)
    expect(first.body).toMatchObject({ provider: 'gemini', model: 'gemini-3.8-flash' })
    expect(retry.body).toMatchObject({ provider: 'gemini', model: 'gemini-3.8-flash', replayed: true })
    expect(mocks.aiText).toHaveBeenCalledOnce()
    expect(fixture.state.reports[0].model_used).toBe('gemini-3.8-flash')
  })

  it('replays a dropped response without a second generation or write', async () => {
    const original = await call()
    const retry = await call()
    expect(retry.response.status).toBe(200)
    expect(retry.body).toEqual({ ...original.body, replayed: true })
    expect(mocks.aiText).toHaveBeenCalledOnce()
    expect(fixture.state.checkinWrites).toBe(1)
  })

  it('rejects an old replay after another device edits the saved check-in', async () => {
    await call()
    fixture.state.checkin.updated_at = '2026-10-10T13:00:00Z'
    const { response, body } = await call()
    expect(response.status).toBe(409)
    expect(body.code).toBe('conflict')
    expect(mocks.aiText).toHaveBeenCalledOnce()
  })

  it.each([
    { expected_updated_at: '2026-10-10T09:00:00.123457+00:00' },
    { eveningText: 'An unsaved draft' },
    { tasks: [{ text: 'Read', done: false }] },
    { date: '2026-10-09' },
    { checkin_id: '22345678-1234-1234-1234-123456789abc' },
  ])('rejects stale, unsaved, historical, or inaccessible check-ins before AI generation', async change => {
    const { response } = await call({ ...requestBody(), ...change })
    expect(response.status).toBe(409)
    expect(mocks.aiText).not.toHaveBeenCalled()
    expect(fixture.state.checkinWrites).toBe(0)
  })

  it('does not overwrite a peer edit made while the AI provider is running', async () => {
    mocks.aiText.mockImplementation(async () => {
      fixture.state.checkin.updated_at = '2026-10-10T11:00:00Z'
      fixture.state.checkin.evening_text = 'New notes from another device'
      return 'Review of old notes'
    })
    expect((await call()).response.status).toBe(409)
    expect(fixture.state.checkin.evening_text).toBe('New notes from another device')
    expect(fixture.state.checkin.ai_verdict).toBeNull()
    expect(fixture.state.checkinWrites).toBe(0)
  })

  it('fails closed when authentication, durable claims, or coaching context are unavailable', async () => {
    mocks.authenticate.mockRejectedValueOnce(new ApiRouteError(401, 'unauthorized', 'Sign in again.'))
    expect((await call()).response.status).toBe(401)
    fixture.state.ledgerUnavailable = true
    expect((await call()).body.code).toBe('ledger_unavailable')
    fixture.state.ledgerUnavailable = false
    fixture.state.contextUnavailable = true
    expect((await call()).body.code).toBe('data_unavailable')
    expect(mocks.aiText).not.toHaveBeenCalled()
  })

  it('retains an authoritative review when secondary history or completion storage fails', async () => {
    fixture.state.historyUnavailable = true
    expect((await call()).body.history_saved).toBe(false)
    expect((await call()).body.replayed).toBe(true)
    expect(mocks.aiText).toHaveBeenCalledOnce()
    fixture = database()
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'bearer' })
    fixture.state.completionUnavailable = true
    expect((await call()).body.code).toBe('result_unconfirmed')
    expect(fixture.state.checkin.ai_verdict).toBe('A useful saved review.')
    expect((await call()).body.code).toBe('in_flight')
    expect(mocks.aiText).toHaveBeenCalledTimes(2)
  })

  it('requires the saved contract for bearer clients but preserves the cookie response', async () => {
    const legacy = { date: reviewDate, eveningText: 'Legacy notes', tasks: [] }
    expect((await call(legacy)).response.status).toBe(400)
    mocks.authenticate.mockResolvedValue({ supabase: fixture.db, user: { id: userId }, mode: 'cookie' })
    fixture.state.profile.deen_enabled = true
    const { response, body } = await call(legacy)
    expect(response.status).toBe(200)
    expect(body.verdict).toBe('A useful saved review.')
    expect(fixture.state.checkinWrites).toBe(0)
    expect(mocks.aiText.mock.calls[0][2]).toContain('faith mode is enabled')
  })

  it.each([
    { date: '2026-02-30' }, { tasks: [{ text: 'Read', done: 'yes' }] },
    { eveningText: ' ' }, { eveningText: 'x'.repeat(10001) }, { request_id: undefined },
  ])('rejects malformed review input before generation', async change => {
    expect((await call({ ...requestBody(), ...change })).response.status).toBe(400)
    expect(mocks.aiText).not.toHaveBeenCalled()
  })

  it('sanitizes provider failures and refuses empty provider responses', async () => {
    mocks.aiText.mockRejectedValueOnce(new Error('secret provider token'))
    const failure = await call()
    expect(failure.response.status).toBe(503)
    expect(JSON.stringify(failure.body)).not.toContain('secret')
    mocks.aiText.mockResolvedValueOnce(' ')
    expect((await call()).response.status).toBe(502)
    expect(fixture.state.checkinWrites).toBe(0)
  })

  it.each([[429, 429], [401, 503], [503, 503]])('maps provider status %i without exposing provider credentials or refreshing the user session', async (providerStatus, expectedStatus) => {
    mocks.aiText.mockRejectedValueOnce(new AiError('private provider credential details', providerStatus))
    const { response, body } = await call()
    expect(response.status).toBe(expectedStatus)
    expect(body.code).toBe('ai_unavailable')
    expect(JSON.stringify(body)).not.toContain('credential')
    expect(fixture.state.checkinWrites).toBe(0)
  })
})
