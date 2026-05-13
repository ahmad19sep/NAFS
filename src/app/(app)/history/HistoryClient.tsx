'use client'

import { useMemo, useState, useEffect } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { ChevronLeft } from 'lucide-react'
import { cn } from '@/lib/utils'
import HistoryGraph from '@/components/HistoryGraph'
import DayDetailPanel from '@/components/DayDetailPanel'
import {
  computeHabitsHistory, computeDeenHistory, computeChallengesHistory,
  computeHealthHistory,
  computeDailyTasksHistory, computeWeeklyTasksHistory, computeMonthlyTasksHistory,
  combineDayScores, deltaVsPrev,
  type DayScore,
} from '@/lib/history'
import type { Habit, HabitLog } from '@/types'
import type { Task, TaskType } from '@/lib/tasks'

interface Props {
  today: string
  habits: any[]
  habitLogs30: any[]
  prayerLogs30: any[]
  challenges: any[]
  challengeCheckins30: any[]
  tasks: Task[]
  healthLogs30: any[]
}

type FeatureKey = 'overall' | 'habits' | 'deen' | 'challenges' | 'health' | 'tasks'

const FEATURES: { key: FeatureKey; label: string; emoji: string; accent: 'gold' | 'teal' | 'emerald' | 'pink' | 'red' | undefined }[] = [
  { key: 'overall',    label: 'Overall',    emoji: '✨', accent: undefined },
  { key: 'habits',     label: 'Habits',     emoji: '🔄', accent: 'teal' },
  { key: 'deen',       label: 'Deen',       emoji: '🕌', accent: 'gold' },
  { key: 'challenges', label: 'Challenges', emoji: '🎯', accent: 'pink' },
  { key: 'health',     label: 'Health',     emoji: '❤️', accent: 'red' },
  { key: 'tasks',      label: 'Tasks',      emoji: '✅', accent: 'emerald' },
]

const VALID_TABS: FeatureKey[] = ['overall', 'habits', 'deen', 'challenges', 'health', 'tasks']
const VALID_PERIODS: TaskType[] = ['daily', 'weekly', 'monthly']

