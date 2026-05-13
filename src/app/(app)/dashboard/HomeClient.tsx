'use client'

import Link from 'next/link'
import { Settings, ChevronRight, Sparkles } from 'lucide-react'
import { cn, scoreColor } from '@/lib/utils'
import { PRAYERS } from '@/lib/scoring'
import type { Habit, HabitLog, Weekday } from '@/types'

const WEEKDAY_CODES: Weekday[] = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']
function todayWeekday(): Weekday {
  return WEEKDAY_CODES[new Date().getDay()]
}
function isHabitScheduledToday(h: Habit): boolean {
  if (h.is_paused) return false
  if (h.schedule_kind === 'weekdays') return (h.schedule_days ?? []).includes(todayWeekday())
  return true
}
function isHabitDoneToday(h: Habit, log?: HabitLog): boolean {
  if (!log?.completed) return false
  if (h.type === 'counter') return log.value >= h.target_value
  if (h.type === 'duration') return log.duration_mins >= h.time_target_mins
  return true
}

interface Props {
  profile: any
  habits: any[]
  habitLogs: any[]
  prayerLog: any
  challenges: any[]
  checkin: any
  goals: any[]
  aiReports: any[]
  healthLog: any
  today: string
}

function getGreeting() {
  const h = new Date().getHours()
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  if (h < 21) return 'Good evening'
  return 'Good night'
}

export default function HomeClient({
  profile, habits, habitLogs, prayerLog, challenges, checkin, goals, aiReports, healthLog,
}: Props) {

  // --- Compute all stats ---
  // New prayer scoring: 0=missed, 1=alone (1pt), 2=jamat (2pts), max = 5*2 = 10 + extras
  const prayerValues = PRAYERS.map((p) => (prayerLog?.[p.toLowerCase()] ?? 0) as number)
  const extras = (prayerLog?.extra_prayers ?? []) as { name: string; status: number }[]
  const prayerDone = prayerValues.reduce((s, v) => s + v, 0) + extras.reduce((s, e) => s + (e.status ?? 0), 0)
  const prayerTotal = 5 * 2 + extras.length * 2

  const tasks: { text: string; done: boolean }[] = checkin?.tasks ?? []
  const tasksDone = tasks.filter((t) => t.done).length
  const tasksTotal = tasks.length

  const scheduledHabits = (habits as Habit[]).filter((h) => isHabitScheduledToday(h))
  const habitsDone = scheduledHabits.filter((h) =>
    isHabitDoneToday(h, (habitLogs as HabitLog[]).find((l) => l.habit_id === h.id))
  ).length
  const habitsTotal = scheduledHabits.length

  // Challenges done today
  const challengesDone = challenges.filter((c: any) =>
    c.challenge_checkins?.some((ci: any) => ci.date === new Date().toISOString().split('T')[0] && ci.completed)
  ).length
  const challengesTotal = challenges.length

  // Goals milestone progress
  const allMilestones = goals.flatMap((g: any) => g.goal_milestones ?? [])
  const milestonesDone = allMilestones.filter((m: any) => m.done).length
  const milestonesTotal = allMilestones.length

  // Health (placeholder counters from health_logs)
  const healthTargets = [
    healthLog?.water_glasses ?? 0,
    healthLog?.exercise_done ? 1 : 0,
    healthLog?.sleep_hours ? 1 : 0,
    healthLog?.steps ? 1 : 0,
  ]
  const healthDone = healthTargets.filter((v) => v > 0).length
  const healthTotal = 4

  // Overall score (weighted)
  const sections = [
    { earned: prayerDone, max: prayerTotal },
    { earned: tasksDone, max: Math.max(tasksTotal, 1) },
    { earned: habitsDone, max: Math.max(habitsTotal, 1) },
    { earned: challengesDone, max: Math.max(challengesTotal, 1) },
    { earned: healthDone, max: healthTotal },
  ]
  const totalEarned = sections.reduce((s, x) => s + (x.earned / x.max), 0)
  const overallScore = Math.round((totalEarned / sections.length) * 100)

  const insights = aiReports.length

  // --- Render ---
  return (
    <div className="mx-auto max-w-md space-y-5 px-4 pb-8">

      {/* Header */}
      <div className="flex items-start justify-between pt-4">
        <div>
          <h1 className="text-2xl font-bold text-foreground">
            {getGreeting()}, {(profile?.name ?? 'Friend').split(' ')[0]} 👋
          </h1>
          <p className="text-xs text-muted-foreground mt-1">
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
          </p>
        </div>
        <Link href="/profile">
          <div className="h-10 w-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all">
            <Settings size={18} className="text-muted-foreground" />
          </div>
        </Link>
      </div>

      {/* Today's score card */}
      <div className="nafs-card p-5">
        <div className="flex items-center gap-5">
          {/* Score ring */}
          <div className="relative flex-shrink-0">
            <svg width="92" height="92" style={{ transform: 'rotate(-90deg)' }}>
              <circle cx="46" cy="46" r="38" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="9" />
              <circle cx="46" cy="46" r="38" fill="none"
                stroke={overallScore >= 80 ? '#34d399' : overallScore >= 60 ? '#fbbf24' : overallScore >= 40 ? '#fb923c' : '#fb923c'}
                strokeWidth="9" strokeLinecap="round"
                strokeDasharray={239}
                strokeDashoffset={239 * (1 - overallScore / 100)}
                style={{ transition: 'stroke-dashoffset 1s ease', filter: 'drop-shadow(0 0 8px currentColor)' }}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn('text-xl font-bold tabular-nums', scoreColor(overallScore))}>
                {overallScore}%
              </span>
              <span className="text-[10px] uppercase tracking-widest text-muted-foreground">today</span>
            </div>
          </div>

          {/* Breakdown */}
          <div className="flex-1 space-y-1.5 text-sm">
            <Row emoji="🕌" label="Prayers" earned={prayerDone} max={prayerTotal} />
            <Row emoji="✅" label="Tasks" earned={tasksDone} max={tasksTotal} />
            <Row emoji="🔄" label="Habits" earned={habitsDone} max={habitsTotal} />
            <Row emoji="🎯" label="Challenges" earned={challengesDone} max={challengesTotal} />
            <p className="text-xs text-muted-foreground/60 pt-0.5">+2 more</p>
          </div>
        </div>
      </div>

      {/* FEATURES section */}
      <div>
        <p className="section-header mb-3">FEATURES</p>
        <div className="grid grid-cols-2 gap-3">
          <FeatureCard href="/checkin" emoji="✅" bg="from-emerald-500/20 to-emerald-700/10" iconBg="bg-emerald-500/30"
            title="Daily tasks" sub={`${tasksDone} / ${Math.max(tasksTotal, 1)} done`} pct={tasksTotal ? (tasksDone / tasksTotal) * 100 : 0} barColor="bg-emerald-400" />

          <FeatureCard href="/habits" emoji="🔄" bg="from-cyan-500/20 to-blue-700/10" iconBg="bg-cyan-500/30"
            title="Habits" sub={`${habitsDone} / ${Math.max(habitsTotal, 1)} done`} pct={habitsTotal ? (habitsDone / habitsTotal) * 100 : 0} barColor="bg-cyan-400" />

          <FeatureCard href="/challenges" emoji="🎯" bg="from-pink-500/20 to-rose-700/10" iconBg="bg-pink-500/30"
            title="Challenges" sub={`${challengesDone} / ${Math.max(challengesTotal, 1)} today`} pct={challengesTotal ? (challengesDone / challengesTotal) * 100 : 0} barColor="bg-pink-400" />

          <FeatureCard href="/deen" emoji="🕌" bg="from-yellow-500/20 to-amber-700/10" iconBg="bg-amber-500/30"
            title="Deen" sub={`${prayerDone} / ${prayerTotal} prayers`} pct={(prayerDone / prayerTotal) * 100} barColor="bg-emerald-400" />

          <FeatureCard href="/health" emoji="❤️" bg="from-red-500/20 to-pink-700/10" iconBg="bg-red-500/30"
            title="Health" sub={`${healthDone} / ${healthTotal} logged`} pct={(healthDone / healthTotal) * 100} barColor="bg-pink-400" />

          <FeatureCard href="/goals" emoji="🏆" bg="from-yellow-500/20 to-orange-700/10" iconBg="bg-yellow-500/30"
            title="Goals" sub={`${milestonesDone} / ${Math.max(milestonesTotal, 1)} milestones`} pct={milestonesTotal ? (milestonesDone / milestonesTotal) * 100 : 0} barColor="bg-yellow-400" />
        </div>
      </div>

      {/* AI analysis & reminders */}
      <Link href="/coach">
        <div className="nafs-card p-4 flex items-center gap-3 hover:bg-white/8 transition-all active:scale-[0.98]">
          <div className="h-11 w-11 rounded-2xl bg-gradient-to-br from-fuchsia-500/30 to-purple-700/20 flex items-center justify-center text-2xl flex-shrink-0">
            🧠
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-foreground text-sm">AI analysis &amp; reminders</p>
            <p className="text-xs text-muted-foreground mt-0.5">
              {insights} new insight{insights !== 1 ? 's' : ''} · {tasks.filter((t) => !t.done).length} reminder{tasks.filter((t) => !t.done).length !== 1 ? 's' : ''}
            </p>
          </div>
          <ChevronRight size={18} className="text-muted-foreground flex-shrink-0" />
        </div>
      </Link>
    </div>
  )
}

