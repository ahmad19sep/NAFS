import { describe, it, expect } from 'vitest'
import { toAnthropicShape } from './anthropic-ai'

describe('toAnthropicShape', () => {
  it('lifts system messages out and joins them', () => {
    const r = toAnthropicShape([
      { role: 'system', content: 'A' },
      { role: 'system', content: 'B' },
      { role: 'user', content: 'hi' },
    ])
    expect(r.system).toBe('A\n\nB')
    expect(r.messages).toEqual([{ role: 'user', content: 'hi' }])
  })

  it('leaves system undefined when there is none', () => {
    expect(toAnthropicShape([{ role: 'user', content: 'hi' }]).system).toBeUndefined()
  })

  it('merges consecutive same-role turns so roles alternate', () => {
    const r = toAnthropicShape([
      { role: 'user', content: 'one' },
      { role: 'user', content: 'two' },
      { role: 'assistant', content: 'reply' },
      { role: 'user', content: 'three' },
    ])
    expect(r.messages).toEqual([
      { role: 'user', content: 'one\n\ntwo' },
      { role: 'assistant', content: 'reply' },
      { role: 'user', content: 'three' },
    ])
  })

  it('drops a leading assistant turn', () => {
    const r = toAnthropicShape([
      { role: 'assistant', content: 'stale' },
      { role: 'user', content: 'hi' },
    ])
    expect(r.messages).toEqual([{ role: 'user', content: 'hi' }])
  })
})
