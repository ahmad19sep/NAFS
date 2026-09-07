import { describe, it, expect } from 'vitest'
import { ageInDays, ageLabel, formatNotesForHandoff, type AppNote } from './app-notes'

const NOW = new Date('2026-09-20T12:00:00.000Z')

const note = (over: Partial<AppNote> & Pick<AppNote, 'id' | 'kind' | 'title' | 'created_at'>): AppNote => ({
  detail: null, where_seen: null, status: 'open', resolved_at: null, ...over,
})

describe('ageInDays / ageLabel', () => {
  it('reads today, yesterday and older', () => {
    expect(ageLabel('2026-09-20T09:00:00.000Z', NOW)).toBe('today')
    expect(ageLabel('2026-09-19T09:00:00.000Z', NOW)).toBe('yesterday')
    expect(ageLabel('2026-09-07T09:00:00.000Z', NOW)).toBe('13 days ago')
    expect(ageInDays('2026-09-07T09:00:00.000Z', NOW)).toBe(13)
  })

  it('never goes negative, and survives a bad date', () => {
    expect(ageInDays('2026-12-01T00:00:00.000Z', NOW)).toBe(0)
    expect(ageInDays('not a date', NOW)).toBe(0)
  })
})

describe('formatNotesForHandoff', () => {
  const notes: AppNote[] = [
    note({ id: '1', kind: 'idea', title: 'Dark mode for the report', created_at: '2026-09-08T10:00:00.000Z' }),
    note({ id: '2', kind: 'bug', title: 'Streak shows 0 after logging', where_seen: 'Home', created_at: '2026-09-15T10:00:00.000Z', detail: 'Only after midnight.\nRefresh fixes it.' }),
    note({ id: '3', kind: 'bug', title: 'Meals sheet will not close', created_at: '2026-09-10T10:00:00.000Z' }),
    note({ id: '4', kind: 'idea', title: 'Already shipped', status: 'done', created_at: '2026-09-01T10:00:00.000Z' }),
  ]

  it('lists bugs before ideas, oldest first inside each', () => {
    const out = formatNotesForHandoff(notes, NOW)
    const order = ['Meals sheet will not close', 'Streak shows 0 after logging', 'Dark mode for the report']
    let at = -1
    for (const title of order) {
      const i = out.indexOf(title)
      expect(i).toBeGreaterThan(at)
      at = i
    }
  })

  it('counts only open items in the header', () => {
    expect(formatNotesForHandoff(notes, NOW)).toContain('3 open items (2 bugs, 1 idea)')
  })

  it('leaves out anything not open', () => {
    expect(formatNotesForHandoff(notes, NOW)).not.toContain('Already shipped')
  })

  it('carries where it was seen, the detail and the age', () => {
    const out = formatNotesForHandoff(notes, NOW)
    expect(out).toContain('[BUG] · Home Streak shows 0 after logging')
    expect(out).toContain('   Only after midnight.')
    expect(out).toContain('   Refresh fixes it.')
    expect(out).toContain('added 5 days ago (2026-09-15)')
  })

  it('says so plainly when there is nothing open', () => {
    expect(formatNotesForHandoff([], NOW)).toBe('No open items.')
    expect(formatNotesForHandoff([notes[3]], NOW)).toBe('No open items.')
  })

  it('uses singular wording for one item', () => {
    expect(formatNotesForHandoff([notes[1]], NOW)).toContain('1 open item (1 bug, 0 ideas)')
  })
})
