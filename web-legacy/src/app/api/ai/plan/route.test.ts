import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'
import { POST } from './route'
import { POST as chatPOST } from '../plan-from-chat/route'
import { ApiRouteError } from '@/lib/api-errors'
import { checkedRecovery } from '@/lib/recovery-planning'
import type { RecoveryPlan } from '@/lib/plan'

const mocks = vi.hoisted(() => ({ auth: vi.fn(), ai: vi.fn(), context: vi.fn() }))
vi.mock('@/lib/supabase/request', () => ({ authenticateRequest: mocks.auth }))
vi.mock('@/lib/coach-context', () => ({ buildCoachContext: mocks.context }))
vi.mock('@/lib/ai', () => ({ aiStructured: mocks.ai }))
const user = 'verified-user'
const chatId = '00000000-0000-4000-8000-000000000001'
const version = '2026-10-10T12:00:00.123456+00:00'
const plan = (): RecoveryPlan => ({ problem: 'Too much evening scrolling', approach: 'Move the phone out of reach and start with a small replacement.',
  steps: [{ kind: 'task', title: 'Move the charger', emoji: '🔌', reason: 'Move the trigger away from where you relax.', task: { priority: 'high' } }] })
const intent = () => ({ intent: 'I keep scrolling at night', mode: 'overcome', request_id: 'stable-plan-request', user_id: 'attacker' })
const pointer = () => ({ conversation_id: chatId, expected_updated_at: version, request_id: 'stable-chat-plan',
  messages: [{ role: 'user', content: 'Attacker supplied text' }], user_id: 'attacker' })

