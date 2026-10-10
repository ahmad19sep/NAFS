import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest, NextResponse } from 'next/server'
import { ApiRouteError } from '@/lib/api-errors'

const mock = vi.hoisted(() => ({ auth: vi.fn(), claim: vi.fn(), generate: vi.fn() }))
vi.mock('@/lib/supabase/request', () => ({ authenticateRequest: mock.auth }))
vi.mock('@/lib/strict-idempotency', () => ({ withStrictIdempotency: mock.claim }))
vi.mock('../habit-starter/route', () => ({ POST: mock.generate }))
vi.mock('../challenge-starter/route', () => ({ POST: mock.generate }))
vi.mock('../goal-starter/route', () => ({ POST: mock.generate }))
vi.mock('../goal-alignment/route', () => ({ POST: mock.generate }))
vi.mock('../goal-plan/route', () => ({ POST: mock.generate }))
vi.mock('../health-recommend/route', () => ({ POST: mock.generate }))
vi.mock('../report-review/route', () => ({ POST: mock.generate }))
vi.mock('../../screentime/analyze/route', () => ({ POST: mock.generate }))
import { POST } from './route'
const body = { requestId: '4bba5193-5969-4f0c-8e39-31e9ef2d0bdb', action: 'habit-starter', payload: { habitId: 'owned-habit' } }
const request = (value: unknown) => new NextRequest('https://nafs.example/api/ai/native', { method: 'POST', headers: { authorization: 'Bearer test-token' }, body: JSON.stringify(value) })
beforeEach(() => {
  vi.clearAllMocks()
  mock.auth.mockResolvedValue({ user: { id: 'verified-owner' }, supabase: {} })
  mock.claim.mockImplementation(async (_db, _user, _id, _payload, work) => ({ result: await work(), replayed: false }))
  mock.generate.mockResolvedValue(NextResponse.json({ starter: { how_to_succeed: 'Small steps' } }))
})
describe('native advice gateway', () => {
  it('uses verified identity for its durable claim and forwards the same auth header', async () => {
    const response = await POST(request({ ...body, userId: 'foreign' }))
    expect(response.status).toBe(200)
    expect(mock.claim.mock.calls[0][1]).toBe('verified-owner')
    expect(mock.generate.mock.calls[0][0].headers.get('authorization')).toBe('Bearer test-token')
    expect(await mock.generate.mock.calls[0][0].json()).toEqual(body.payload)
  })
  it('rejects unknown actions and array payloads before claiming or generating', async () => {
    expect((await POST(request({ ...body, action: 'delete-user' }))).status).toBe(400)
    expect((await POST(request({ ...body, payload: [] }))).status).toBe(400)
    expect(mock.claim).not.toHaveBeenCalled()
  })
  it('does no provider work for an unverified caller', async () => {
    mock.auth.mockRejectedValue(new ApiRouteError(401, 'unauthorized', 'Sign in.'))
    expect((await POST(request(body))).status).toBe(401)
    expect(mock.generate).not.toHaveBeenCalled()
  })
  it('returns a durable replay without invoking the handler', async () => {
    mock.claim.mockResolvedValue({ result: { starter: {} }, replayed: true })
    expect(await (await POST(request(body))).json()).toMatchObject({ replayed: true })
    expect(mock.generate).not.toHaveBeenCalled()
  })
  it('retains uncertainty after a handler that may have written', async () => {
    mock.generate.mockResolvedValue(NextResponse.json({ error: 'Sensitive server detail' }, { status: 500 }))
    const response = await POST(request(body))
    expect(response.status).toBe(503)
    expect(await response.json()).toMatchObject({ code: 'result_unconfirmed' })
  })
  it('allows retry after explicit provider failure before any feature write', async () => {
    mock.generate.mockResolvedValue(NextResponse.json({ code: 'generation_failed', error: 'Provider unavailable' }, { status: 502 }))
    expect(await (await POST(request(body))).json()).toMatchObject({ code: 'unavailable' })
  })
  it('does not retain a claim for a failed read-only goal-plan generation', async () => {
    mock.generate.mockResolvedValue(NextResponse.json({ error: 'Provider error' }, { status: 500 }))
    expect(await (await POST(request({ ...body, action: 'goal-plan' }))).json()).toMatchObject({ code: 'unavailable' })
  })
})
