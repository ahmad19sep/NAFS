import { describe, it, expect } from 'vitest'
import { validate, parseJson, type Schema } from './schema'

/**
 * Fixtures A01 and A02 from the NAFS Improvement Blueprint: model output must
 * be checked before anything uses it, and invalid output must fail visibly
 * rather than becoming a plausible-looking object.
 */

const HABIT: Schema = {
  kind: 'object',
  optional: ['target_value'],
  fields: {
    name: { kind: 'string', minLength: 2, maxLength: 80 },
    type: { kind: 'enum', values: ['simple', 'counter', 'duration'] },
    target_value: { kind: 'number', min: 1, max: 1000 },
  },
}

describe('A01 — prose, fences and malformed JSON', () => {
  it('accepts a plain JSON object', () => {
    const r = parseJson('{"name":"Walk","type":"simple"}')
    expect(r.ok).toBe(true)
  })

  it('strips one complete markdown fence, which models add routinely', () => {
    const r = parseJson('```json\n{"name":"Walk","type":"simple"}\n```')
    expect(r.ok).toBe(true)
    if (r.ok) expect(r.value).toEqual({ name: 'Walk', type: 'simple' })
  })

  it('rejects prose wrapped around JSON instead of guessing at the braces', () => {
    // The old safeParseJSON sliced from the first { to the last }, which turns
    // commentary into a confident object. Malformed output must fail so the
    // caller can retry, not be silently "repaired".
    const r = parseJson('Sure! Here is the plan: {"name":"Walk","type":"simple"} Hope that helps.')
    expect(r.ok).toBe(false)
  })

  it('rejects truncated JSON', () => {
    expect(parseJson('{"name":"Walk","type":').ok).toBe(false)
  })

  it('rejects an empty reply', () => {
    expect(parseJson('').ok).toBe(false)
    expect(parseJson('   ').ok).toBe(false)
  })
})

describe('A02 — JSON that parses but is wrong', () => {
  it('rejects a value outside the allowed enum', () => {
    const r = validate({ name: 'Walk', type: 'invented' }, HABIT)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toMatch(/type/)
  })

  it('rejects a string where a number belongs', () => {
    const r = validate({ name: 'Water', type: 'counter', target_value: '8' }, HABIT)
    expect(r.ok).toBe(false)
  })

  it('rejects a missing required field', () => {
    const r = validate({ type: 'simple' }, HABIT)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toMatch(/name.*missing/)
  })

  it('rejects numbers outside their range', () => {
    expect(validate({ name: 'Water', type: 'counter', target_value: 0 }, HABIT).ok).toBe(false)
    expect(validate({ name: 'Water', type: 'counter', target_value: 99999 }, HABIT).ok).toBe(false)
  })

  it('rejects NaN and Infinity, which pass a typeof check', () => {
    expect(validate({ name: 'W', type: 'counter', target_value: NaN }, HABIT).ok).toBe(false)
    expect(validate({ name: 'W', type: 'counter', target_value: Infinity }, HABIT).ok).toBe(false)
  })

  it('rejects null where an object is required', () => {
    expect(validate(null, HABIT).ok).toBe(false)
  })

  it('rejects an array where an object is required', () => {
    expect(validate([{ name: 'Walk', type: 'simple' }], HABIT).ok).toBe(false)
  })

  it('accepts valid output, and omitting an optional field', () => {
    expect(validate({ name: 'Walk', type: 'simple' }, HABIT).ok).toBe(true)
    expect(validate({ name: 'Water', type: 'counter', target_value: 8 }, HABIT).ok).toBe(true)
  })

  it('tolerates unknown extra keys rather than discarding good output', () => {
    const r = validate({ name: 'Walk', type: 'simple', commentary: 'hope this helps' }, HABIT)
    expect(r.ok).toBe(true)
  })

  it('validates every element of an array', () => {
    const list: Schema = { kind: 'array', of: HABIT }
    const r = validate([{ name: 'Walk', type: 'simple' }, { name: 'X', type: 'nope' }], list)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toMatch(/\[1\]/)
  })

  it('caps how many errors it reports, so a retry prompt stays small', () => {
    const wide: Schema = {
      kind: 'object',
      fields: Object.fromEntries(
        Array.from({ length: 20 }, (_, i) => [`f${i}`, { kind: 'string' } as Schema]),
      ),
    }
    const r = validate({}, wide)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.length).toBeLessThanOrEqual(8)
  })
})