function database() {
  const state = { chat: { id: chatId, user_id: user, updated_at: version,
    messages: Array.from({ length: 16 }, (_, i) => ({ role: i % 2 ? 'assistant' : 'user', content: `Full saved message ${i}` })) } as any,
    claims: new Map<string, any>(), queries: [] as any[], ledgerMissing: false, completionFailed: false, chatError: null as any }
  const db = { from: (table: string) => {
    const call = { table, mode: 'select', filters: [] as [string, unknown][], value: null as any }
    state.queries.push(call)
    const matches = (row: any) => call.filters.every(([field, value]) => row?.[field] === value)
    const run = () => {
      if (table === 'ai_conversations') return { data: state.chat && matches(state.chat) ? structuredClone(state.chat) : null, error: state.chatError }
      if (table !== 'mutations') throw new Error(`Unexpected write to ${table}`)
      if (state.ledgerMissing) return { data: null, error: { code: '42P01' } }
      if (call.mode === 'insert') {
        if (state.claims.has(call.value.request_id)) return { data: null, error: { code: '23505' } }
        state.claims.set(call.value.request_id, { ...call.value, result: null })
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
    const query: any = { select: () => query,
      eq: (field: string, value: unknown) => { call.filters.push([field, value]); return query },
      insert: (value: any) => { call.mode = 'insert'; call.value = value; return query },
      update: (value: any) => { call.mode = 'update'; call.value = value; return query },
      delete: () => { call.mode = 'delete'; return query }, maybeSingle: async () => run(),
      then: (resolve: any, reject: any) => Promise.resolve().then(run).then(resolve, reject) }
    return query
  } }
  return { state, db }
}
async function call(body: unknown = intent(), chat = false, raw = false) {
  const response = await (chat ? chatPOST : POST)(new NextRequest(`https://nafs.example/api/ai/${chat ? 'plan-from-chat' : 'plan'}`, {
    method: 'POST', headers: { authorization: 'Bearer verified-token' }, body: raw ? body as string : JSON.stringify(body) }))
  return { response, body: await response.json() }
}

describe('recovery preview routes', () => {
  let fixture: ReturnType<typeof database>
  let context: any
  beforeEach(() => {
    vi.resetAllMocks()
    fixture = database()
    context = { profile: { deen_enabled: false }, deenOn: false, today: '2026-10-10', unavailableSources: [],
      context: { last_30_days: { habits: [{ name: 'Reading' }], repeated_misses: ['Reading missed twice'],
        phone_screen_time: { minutes: 90 }, health: { sleep: { recorded_nights: 2 } } }, coach_memory: ['Evenings are hard'] } }
    mocks.auth.mockResolvedValue({ user: { id: user }, supabase: fixture.db, mode: 'bearer' })
    mocks.context.mockImplementation(async () => context)
    mocks.ai.mockResolvedValue({ ok: true, data: plan(), provider: 'cloudflare' })
  })
  it('returns a preview with verified context and creates no feature items', async () => {
    const { response, body } = await call()
    expect(response.status).toBe(200)
    expect(response.headers.get('cache-control')).toBe('no-store')
    expect(body).toMatchObject({ recovery: plan(), request_id: intent().request_id, provider: 'cloudflare', context_partial: false, replayed: false, source_date: '2026-10-10' })
    expect(mocks.context).toHaveBeenCalledWith(fixture.db, user)
    const prompt = JSON.parse(mocks.ai.mock.calls[0][0])
    expect(prompt.recorded_context).toMatchObject({ existing_habits: context.context.last_30_days.habits,
      phone_screen_time: { minutes: 90 }, sleep: { recorded_nights: 2 }, coach_memory: ['Evenings are hard'] })
    expect(prompt.subject).toEqual({ intent: intent().intent })
    expect(mocks.ai.mock.calls[0][2]).toContain('Keep the plan secular')
    expect(fixture.state.queries.every(q => q.table === 'mutations')).toBe(true)
    expect(fixture.state.claims.get(intent().request_id).user_id).toBe(user)
  })
  it('replays a stable request without another provider call and refuses altered intent', async () => {
    await call()
    expect((await call()).body.replayed).toBe(true)
    expect((await call({ ...intent(), intent: 'A different problem' })).response.status).toBe(409)
    expect(mocks.ai).toHaveBeenCalledOnce()
  })
  it('preserves validated Gemini plans on idempotent replay', async () => {
    mocks.ai.mockResolvedValueOnce({ ok: true, data: plan(), provider: 'gemini', fellBack: false })
    const first = await call(), retry = await call()
    expect(first.response.status).toBe(200)
    expect(first.body).toMatchObject({ provider: 'gemini', fellBack: false })
    expect(retry.body).toMatchObject({ provider: 'gemini', replayed: true })
    expect(mocks.ai).toHaveBeenCalledOnce()
  })
  it('claims generation before the provider and refuses a simultaneous retry', async () => {
    let finish!: (value: unknown) => void
    let start!: () => void
    const started = new Promise<void>(resolve => { start = resolve })
    mocks.ai.mockImplementation(() => { start(); return new Promise(resolve => { finish = resolve }) })
    const first = call()
    await started
    expect(fixture.state.claims.size).toBe(1)
    expect((await call()).body.code).toBe('in_flight')
    finish({ ok: true, data: plan() })
    expect((await first).response.status).toBe(200)
    expect(mocks.ai).toHaveBeenCalledOnce()
  })
  it('uses the full owned saved chat instead of supplied messages', async () => {
    mocks.ai.mockResolvedValueOnce({ ok: true, data: plan(), provider: 'anthropic', fellBack: false })
    const result = await call(pointer(), true)
    expect(result.response.status).toBe(200)
    expect(JSON.parse(mocks.ai.mock.calls[0][0]).subject.messages).toEqual(fixture.state.chat.messages)
    expect(mocks.ai.mock.calls[0][3]).toEqual({ deep: true })
    expect(fixture.state.queries.filter(q => q.table === 'ai_conversations').every(q =>
      JSON.stringify(q.filters) === JSON.stringify([['user_id', user], ['id', chatId]]))).toBe(true)
    expect(result.body.provider).toBe('anthropic')
  })
  it.each(['foreign', 'missing', 'stale', 'invalid'])('refuses a %s saved conversation before generation', async condition => {
    if (condition === 'foreign') fixture.state.chat.user_id = 'another-user'
    if (condition === 'missing') fixture.state.chat = null
    if (condition === 'stale') fixture.state.chat.updated_at = '2026-10-10T12:00:00.123457+00:00'
    if (condition === 'invalid') fixture.state.chat.messages[0].role = 'assistant'
    expect((await call(pointer(), true)).response.status).toBe(409)
    expect(mocks.ai).not.toHaveBeenCalled()
  })
  it('refuses a chat edited during generation and does not retain the obsolete preview', async () => {
    mocks.ai.mockImplementationOnce(async () => { fixture.state.chat.updated_at = '2026-10-10T13:00:00Z'; return { ok: true, data: plan() } })
    expect((await call(pointer(), true)).response.status).toBe(409)
    expect(fixture.state.claims.size).toBe(0)
  })
  it('rechecks saved chat on replay, even without another provider call', async () => {
    await call(pointer(), true)
    expect((await call(pointer(), true)).body.replayed).toBe(true)
    fixture.state.chat.updated_at = '2026-10-10T13:00:00Z'
    expect((await call(pointer(), true)).response.status).toBe(409)
    expect(mocks.ai).toHaveBeenCalledOnce()
  })
  it.each([{}, { intent: 'ok', request_id: 'valid-request' }, { ...intent(), request_id: undefined }, null, []])('rejects malformed intent %j', async body => {
    expect((await call(body)).response.status).toBe(400)
    expect(mocks.ai).not.toHaveBeenCalled()
  })
  it('rejects malformed JSON and excessive native requests', async () => {
    expect((await call('{', false, true)).response.status).toBe(400)
    expect((await call('x'.repeat(12001), false, true)).response.status).toBe(413)
    expect((await call('x'.repeat(4001), true, true)).response.status).toBe(413)
  })
  it('requires saved pointers and stable IDs for bearer chat planning', async () => {
    expect((await call({ messages: pointer().messages, request_id: 'valid-request' }, true)).response.status).toBe(400)
    expect((await call({ ...pointer(), request_id: undefined }, true)).response.status).toBe(400)
    expect(mocks.ai).not.toHaveBeenCalled()
  })
  it('fails closed for authentication, profile, version migration, or missing request storage', async () => {
    mocks.auth.mockRejectedValueOnce(new ApiRouteError(401, 'unauthorized', 'Sign in again.'))
    expect((await call()).response.status).toBe(401)
    context.unavailableSources = ['profile']
    expect((await call()).body.code).toBe('data_unavailable')
    context.unavailableSources = []
    fixture.state.chatError = { code: '42703' }
    expect((await call(pointer(), true)).body.code).toBe('not_configured')
    fixture.state.ledgerMissing = true
    expect((await call()).body.code).toBe('ledger_unavailable')
    expect(mocks.ai).not.toHaveBeenCalled()
  })
  it('retains an unfinished completion claim instead of charging again', async () => {
    fixture.state.completionFailed = true
    expect((await call()).body.code).toBe('result_unconfirmed')
    fixture.state.completionFailed = false
    expect((await call()).body.code).toBe('in_flight')
    expect(mocks.ai).toHaveBeenCalledOnce()
  })
  it('discloses partial context and provider fallback, respecting faith preference', async () => {
    context.deenOn = true
    context.unavailableSources = ['health']
    mocks.ai.mockResolvedValueOnce({ ok: true, data: plan(), provider: 'cloudflare', fellBack: true })
    const result = await call()
    expect(result.body).toMatchObject({ context_partial: true, fellBack: true })
    expect(mocks.ai.mock.calls[0][2]).toContain('Faith mode is enabled')
    expect(mocks.ai.mock.calls[0][2]).toContain('Unrecorded is not missed')
  })
  it('sanitizes provider and transport failures and releases failed generation for retry', async () => {
    mocks.ai.mockResolvedValueOnce({ ok: false, code: 'rate_limited', message: 'Private key details' })
    const first = await call()
    expect(first.response.status).toBe(429)
    expect(JSON.stringify(first.body)).not.toContain('Private')
    expect(fixture.state.claims.size).toBe(0)
    mocks.ai.mockRejectedValueOnce(new Error('Private provider details'))
    expect(JSON.stringify((await call()).body)).not.toContain('Private')
    expect((await call()).response.status).toBe(200)
  })
  it('preserves bounded cookie chat and single-item planning contracts', async () => {
    mocks.auth.mockResolvedValue({ user: { id: user }, supabase: fixture.db, mode: 'cookie' })
    const messages = Array.from({ length: 16 }, (_, i) => ({ role: i % 2 ? 'assistant' : 'user', content: 'x'.repeat(4500) }))
    expect((await call({ messages }, true)).response.status).toBe(200)
    const prompt = JSON.parse(mocks.ai.mock.calls[0][0])
    expect(prompt.subject.messages).toHaveLength(12)
    expect(prompt.subject.messages[0].content).toHaveLength(4000)
    mocks.ai.mockResolvedValueOnce({ ok: true, data: plan().steps[0] })
    expect((await call({ intent: 'Move the charger' })).body.proposal).toEqual(plan().steps[0])
    expect(fixture.state.claims.size).toBe(0)
  })
})

describe('usable native recovery steps', () => {
  it('normalizes flattened fields, emoji titles, and duration units', () => {
    const raw = plan()
    raw.steps = [{ ...raw.steps[0], title: '🔌 Move the charger', task: undefined, priority: 'high' } as any,
      { kind: 'habit', title: 'Read a book', emoji: '📖', reason: 'A small replacement for the scrolling slot.', habit: { type: 'duration', target_value: 1, unit: 'hour', schedule_kind: 'daily' } }]
    const checked = checkedRecovery(raw)
    expect(checked.dropped).toBe(0)
    expect(checked.recovery.steps[0].title).toBe('Move the charger')
    expect(checked.recovery.steps[1].habit?.time_target_mins).toBe(60)
  })
  it('drops uncreatable native titles, counter units, and challenge cadence', () => {
    const raw = plan()
    raw.steps.push({ kind: 'habit', title: 'h'.repeat(81), emoji: '📖', reason: 'This title exceeds the habit create limit.', habit: { type: 'simple', schedule_kind: 'daily' } },
      { kind: 'habit', title: 'Count pages', emoji: '📖', reason: 'This target has no concrete counter unit.', habit: { type: 'counter', target_value: 3, schedule_kind: 'daily' } },
      { kind: 'challenge', title: 'Read for a month', emoji: '📖', reason: 'Monthly check-in needs at least thirty days.', challenge: { frequency: 'monthly', duration_days: 14, requires_photo: false } })
    expect(checkedRecovery(raw)).toMatchObject({ dropped: 3, recovery: plan() })
  })
  it.each([null, {}, { ...plan(), steps: [] }, { ...plan(), steps: [{ ...plan().steps[0], title: null }] },
    { ...plan(), steps: [{ kind: 'habit', title: 'Read a book', emoji: '📖', reason: 'A small replacement for scrolling.', type: 'duration', target_value: 1, unit: 123, schedule_kind: 'daily' }] }])('rejects malformed provider data %j', raw => {
    expect(() => checkedRecovery(raw as RecoveryPlan)).toThrow(ApiRouteError)
  })
})
