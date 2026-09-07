import { describe, it, expect } from 'vitest'
import { parseJson, repairStrayObjectQuotes } from './schema'

/**
 * The one malformation the free model produces on longer JSON: an object
 * inside an array wrapped in quotes. These pin that the repair is purely
 * syntactic — it fixes that shape, changes nothing else, and never turns
 * invalid input into a wrong-but-parseable value.
 */

describe('repairStrayObjectQuotes', () => {
  it('repairs the shape observed live: a quote before an array element', () => {
    // Exactly what came back from the Worker, shortened.
    const broken = '{"steps":[{"kind":"task","title":"A"},"{"kind":"habit","title":"B"}]}'
    const fixed = repairStrayObjectQuotes(broken)
    expect(fixed).not.toBeNull()
    expect(JSON.parse(fixed as string)).toEqual({
      steps: [{ kind: 'task', title: 'A' }, { kind: 'habit', title: 'B' }],
    })
  })

  it('repairs a trailing partner quote after the closing brace', () => {
    const broken = '{"steps":[{"a":1},"{"b":2}",{"c":3}]}'
    const fixed = repairStrayObjectQuotes(broken)
    expect(JSON.parse(fixed as string)).toEqual({ steps: [{ a: 1 }, { b: 2 }, { c: 3 }] })
  })

  it('leaves valid JSON alone', () => {
    expect(repairStrayObjectQuotes('{"steps":[{"a":1},{"b":2}]}')).toBeNull()
    expect(repairStrayObjectQuotes('{"a":"plain"}')).toBeNull()
  })

  it('never touches a brace inside a string value', () => {
    const s = '{"note":"use {braces} here","steps":[{"a":1}]}'
    expect(repairStrayObjectQuotes(s)).toBeNull()
  })

  it('does not corrupt a legitimate quoted string that follows a comma', () => {
    // A string element after a comma is normal and must survive untouched.
    const s = '{"list":["one","two"],"steps":[{"a":1}]}'
    expect(repairStrayObjectQuotes(s)).toBeNull()
    expect(JSON.parse(s)).toEqual({ list: ['one', 'two'], steps: [{ a: 1 }] })
  })

  it('respects escaped quotes inside strings', () => {
    const s = '{"note":"he said \\"hi\\"","steps":[{"a":1}]}'
    expect(repairStrayObjectQuotes(s)).toBeNull()
  })
})

describe('parseJson falls back to the repair', () => {
  it('parses the broken shape rather than failing', () => {
    const broken = '{"problem":"x","steps":[{"kind":"task"},"{"kind":"habit"}]}'
    const r = parseJson(broken)
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect((r.value as any).steps).toHaveLength(2)
  })

  it('still reports genuinely broken JSON as broken', () => {
    expect(parseJson('{"a": ').ok).toBe(false)
    expect(parseJson('not json at all').ok).toBe(false)
    expect(parseJson('').ok).toBe(false)
  })

  it('leaves well-formed replies exactly as they were', () => {
    const r = parseJson('{"steps":[{"a":1},{"b":2}]}')
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.value).toEqual({ steps: [{ a: 1 }, { b: 2 }] })
  })
})