export default function HistoryPageClient({
  today, habits, habitLogs30, prayerLogs30,
  challenges, challengeCheckins30, tasks, healthLogs30,
}: Props) {
  const searchParams = useSearchParams()
  const initialTab = (searchParams.get('tab') as FeatureKey) ?? 'overall'
  const initialPeriod = (searchParams.get('period') as TaskType) ?? 'daily'

  const [tab, setTab] = useState<FeatureKey>(
    VALID_TABS.includes(initialTab) ? initialTab : 'overall'
  )
  const [taskPeriod, setTaskPeriod] = useState<TaskType>(
    VALID_PERIODS.includes(initialPeriod) ? initialPeriod : 'daily'
  )
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  // Tabs in URL → re-sync if user navigates back/forward
  useEffect(() => {
    const t = (searchParams.get('tab') as FeatureKey) ?? 'overall'
    if (VALID_TABS.includes(t)) setTab(t)
    const p = (searchParams.get('period') as TaskType) ?? 'daily'
    if (VALID_PERIODS.includes(p)) setTaskPeriod(p)
    setSelectedDate(null)
  }, [searchParams])

  // Per-feature 30-day arrays
  const habitsHistory     = useMemo(() => computeHabitsHistory(habits as Habit[], habitLogs30 as HabitLog[], today), [habits, habitLogs30, today])
  const deenHistory       = useMemo(() => computeDeenHistory(prayerLogs30, today), [prayerLogs30, today])
  const challengesHistory = useMemo(() => computeChallengesHistory(challenges, challengeCheckins30, today), [challenges, challengeCheckins30, today])
  const healthHistory     = useMemo(() => computeHealthHistory(healthLogs30, today), [healthLogs30, today])

  const dailyTasks   = useMemo(() => tasks.filter((t) => t.type === 'daily'),   [tasks])
  const weeklyTasks  = useMemo(() => tasks.filter((t) => t.type === 'weekly'),  [tasks])
  const monthlyTasks = useMemo(() => tasks.filter((t) => t.type === 'monthly'), [tasks])

  const dailyTasksHist   = useMemo(() => computeDailyTasksHistory(dailyTasks, today),       [dailyTasks, today])
  const weeklyTasksHist  = useMemo(() => computeWeeklyTasksHistory(weeklyTasks, today, 12), [weeklyTasks, today])
  const monthlyTasksHist = useMemo(() => computeMonthlyTasksHistory(monthlyTasks, today, 6), [monthlyTasks, today])

  const overallHistory = useMemo(
    () => combineDayScores([habitsHistory, deenHistory, challengesHistory, healthHistory, dailyTasksHist]),
    [habitsHistory, deenHistory, challengesHistory, healthHistory, dailyTasksHist]
  )

  // What chart to show?
  const days: DayScore[] = (() => {
    if (tab === 'tasks') {
      if (taskPeriod === 'weekly')  return weeklyTasksHist
      if (taskPeriod === 'monthly') return monthlyTasksHist
      return dailyTasksHist
    }
    return ({
      overall: overallHistory, habits: habitsHistory, deen: deenHistory,
      challenges: challengesHistory, health: healthHistory,
    } as Record<FeatureKey, DayScore[]>)[tab]
  })()

  const selected = selectedDate ? days.find((d) => d.date === selectedDate) : null
  const delta = selectedDate ? deltaVsPrev(days, selectedDate) : null

  function selectTab(t: FeatureKey) { setTab(t); setSelectedDate(null) }
  function selectPeriod(p: TaskType) { setTaskPeriod(p); setSelectedDate(null) }

  // Stats
  const valid = days.filter((d) => d.max > 0)
  const avg = valid.length > 0 ? Math.round(valid.reduce((s, d) => s + d.pct, 0) / valid.length) : 0
  const best = valid.length > 0 ? Math.max(...valid.map((d) => d.pct)) : 0
  const perfectDays = valid.filter((d) => d.pct === 100).length

  const goodStreak = (() => {
    let cur = 0
    for (let i = days.length - 1; i >= 0; i--) {
      const d = days[i]; if (d.max === 0) continue
      if (d.pct >= 50) cur++; else break
    }
    return cur
  })()

  const headTitle = (() => {
    const f = FEATURES.find((x) => x.key === tab)!.label
    if (tab === 'tasks') {
      const span = taskPeriod === 'daily' ? 'last 30 days'
                 : taskPeriod === 'weekly' ? 'last 12 weeks' : 'last 6 months'
      return `${f} · ${taskPeriod} · ${span}`
    }
    return `${f} · last 30 days`
  })()

  const todayAnchor = tab === 'tasks'
    ? (taskPeriod === 'daily' ? today : days[days.length - 1]?.date ?? today)
    : today

  return (
    <div className="mx-auto max-w-md px-4 space-y-5 pb-8">
      {/* Header */}
      <div className="pt-3 flex items-center gap-3">
        <Link href="/dashboard"
          className="h-9 w-9 rounded-xl border border-white/10 bg-white/5 flex items-center justify-center hover:bg-white/10 transition-colors">
          <ChevronLeft size={16} className="text-muted-foreground" />
        </Link>
        <div className="flex-1">
          <p className="text-xs text-muted-foreground">Trends</p>
          <h1 className="text-2xl font-bold text-foreground">History</h1>
        </div>
      </div>

      {/* Feature tabs */}
      <div className="-mx-4 px-4 overflow-x-auto scrollbar-hide">
        <div className="flex gap-1.5 min-w-max">
          {FEATURES.map((f) => (
            <button key={f.key} onClick={() => selectTab(f.key)}
              className={cn('rounded-xl border px-3.5 py-2 text-sm font-semibold transition-all whitespace-nowrap flex items-center gap-1.5',
                tab === f.key
                  ? 'border-gold/50 bg-gold/10 text-gold'
                  : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
              )}>
              <span>{f.emoji}</span>{f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Task period sub-toggle */}
      {tab === 'tasks' && (
        <div className="grid grid-cols-3 gap-2">
          {(['daily', 'weekly', 'monthly'] as TaskType[]).map((p) => (
            <button key={p} onClick={() => selectPeriod(p)}
              className={cn('rounded-lg border py-2 text-xs font-semibold transition-all capitalize',
                taskPeriod === p
                  ? 'border-emerald-400/50 bg-emerald-500/10 text-emerald-300'
                  : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
              )}>
              {p}
            </button>
          ))}
        </div>
      )}

      {/* Stat tiles */}
      <div className="grid grid-cols-4 gap-2">
        <StatTile label="Avg" value={`${avg}%`} tone="gold" />
        <StatTile label="Best" value={`${best}%`} tone="emerald" />
        <StatTile label="Perfect" value={`${perfectDays}`} tone="emerald" />
        <StatTile label="Streak" value={`${goodStreak}`} tone="orange" />
      </div>

      {/* Graph */}
      <HistoryGraph
        days={days}
        selectedDate={selectedDate}
        onSelect={setSelectedDate}
        today={todayAnchor}
        title={headTitle}
        accent={tab === 'tasks' ? 'emerald' : FEATURES.find((f) => f.key === tab)!.accent}
      />

      {selected && (
        <DayDetailPanel
          selected={selected}
          prevPct={delta?.prevPct}
          today={todayAnchor}
          onClose={() => setSelectedDate(null)}
        >
          {tab === 'overall' ? (
            <div className="space-y-1.5 text-xs">
              <BreakdownRow emoji="🕌" label="Deen"       data={deenHistory.find((d) => d.date === selected.date)} />
              <BreakdownRow emoji="✅" label="Tasks"      data={dailyTasksHist.find((d) => d.date === selected.date)} />
              <BreakdownRow emoji="🔄" label="Habits"     data={habitsHistory.find((d) => d.date === selected.date)} />
              <BreakdownRow emoji="🎯" label="Challenges" data={challengesHistory.find((d) => d.date === selected.date)} />
              <BreakdownRow emoji="❤️" label="Health"     data={healthHistory.find((d) => d.date === selected.date)} />
            </div>
          ) : tab === 'tasks' ? (
            <TasksDayDetail date={selected.date} tasks={
              taskPeriod === 'daily' ? dailyTasks
              : taskPeriod === 'weekly' ? weeklyTasks
              : monthlyTasks
            } />
          ) : null}
        </DayDetailPanel>
      )}

      {/* Per-feature mini summary (only on overall tab) */}
      {tab === 'overall' && (
        <div>
          <p className="section-header mb-3">By feature</p>
          <div className="space-y-2">
            {FEATURES.filter((f) => f.key !== 'overall').map((f) => {
              const d = ({
                habits: habitsHistory, deen: deenHistory, challenges: challengesHistory,
                health: healthHistory, tasks: dailyTasksHist,
              } as Record<string, DayScore[]>)[f.key]
              const v = d.filter((x) => x.max > 0)
              const fAvg = v.length > 0 ? Math.round(v.reduce((s, x) => s + x.pct, 0) / v.length) : 0
              return (
                <button key={f.key} onClick={() => selectTab(f.key)}
                  className="w-full nafs-card p-3 flex items-center gap-3 hover:bg-white/8 transition-all active:scale-[0.99] text-left">
                  <div className="h-9 w-9 rounded-xl bg-white/8 flex items-center justify-center text-base">{f.emoji}</div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-foreground">{f.label}</p>
                    <p className="text-[10px] text-muted-foreground">avg {fAvg}% · {v.length} days tracked</p>
                  </div>
                  <div className="flex items-end gap-[2px] h-7 w-20">
                    {d.map((day, i) => (
                      <div key={i}
                        className={cn('flex-1 rounded-sm',
                          day.pct === 0 ? 'bg-white/8'
                          : day.pct < 50 ? 'bg-orange-500/60'
                          : day.pct < 75 ? 'bg-gold/70'
                          : 'bg-emerald-400'
                        )}
                        style={{ height: `${day.pct === 0 ? 2 : Math.max(4, (day.pct / 100) * 26)}px` }} />
                    ))}
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

function StatTile({ label, value, tone }: { label: string; value: string; tone: 'gold' | 'emerald' | 'orange' }) {
  const colors = { gold: 'text-gold', emerald: 'text-emerald-400', orange: 'text-orange-400' }
  return (
    <div className="rounded-xl border border-white/10 bg-white/5 p-2.5 text-center">
      <p className={cn('text-lg font-bold tabular-nums leading-tight', colors[tone])}>{value}</p>
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
    </div>
  )
}

function BreakdownRow({ emoji, label, data }: {
  emoji: string; label: string; data?: { pct: number; earned: number; max: number }
}) {
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="text-sm w-5">{emoji}</span>
      <span className="flex-1 text-foreground">{label}</span>
      {data && data.max > 0 ? (
        <>
          <span className="tabular-nums text-muted-foreground">{data.earned}/{data.max}</span>
          <span className={cn('tabular-nums font-semibold w-10 text-right',
            data.pct >= 75 ? 'text-emerald-400' : data.pct >= 50 ? 'text-gold' : data.pct > 0 ? 'text-orange-400' : 'text-muted-foreground'
          )}>{data.pct}%</span>
        </>
      ) : (
        <span className="text-muted-foreground/50 text-[10px]">no data</span>
      )}
    </div>
  )
}

function TasksDayDetail({ date, tasks }: { date: string; tasks: Task[] }) {
  const inPeriod = tasks.filter((t) => t.period_date === date)
  if (inPeriod.length === 0)
    return <p className="text-xs text-muted-foreground italic">No tasks for this period.</p>
  return (
    <div className="space-y-1.5 text-xs">
      {inPeriod.map((t) => (
        <div key={t.id} className="flex items-center gap-2">
          <span className={cn('h-2 w-2 rounded-full',
            t.priority === 'high' ? 'bg-red-500' : t.priority === 'medium' ? 'bg-gold' : 'bg-white/30'
          )} />
          <span className={cn('flex-1 truncate',
            t.status === 'completed' ? 'text-emerald-400 line-through' : 'text-foreground'
          )}>{t.title}</span>
          <span className={cn('text-[10px]',
            t.status === 'completed' ? 'text-emerald-400' : 'text-red-400'
          )}>{t.status === 'completed' ? '✓ done' : '✗ missed'}</span>
        </div>
      ))}
    </div>
  )
}
