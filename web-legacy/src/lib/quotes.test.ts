import { describe, it, expect } from 'vitest'
import { moodFor, pickQuote, allQuotes } from './quotes'

describe('the curated list is sound', () => {
  it('has text and a reference on every entry', () => {
    for (const q of allQuotes()) {
      expect(q.text.trim().length).toBeGreaterThan(10)
      expect(q.source.trim().length).toBeGreaterThan(3)
    }
  })

  it('covers every mood in both modes, so no state is left silent', () => {
    for (const faith of [true, false]) {
      for (const mood of ['struggling', 'steady', 'strong'] as const) {
        expect(allQuotes().some((q) => q.mood === mood && q.faith === faith)).toBe(true)
      }
    }
  })

  it('cites a chapter and verse for every Quran entry', () => {
    for (const q of allQuotes()) {
      if (q.source.startsWith('Quran')) expect(q.source).toMatch(/^Quran \d+:\d+$/)
    }
  })
})

describe('reading the day', () => {
  it('treats a broken streak as struggling whatever the score', () => {
    expect(moodFor({ score: 90, streakJustBroken: true })).toBe('struggling')
  })

  it('passes no verdict on a day with nothing logged yet', () => {
    expect(moodFor({ score: null })).toBe('steady')
    expect(moodFor({ score: 0, nothingLoggedYet: true })).toBe('steady')
  })

  it('reads a low score as struggling and a high one as strong', () => {
    expect(moodFor({ score: 20 })).toBe('struggling')
    expect(moodFor({ score: 55 })).toBe('steady')
    expect(moodFor({ score: 80 })).toBe('strong')
  })
})

describe('picking a line', () => {
  it('holds still within a day', () => {
    const a = pickQuote('steady', true, '2026-09-07')
    for (let i = 0; i < 20; i++) expect(pickQuote('steady', true, '2026-09-07')).toEqual(a)
  })

  it('rotates across days', () => {
    const seen = new Set<string>()
    for (let d = 1; d <= 28; d++) {
      const q = pickQuote('steady', true, `2026-09-${String(d).padStart(2, '0')}`)
      if (q) seen.add(q.text)
    }
    expect(seen.size).toBeGreaterThan(1)
  })

  it('respects faith mode', () => {
    const faith = pickQuote('struggling', true, '2026-09-07')!
    const secular = pickQuote('struggling', false, '2026-09-07')!
    expect(faith.source).toMatch(/Quran|Muslim|Bukhari|Hakim/)
    expect(secular.source).not.toMatch(/Quran|Muslim|Bukhari|Hakim/)
  })

  it('never returns an entry from the wrong mood', () => {
    for (let d = 1; d <= 28; d++) {
      const q = pickQuote('strong', true, `2026-09-${String(d).padStart(2, '0')}`)!
      const entry = allQuotes().find((e) => e.text === q.text)!
      expect(entry.mood).toBe('strong')
    }
  })
})
