import { combineDayScores, computeChallengesHistory, computeDailyTasksHistory, computeDeenHistory, computeHabitsHistory, computeHealthHistory, type DayScore } from './history'
import type { ReportRows } from './report-data'

export type CoverageDay = DayScore & { coverage: 'recorded' | 'pending' | 'unrecorded' | 'unknown' }

/** Preserve feature calculations while keeping absence and failed reads out of measured totals. */
export function nativeHistory(rows: ReportRows, today: string, deenEnabled: boolean) {
  const missing = new Set(rows.unavailable ?? [])
  const annotate = (scores: DayScore[], dates: Set<string>, sources: string[]): CoverageDay[] => scores.map(day => ({
    ...day, coverage: sources.some(source => missing.has(source)) ? 'unknown'
      : dates.has(day.date) ? 'recorded' : day.date === today ? 'pending' : 'unrecorded',
  }))
  const dates = (items: { date: string }[]) => new Set(items.map(item => item.date))
  const dailyTasks = rows.tasks.filter(task => !task.deleted_at && task.type === 'daily')
  const history = {
    habits: annotate(computeHabitsHistory(rows.habits, rows.habitLogs, today), dates(rows.habitLogs), ['habits', 'habitLogs']),
    deen: deenEnabled ? annotate(computeDeenHistory(rows.prayerLogs, today), dates(rows.prayerLogs), ['prayerLogs']) : [],
    tasks: annotate(computeDailyTasksHistory(dailyTasks, today), new Set(dailyTasks.map(task => task.period_date)), ['tasks']),
    health: annotate(computeHealthHistory(rows.healthLogs, today), dates(rows.healthLogs), ['healthLogs']),
    challenges: annotate(computeChallengesHistory(rows.challenges, rows.challengeCheckins, today), dates(rows.challengeCheckins), ['challenges', 'challengeCheckins']),
  }
  const lists = Object.values(history).filter(list => list.length > 0)
  const overall = combineDayScores(lists.map(list => list.map(day => day.coverage === 'recorded' ? day : { ...day, earned: 0, max: 0, pct: 0 })))
    .map((day, index): CoverageDay => ({ ...day, coverage: lists.some(list => list[index].coverage === 'recorded' && list[index].max > 0) ? 'recorded'
      : lists.some(list => list[index].coverage === 'unknown') ? 'unknown' : day.date === today ? 'pending' : 'unrecorded' }))
  return { overall, ...history }
}
