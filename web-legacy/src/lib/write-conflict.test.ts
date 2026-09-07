import { describe, it, expect } from 'vitest'
import { classifyWrite, isDuplicateKeyError } from './write-conflict'

/**
 * LOG-01 / D09. Two devices editing the same day must not silently overwrite
 * each other, and — the part that is easy to get wrong — "no rows matched"
 * must never be read as a successful write.
 */

describe('a guarded update that matched nothing is a conflict', () => {
  it('treats a matched row as saved', () => {
    expect(classifyWrite(null, true)).toEqual({ kind: 'saved' })
  })

  it('treats no matching row as a conflict, not success', () => {
    // The version moved between load and save. Postgres reports this as zero
    // rows updated and no error, so reading it as success is the whole bug.
    expect(classifyWrite(null, false)).toEqual({ kind: 'conflict' })
  })

  it('never reports saved when nothing was written', () => {
    expect(classifyWrite(null, false).kind).not.toBe('saved')
  })
})

describe('an insert colliding on the day is the same situation', () => {
  it('reads a duplicate key on insert as a conflict', () => {
    const err = { code: '23505', message: 'duplicate key value violates unique constraint' }
    expect(classifyWrite(err, false, true)).toEqual({ kind: 'conflict' })
  })

  it('does not turn every insert error into a conflict', () => {
    expect(classifyWrite({ message: 'permission denied' }, false, true)).toEqual({ kind: 'failed' })
  })

  it('does not read a duplicate key on an update as a conflict', () => {
    // On an update path a duplicate key means something else is wrong; only
    // the insert path can legitimately collide on (user_id, date).
    const err = { code: '23505', message: 'duplicate key value violates unique constraint' }
    expect(classifyWrite(err, false, false)).toEqual({ kind: 'failed' })
  })
})

describe('recognising a unique violation', () => {
  it('matches on SQLSTATE', () => {
    expect(isDuplicateKeyError({ code: '23505' })).toBe(true)
  })

  it('matches on the message when no code is supplied', () => {
    // PostgREST does not always surface the code, so the message is a fallback.
    expect(isDuplicateKeyError({ message: 'duplicate key value violates unique constraint' })).toBe(true)
    expect(isDuplicateKeyError({ message: 'relation already exists' })).toBe(true)
  })

  it('does not match unrelated failures', () => {
    expect(isDuplicateKeyError({ message: 'permission denied for table health_logs' })).toBe(false)
    expect(isDuplicateKeyError({ code: '42501', message: 'insufficient privilege' })).toBe(false)
    expect(isDuplicateKeyError(null)).toBe(false)
    expect(isDuplicateKeyError({})).toBe(false)
  })
})
