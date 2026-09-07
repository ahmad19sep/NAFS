import { describe, it, expect } from 'vitest'
import {
  canonicalJson, hashPayload, isValidRequestId, decideFromExisting,
} from './idempotency'

/**
 * LOG-01 request-identifier fixtures.
 *
 * The failure being prevented: a client sends "create this task", the
 * connection drops before the response arrives, the client retries, and there
 * are now two identical tasks.
 */

describe('canonical payloads — the same intent always hashes the same', () => {
  it('ignores key order', () => {
    // JSON.stringify preserves insertion order, so without this an honest
    // retry that built its body differently would look like a new payload and
    // be rejected as a conflict.
    expect(canonicalJson({ a: 1, b: 2 })).toBe(canonicalJson({ b: 2, a: 1 }))
    expect(hashPayload({ a: 1, b: 2 })).toBe(hashPayload({ b: 2, a: 1 }))
  })

  it('sorts nested objects too', () => {
    expect(hashPayload({ x: { p: 1, q: 2 }, y: 3 }))
      .toBe(hashPayload({ y: 3, x: { q: 2, p: 1 } }))
  })

  it('treats an undefined field as absent', () => {
    expect(hashPayload({ a: 1 })).toBe(hashPayload({ a: 1, b: undefined }))
  })

  it('keeps array order significant', () => {
    // Order carries meaning in a list; it is not noise like key order.
    expect(hashPayload({ xs: [1, 2] })).not.toBe(hashPayload({ xs: [2, 1] }))
  })

  it('separates genuinely different content', () => {
    expect(hashPayload({ title: 'Read' })).not.toBe(hashPayload({ title: 'Rest' }))
    expect(hashPayload({ n: 1 })).not.toBe(hashPayload({ n: '1' }))
    expect(hashPayload({ a: null })).not.toBe(hashPayload({ a: 0 }))
  })

  it('handles null and primitives without throwing', () => {
    expect(() => hashPayload(null)).not.toThrow()
    expect(() => hashPayload('x')).not.toThrow()
    expect(() => hashPayload([])).not.toThrow()
  })
})

describe('request ids', () => {
  it('accepts an opaque id of reasonable length', () => {
    expect(isValidRequestId('r-abc12345')).toBe(true)
    expect(isValidRequestId(crypto.randomUUID())).toBe(true)
  })

  it('rejects anything too short, absent or not a string', () => {
    // Short ids collide, and a collision here means one user's write returning
    // another's result.
    expect(isValidRequestId('short')).toBe(false)
    expect(isValidRequestId(undefined)).toBe(false)
    expect(isValidRequestId(null)).toBe(false)
    expect(isValidRequestId(12345678)).toBe(false)
    expect(isValidRequestId('x'.repeat(500))).toBe(false)
  })
})

describe('deciding what a repeat request means', () => {
  const hash = hashPayload({ title: 'Read Quran' })

  it('claims when nothing has been recorded', () => {
    expect(decideFromExisting(null, hash)).toEqual({ kind: 'claimed' })
  })

  it('replays the stored result for an identical retry', () => {
    const existing = { payload_hash: hash, result: { id: 't1' } }
    expect(decideFromExisting(existing, hash)).toEqual({ kind: 'replay', result: { id: 't1' } })
  })

  it('reports in-flight when the first attempt has not finished', () => {
    // Not a replay: there is no result to return yet. Answering "in flight" is
    // honest; inventing a result or doing the work again is not.
    expect(decideFromExisting({ payload_hash: hash, result: null }, hash))
      .toEqual({ kind: 'in_flight' })
  })

  it('calls a reused id with different content a conflict, not a second write', () => {
    const existing = { payload_hash: hashPayload({ title: 'Something else' }), result: { id: 't1' } }
    expect(decideFromExisting(existing, hash)).toEqual({ kind: 'conflict' })
  })

  it('does not mistake a conflict for a replay just because a result exists', () => {
    // The dangerous confusion: returning the WRONG task because the id matched
    // but the payload did not.
    const existing = { payload_hash: 'different', result: { id: 'wrong-task' } }
    expect(decideFromExisting(existing, hash).kind).toBe('conflict')
  })
})
