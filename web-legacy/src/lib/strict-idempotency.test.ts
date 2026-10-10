import { describe, expect, it, vi } from 'vitest'
import { withStrictIdempotency } from './strict-idempotency'
import { hashPayload } from './idempotency'
import { ApiRouteError } from './api-errors'

function ledger(options: { claimError?: unknown; existing?: unknown; completionError?: unknown; completionMissing?: boolean; completionThrows?: boolean } = {}) {
  const calls: { operation: string; filters: [string, unknown][]; value?: any }[] = []
  const db = { from: (table: string) => {
    expect(table).toBe('mutations')
    let operation = ''
    const call = { operation, filters: [] as [string, unknown][], value: undefined as any }
    const query = {
      insert: (value: unknown) => { calls.push({ operation: 'insert', filters: [], value }); return Promise.resolve({ error: options.claimError ?? null }) },
      select: () => { if (!operation) { operation = 'select'; call.operation = operation; calls.push(call) } return query },
      update: (value: unknown) => { operation = 'update'; call.operation = operation; call.value = value; calls.push(call); return query },
      delete: () => { operation = 'delete'; call.operation = operation; calls.push(call); return query },
      eq: (field: string, value: unknown) => { call.filters.push([field, value]); return query },
      maybeSingle: async () => {
        if (operation === 'select') return { data: options.existing ?? null, error: null }
        if (options.completionThrows) throw new Error('Network failure after save')
        return { data: options.completionMissing ? null : { request_id: 'request-123' }, error: options.completionError ?? null }
      },
      then: (resolve: (result: unknown) => unknown) => Promise.resolve({ error: null }).then(resolve),
    }
    return query
  } }
  return { db, calls }
}

const user = 'verified-user', requestId = 'request-123', payload = { operation: 'review', notes: 'Saved notes' }

describe('durable AI generation claims', () => {
  it('does not generate when the ledger is unavailable or request id is invalid', async () => {
    const work = vi.fn()
    const { db } = ledger({ claimError: { code: '42P01', message: 'missing table' } })
    await expect(withStrictIdempotency(db, user, requestId, payload, work)).rejects.toMatchObject({ status: 503, code: 'ledger_unavailable' })
    await expect(withStrictIdempotency(db, user, 'short', payload, work)).rejects.toMatchObject({ status: 400 })
    expect(work).not.toHaveBeenCalled()
  })

  it('claims and completes once under the verified user', async () => {
    const { db, calls } = ledger()
    const result = { verdict: 'A saved review' }
    const work = vi.fn().mockResolvedValue(result)
    expect(await withStrictIdempotency(db, user, requestId, payload, work)).toEqual({ result, replayed: false })
    expect(work).toHaveBeenCalledOnce()
    expect(calls[0].value).toEqual({ user_id: user, request_id: requestId, payload_hash: hashPayload(payload) })
    expect(calls[1].filters).toEqual([['user_id', user], ['request_id', requestId]])
    expect(calls[1].value.result).toEqual(result)
  })

  it('replays a completed identical request without another generation', async () => {
    const result = { verdict: 'Original review' }
    const { db, calls } = ledger({ claimError: { code: '23505' }, existing: { payload_hash: hashPayload(payload), result } })
    const work = vi.fn()
    expect(await withStrictIdempotency(db, user, requestId, payload, work)).toEqual({ result, replayed: true })
    expect(work).not.toHaveBeenCalled()
    expect(calls[1].filters).toEqual([['user_id', user], ['request_id', requestId]])
  })

  it.each([
    [{ payload_hash: 'different', result: { verdict: 'Other review' } }, 'conflict', 409],
    [{ payload_hash: hashPayload(payload), result: null }, 'in_flight', 409],
    [null, 'ledger_unavailable', 503],
  ])('never generates for a conflicting, unfinished, or missing existing claim', async (existing, code, status) => {
    const { db } = ledger({ claimError: { code: '23505' }, existing })
    const work = vi.fn()
    await expect(withStrictIdempotency(db, user, requestId, payload, work)).rejects.toMatchObject({ code, status })
    expect(work).not.toHaveBeenCalled()
  })

  it('releases a failed generation claim so the exact intent may be retried', async () => {
    const { db, calls } = ledger()
    const failure = new Error('Provider unavailable before save')
    await expect(withStrictIdempotency(db, user, requestId, payload, async () => { throw failure })).rejects.toBe(failure)
    expect(calls[1]).toMatchObject({ operation: 'delete', filters: [['user_id', user], ['request_id', requestId]] })
  })

  it('retains the claim when a write may have committed before its response failed', async () => {
    const { db, calls } = ledger()
    const failure = new ApiRouteError(503, 'result_unconfirmed', 'Reload saved data.')
    await expect(withStrictIdempotency(db, user, requestId, payload, async () => { throw failure })).rejects.toBe(failure)
    expect(calls.some(call => call.operation === 'delete')).toBe(false)
  })

  it.each([{ completionError: { code: 'network' } }, { completionMissing: true }, { completionThrows: true }])(
    'retains the claim when a saved result cannot be confirmed in the ledger', async options => {
      const { db, calls } = ledger(options)
      const work = vi.fn().mockResolvedValue({ verdict: 'Already saved' })
      await expect(withStrictIdempotency(db, user, requestId, payload, work)).rejects.toMatchObject({ status: 503, code: 'result_unconfirmed' })
      expect(work).toHaveBeenCalledOnce()
      expect(calls.some(call => call.operation === 'delete')).toBe(false)
    },
  )
})
