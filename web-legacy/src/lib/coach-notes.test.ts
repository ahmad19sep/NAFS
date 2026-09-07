import { describe, it, expect } from 'vitest'
import {
  buildCoachMemory, latestBySubject, memoryIsEmpty, subjectFor, LIFE_TOPICS, type CoachNote,
} from './coach-notes'

const note = (over: Partial<CoachNote> & Pick<CoachNote, 'kind' | 'content' | 'date'>): CoachNote =>
  ({ subject: null, ...over })

describe('buildCoachMemory', () => {
  it('keeps only the latest answer per life question, keyed by the question', () => {
    const m = buildCoachMemory([
      note({ kind: 'life', subject: 'want', content: 'old answer', date: '2026-08-01' }),
      note({ kind: 'life', subject: 'want', content: 'new answer', date: '2026-09-01' }),
      note({ kind: 'life', subject: 'now', content: 'exams', date: '2026-08-15' }),
    ])
    const wantQ = LIFE_TOPICS.find((t) => t.key === 'want')!.question
    expect(m.about_their_life[wantQ]).toEqual({ said: 'new answer', on: '2026-09-01' })
    expect(Object.keys(m.about_their_life)).toHaveLength(2)
  })

  it('groups miss reasons by subject, newest first, labelled and capped', () => {
    const notes: CoachNote[] = ['08-01', '08-02', '08-03', '08-04', '08-05'].map((d, i) =>
      note({ kind: 'miss_reason', subject: 'prayer:fajr', content: `reason ${i}`, date: `2026-${d}` }))
    notes.push(note({ kind: 'miss_reason', subject: 'habit:h1', content: 'busy', date: '2026-08-02' }))

    const m = buildCoachMemory(notes, {
      perSubject: 3,
      labelFor: (s) => (s === 'prayer:fajr' ? 'Fajr' : s === 'habit:h1' ? 'Reading' : s),
    })
    expect(m.reasons_given_for_misses.Fajr.map((r) => r.said)).toEqual(['reason 4', 'reason 3', 'reason 2'])
    expect(m.reasons_given_for_misses.Reading).toEqual([{ on: '2026-08-02', said: 'busy' }])
  })

  it('keeps bad-day notes newest first and capped', () => {
    const m = buildCoachMemory([
      note({ kind: 'low_score', content: 'a', date: '2026-08-01' }),
      note({ kind: 'low_score', content: 'b', date: '2026-08-03' }),
      note({ kind: 'low_score', content: 'c', date: '2026-08-02' }),
    ], { lowScore: 2 })
    expect(m.when_a_day_went_badly.map((x) => x.said)).toEqual(['b', 'c'])
  })

  it('drops blank notes and reports emptiness', () => {
    const m = buildCoachMemory([note({ kind: 'low_score', content: '   ', date: '2026-08-01' })])
    expect(memoryIsEmpty(m)).toBe(true)
    expect(memoryIsEmpty(buildCoachMemory([note({ kind: 'low_score', content: 'x', date: '2026-08-01' })]))).toBe(false)
  })

  it('keeps the user’s words verbatim apart from trimming', () => {
    const m = buildCoachMemory([note({ kind: 'low_score', content: '  slept at 3am, phone  ', date: '2026-08-01' })])
    expect(m.when_a_day_went_badly[0].said).toBe('slept at 3am, phone')
  })
})

describe('latestBySubject', () => {
  it('returns the newest note for each subject of the given kind', () => {
    const latest = latestBySubject([
      note({ kind: 'miss_reason', subject: 'prayer:fajr', content: 'old', date: '2026-08-01' }),
      note({ kind: 'miss_reason', subject: 'prayer:fajr', content: 'new', date: '2026-08-09' }),
      note({ kind: 'life', subject: 'prayer:fajr', content: 'not this kind', date: '2026-08-20' }),
    ], 'miss_reason')
    expect(latest.get('prayer:fajr')?.content).toBe('new')
  })
})

describe('subjectFor', () => {
  it('is stable and distinguishes kinds', () => {
    expect(subjectFor({ kind: 'prayer', id: 'fajr' })).toBe('prayer:fajr')
    expect(subjectFor({ kind: 'habit', id: 'abc' })).toBe('habit:abc')
  })
})
