import { describe, expect, it } from 'vitest'
import { nativeHistory } from './native-history'
import type { ReportRows } from './report-data'

const empty: ReportRows = { profile: {}, habits: [], habitLogs: [], prayerLogs: [], tasks: [], healthLogs: [], quranLogs: [], checkins: [], challenges: [], challengeCheckins: [], goals: [] }
describe('native history coverage', () => {
  it('does not score missing prayer and health rows as measured failures', () => {
    const history = nativeHistory(empty, '2026-10-10', true)
    expect(history.overall[0]).toMatchObject({ coverage: 'unrecorded', max: 0 })
    expect(history.overall.at(-1)?.coverage).toBe('pending')
  })
  it('retains a genuinely recorded zero', () => {
    const history = nativeHistory({ ...empty, prayerLogs: [{ date: '2026-10-09', fajr: 0, dhuhr: 0, asr: 0, maghrib: 0, isha: 0 }] }, '2026-10-10', true)
    expect(history.deen.at(-2)).toMatchObject({ coverage: 'recorded', pct: 0, max: 10 })
    expect(history.overall.at(-2)).toMatchObject({ coverage: 'recorded', pct: 0, earned: 1 })
  })
  it('marks unavailable sources unknown and suppresses disabled faith', () => {
    const history = nativeHistory({ ...empty, unavailable: ['healthLogs', 'prayerLogs'] }, '2026-10-10', false)
    expect(history.deen).toEqual([])
    expect(history.overall[0].coverage).toBe('unknown')
  })
  it('does not treat weekly tasks as daily measured opportunities', () => {
    const history = nativeHistory({ ...empty, tasks: [{ type: 'weekly', period_date: '2026-10-05', status: 'completed' }] }, '2026-10-10', false)
    expect(history.tasks.every(day => day.max === 0)).toBe(true)
  })
})