function Row({ emoji, label, earned, max }: { emoji: string; label: string; earned: number; max: number }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm w-4">{emoji}</span>
      <span className="flex-1 text-foreground">{label}</span>
      <span className="tabular-nums text-muted-foreground text-xs">
        {earned}/{max}
      </span>
    </div>
  )
}

function FeatureCard({
  href, emoji, bg, iconBg, title, sub, pct, barColor,
}: {
  href: string
  emoji: string
  bg: string
  iconBg: string
  title: string
  sub: string
  pct: number
  barColor: string
}) {
  return (
    <Link href={href}>
      <div className={cn(
        'rounded-2xl border border-white/8 bg-gradient-to-br p-4 transition-all active:scale-95 hover:border-white/20',
        bg
      )}>
        {/* Top row */}
        <div className="flex items-center justify-between mb-3">
          <div className={cn('h-10 w-10 rounded-xl flex items-center justify-center text-xl', iconBg)}>
            {emoji}
          </div>
          <ChevronRight size={14} className="text-muted-foreground/60" />
        </div>

        {/* Title + sub */}
        <p className="font-bold text-foreground">{title}</p>
        <p className="text-xs text-muted-foreground mt-0.5">{sub}</p>

        {/* Progress bar */}
        <div className="mt-3 h-1.5 w-full rounded-full bg-white/10">
          <div className={cn('h-full rounded-full transition-all', barColor)}
            style={{ width: `${Math.min(100, Math.max(0, pct))}%` }} />
        </div>
      </div>
    </Link>
  )
}
