'use client'

import { useMemo } from 'react'
import Link from 'next/link'
import { Settings, ChevronRight, Sparkles, TrendingUp, TrendingDown, Minus } from 'lucide-react'
import { cn, scoreColor } from '@/lib/utils'
import { PRAYERS } from '@/lib/scoring'
import type { Habit, HabitLog, Weekday } from '@/types'
import {
  computeHabitsHistory, computeDeenHistory, computeChallengesHistory,
  computeHealthHistory, computeDailyTasksHistory,
  combineDayScores, deltaVsPrev,
} from '@/lib/history'
import type { Task } from '@/lib/tasks'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'

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
  habitLogs30: any[]
  prayerLog: any
  prayerLogs30: any[]
  challenges: any[]
  allChallenges: any[]
  challengeCheckins30: any[]
  todayTasks: Task[]
  tasks30: Task[]
  goals: any[]
  aiReports: any[]
  healthLog: any
  healthLogs30: any[]
  today: string
}

function getGreeting(name: string) {
  const h = new Date().getHours()
  if (h < 5)  return { text: `Up late, ${name}`,        emoji: '🌙' }
  if (h < 12) return { text: `Good morning, ${name}`,   emoji: '☀️' }
  if (h < 17) return { text: `Good afternoon, ${name}`, emoji: '🌤️' }
  if (h < 21) return { text: `Good evening, ${name}`,   emoji: '🌅' }
  return            { text: `Good night, ${name}`,      emoji: '🌙' }
}

function getVerdict(score: number): { text: string; tone: 'great' | 'good' | 'okay' | 'low' } {
  const h = new Date().getHours()
  if (score >= 85) return { text: 'You\'re on fire today — keep this rhythm 🔥', tone: 'great' }
  if (score >= 70) return { text: 'Strong day — finish what you started', tone: 'great' }
  if (score >= 50) return { text: h < 18 ? 'Solid start — push for more' : 'Decent day — close it well', tone: 'good' }
  if (score >= 25) return { text: h < 18 ? 'Still time to climb. Start with one win.' : 'Quiet day. Take a small win before sleep.', tone: 'okay' }
  return { text: h < 18 ? 'The day\'s still yours. Begin somewhere.' : 'Reset. Tomorrow is a new shot.', tone: 'low' }
}

export default function HomeClient({
  profile, habits, habitLogs, habitLogs30, prayerLog, prayerLogs30,
  challenges, allChallenges, challengeCheckins30,
  todayTasks, tasks30, goals, aiReports,
  healthLog, healthLogs30, today,
}: Props) {

  // ---- Today's per-feature stats ----
  const prayerValues = PRAYERS.map((p) => (prayerLog?.[p.toLowerCase()] ?? 0) as number)
  const extras = (prayerLog?.extra_prayers ?? []) as { name: string; status: number }[]
  const prayerDone = prayerValues.reduce((s, v) => s + v, 0) + extras.reduce((s, e) => s + (e.status ?? 0), 0)
  const prayerTotal = 5 * 2 + extras.length * 2

  const tasksDone = todayTasks.filter((t) => t.status === 'completed').length
  const tasksTotal = todayTasks.length

  const scheduledHabits = (habits as Habit[]).filter((h) => isHabitScheduledToday(h))
  const habitsDone = scheduledHabits.filter((h) =>
    isHabitDoneToday(h, (habitLogs as HabitLog[]).find((l) => l.habit_id === h.id))
  ).length
  const habitsTotal = scheduledHabits.length

  const challengesDone = challenges.filter((c: any) =>
    c.challenge_checkins?.some((ci: any) => ci.date === today && ci.completed)
  ).length
  const challengesTotal = challenges.length

  const allMilestones = goals.flatMap((g: any) => g.goal_milestones ?? [])
  const milestonesDone = allMilestones.filter((m: any) => m.done).length
  const milestonesTotal = allMilestones.length

  const healthTargets = [
    (healthLog?.water_glasses ?? 0) > 0,
    !!healthLog?.exercise_done,
    (healthLog?.sleep_hours ?? 0) > 0,
    (healthLog?.steps ?? 0) > 0,
  ]
  const healthDone = healthTargets.filter(Boolean).length
  const healthTotal = 4

  // ---- Engagement signals: only count a feature in scoring after user
  // has interacted with it at least once. New users start at 0/0, not 0/N.
  const deenEngaged       = (prayerLogs30 ?? []).length > 0 || !!prayerLog
  const healthEngaged     = !!profile?.height_cm || (healthLogs30 ?? []).length > 0
  const tasksEngaged      = tasksTotal > 0
  const habitsEngaged     = habitsTotal > 0
  const challengesEngaged = challengesTotal > 0

  const sections = [
    { earned: prayerDone,     max: prayerTotal,                  weight: deenEngaged       ? 1 : 0 },
    { earned: tasksDone,      max: Math.max(tasksTotal, 1),      weight: tasksEngaged      ? 1 : 0 },
    { earned: habitsDone,     max: Math.max(habitsTotal, 1),     weight: habitsEngaged     ? 1 : 0 },
    { earned: challengesDone, max: Math.max(challengesTotal, 1), weight: challengesEngaged ? 1 : 0 },
    { earned: healthDone,     max: healthTotal,                  weight: healthEngaged     ? 1 : 0 },
  ]
  const totalWeight = sections.reduce((s, x) => s + x.weight, 0)
  const overallScore = totalWeight > 0
    ? Math.round(sections.reduce((s, x) => s + (x.weight * x.earned / x.max), 0) / totalWeight * 100)
    : 0
  const noEngagement = totalWeight === 0

  // ---- 30-day history (for sparkline + delta vs yesterday) ----
  const habitsHistory     = useMemo(() => computeHabitsHistory(habits as Habit[], habitLogs30 as HabitLog[], today), [habits, habitLogs30, today])
  const deenHistory       = useMemo(() => computeDeenHistory(prayerLogs30, today), [prayerLogs30, today])
  const challengesHistory = useMemo(() => computeChallengesHistory(allChallenges, challengeCheckins30, today), [allChallenges, challengeCheckins30, today])
  const healthHistory     = useMemo(() => computeHealthHistory(healthLogs30, today), [healthLogs30, today])
  const tasksHistory      = useMemo(() => computeDailyTasksHistory(tasks30, today), [tasks30, today])
  const totalHistory      = useMemo(
    () => combineDayScores([habitsHistory, deenHistory, challengesHistory, healthHistory, tasksHistory]),
    [habitsHistory, deenHistory, challengesHistory, healthHistory, tasksHistory]
  )
  const todayHistory = totalHistory.find((d) => d.date === today)
  // Overwrite today's history pct with the live "overallScore" we just computed
  const liveTotalHistory = totalHistory.map((d) =>
    d.date === today ? { ...d, pct: overallScore } : d
  )
  const yesterdayDelta = deltaVsPrev(liveTotalHistory, today)

  const avg30 = (() => {
    const valid = liveTotalHistory.filter((d) => d.max > 0)
    if (valid.length === 0) return 0
    return Math.round(valid.reduce((s, d) => s + d.pct, 0) / valid.length)
  })()

  // ---- Verdict ----
  const firstName = (profile?.name && profile.name.trim())
    ? profile.name.trim().split(' ')[0]
    : 'friend'
  const greeting = getGreeting(firstName)
  const verdict = getVerdict(overallScore)
  const insights = aiReports.length
  const reminders = todayTasks.filter((t) => t.status !== 'completed').length

  const verdictTone = {
    great: 'text-emerald-300',
    good: 'text-gold',
    okay: 'text-orange-300',
    low: 'text-red-300/90',
  }[verdict.tone]

  return (
    <div className="mx-auto max-w-md space-y-5 px-4 pb-8">

      {/* ───────────── Header ───────────── */}
      <div className="flex items-start justify-between pt-4">
        <div className="min-w-0 flex-1">
          <p className="text-xs text-muted-foreground">
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
          </p>
          <h1 className="text-[22px] font-bold text-foreground leading-tight mt-0.5 truncate">
            {greeting.text} <span className="ml-0.5">{greeting.emoji}</span>
          </h1>
        </div>
        <Link href="/profile">
          <div className="h-10 w-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all active:scale-95">
            <Settings size={17} className="text-muted-foreground" />
          </div>
        </Link>
      </div>

      {/* ───────────── Hero score card ───────────── */}
      <div className="relative overflow-hidden rounded-3xl border border-white/10
                      bg-gradient-to-br from-[#16314a] via-[#0f2235] to-[#0b1a2b]
                      p-5 animate-slide-up">
        {/* Background glow */}
        <div className="pointer-events-none absolute -top-10 -right-10 h-44 w-44 rounded-full
                        bg-gold/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-10 -left-10 h-32 w-32 rounded-full
                        bg-primary/20 blur-3xl" />

        <div className="relative flex items-center gap-5">
          {/* Big ring */}
          <div className="relative flex-shrink-0">
            <svg width="108" height="108" style={{ transform: 'rotate(-90deg)' }}>
              <circle cx="54" cy="54" r="46" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="10" />
              <circle cx="54" cy="54" r="46" fill="none"
                stroke={overallScore >= 80 ? '#34d399' : overallScore >= 60 ? '#fbbf24' : overallScore >= 40 ? '#fb923c' : '#f87171'}
                strokeWidth="10" strokeLinecap="round"
                strokeDasharray={289}
                strokeDashoffset={289 * (1 - overallScore / 100)}
                style={{ transition: 'stroke-dashoffset 1s ease', filter: 'drop-shadow(0 0 10px currentColor)' }}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn('text-3xl font-bold tabular-nums leading-none', scoreColor(overallScore))}>
                {overallScore}
              </span>
              <span className="text-[9px] uppercase tracking-widest text-muted-foreground mt-0.5">today</span>
            </div>
          </div>

          {/* Right column */}
          <div className="flex-1 min-w-0">
            {noEngagement ? (
              <>
                <p className="text-sm font-semibold text-foreground leading-snug">
                  Welcome to NAFS 👋
                </p>
                <p className="text-xs text-muted-foreground mt-1.5 leading-snug">
                  Tap any feature below to start. Your score will grow as you engage.
                </p>
              </>
            ) : (
              <>
                {/* Delta vs yesterday */}
                {yesterdayDelta && <DeltaChip delta={yesterdayDelta.delta} />}
                {/* Verdict */}
                <p className={cn('text-sm font-semibold mt-2 leading-snug', verdictTone)}>
                  {verdict.text}
                </p>
                {/* Mini stats — only show engaged features */}
                <div className="flex flex-wrap gap-x-3 gap-y-1 mt-2.5 text-[11px]">
                  {deenEngaged       && <MiniStat emoji="🕌" v={prayerDone} m={prayerTotal} />}
                  {tasksEngaged      && <MiniStat emoji="✅" v={tasksDone} m={tasksTotal} />}
                  {habitsEngaged     && <MiniStat emoji="🔄" v={habitsDone} m={habitsTotal} />}
                  {challengesEngaged && <MiniStat emoji="🎯" v={challengesDone} m={challengesTotal} />}
                  {healthEngaged     && <MiniStat emoji="❤️" v={healthDone} m={healthTotal} />}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* ───────────── Features grid ───────────── */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <p className="section-header">Features</p>
          <span className="text-[10px] text-muted-foreground tabular-nums">{new Date().getHours() < 21 ? 'tap to log' : 'review today'}</span>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <FeatureCard href="/tasks"      emoji="✅" gradient="from-emerald-500/25 via-emerald-700/10 to-transparent" iconBg="bg-emerald-500/30" iconRing="ring-emerald-400/30"
            title="Tasks"          sub={`${tasksDone} / ${tasksTotal} done`}              pct={tasksTotal      ? (tasksDone / tasksTotal) * 100 : 0}     barColor="bg-emerald-400" notStarted={!tasksEngaged} />

          <FeatureCard href="/habits"     emoji="🔄" gradient="from-cyan-500/25 via-blue-700/10 to-transparent"       iconBg="bg-cyan-500/30"    iconRing="ring-cyan-400/30"
            title="Habits"         sub={`${habitsDone} / ${habitsTotal} done`}            pct={habitsTotal     ? (habitsDone / habitsTotal) * 100 : 0}   barColor="bg-cyan-400" notStarted={!habitsEngaged} />

          <FeatureCard href="/challenges" emoji="🎯" gradient="from-pink-500/25 via-rose-700/10 to-transparent"        iconBg="bg-pink-500/30"    iconRing="ring-pink-400/30"
            title="Challenges"     sub={`${challengesDone} / ${challengesTotal} today`}    pct={challengesTotal ? (challengesDone / challengesTotal) * 100 : 0} barColor="bg-pink-400" notStarted={!challengesEngaged} />

          <FeatureCard href="/deen"       emoji="🕌" gradient="from-yellow-500/25 via-amber-700/10 to-transparent"     iconBg="bg-amber-500/30"   iconRing="ring-amber-400/30"
            title="Deen"           sub={deenEngaged ? `${prayerDone} / ${prayerTotal} pts` : 'Track prayers'}
            pct={deenEngaged && prayerTotal ? (prayerDone / prayerTotal) * 100 : 0}        barColor="bg-amber-400" notStarted={!deenEngaged} />

          <FeatureCard href="/health"     emoji="❤️" gradient="from-red-500/25 via-pink-700/10 to-transparent"          iconBg="bg-red-500/30"     iconRing="ring-red-400/30"
            title="Health"         sub={healthEngaged ? `${healthDone} / ${healthTotal} logged` : 'Set up profile'}
            pct={healthEngaged ? (healthDone / healthTotal) * 100 : 0}                     barColor="bg-red-400" notStarted={!healthEngaged} />

          <FeatureCard href="/goals"      emoji="🏆" gradient="from-yellow-500/20 via-orange-700/10 to-transparent"    iconBg="bg-yellow-500/30"  iconRing="ring-yellow-400/30"
            title="Goals"          sub={`${milestonesDone} / ${milestonesTotal} milestones`} pct={milestonesTotal ? (milestonesDone / milestonesTotal) * 100 : 0} barColor="bg-yellow-400" notStarted={milestonesTotal === 0} />
        </div>
      </div>

      {/* ───────────── History teaser ───────────── */}
      <HistoryTeaserCard
        days={liveTotalHistory}
        title="Your last 30 days"
        href="/history?tab=overall"
        subtitle={`avg ${avg30}% · tap for breakdown`}
        emoji="📊"
        accent="gold"
      />

      {/* ───────────── AI insights ───────────── */}
      <Link href="/coach">
        <div className="relative overflow-hidden rounded-2xl border border-white/10
                        bg-gradient-to-br from-fuchsia-500/15 via-purple-700/10 to-transparent
                        p-4 flex items-center gap-3 hover:border-fuchsia-400/30 transition-all active:scale-[0.99]">
          <div className="pointer-events-none absolute -right-6 -bottom-6 h-24 w-24 rounded-full bg-fuchsia-500/15 blur-2xl" />
          <div className="relative h-11 w-11 rounded-2xl bg-gradient-to-br from-fuchsia-500/40 to-purple-700/30 flex items-center justify-center text-xl flex-shrink-0">
            🧠
          </div>
          <div className="relative flex-1 min-w-0">
            <p className="font-semibold text-foreground text-sm">AI analysis &amp; reminders</p>
            <p className="text-xs text-muted-foreground mt-0.5">
              {insights} new insight{insights !== 1 ? 's' : ''} · {reminders} reminder{reminders !== 1 ? 's' : ''}
            </p>
          </div>
          <ChevronRight size={16} className="relative text-muted-foreground flex-shrink-0" />
        </div>
      </Link>
    </div>
  )
}

// ============================================================
// Sub-components
// ============================================================

function DeltaChip({ delta }: { delta: number }) {
  const icon = delta > 0 ? <TrendingUp size={11} /> : delta < 0 ? <TrendingDown size={11} /> : <Minus size={11} />
  return (
    <div className={cn(
      'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-semibold',
      delta > 2  ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400' :
      delta < -2 ? 'border-red-500/30 bg-red-500/10 text-red-400' :
                   'border-white/10 bg-white/5 text-muted-foreground'
    )}>
      {icon}
      {delta > 0 ? '+' : ''}{delta}%
      <span className="opacity-70 ml-0.5">vs yesterday</span>
    </div>
  )
}

function MiniStat({ emoji, v, m }: { emoji: string; v: number; m: number }) {
  const pct = m > 0 ? v / m : 0
  return (
    <span className={cn('inline-flex items-center gap-1 tabular-nums',
      pct >= 1 ? 'text-emerald-400' : pct >= 0.5 ? 'text-foreground' : 'text-muted-foreground'
    )}>
      <span className="opacity-90">{emoji}</span>
      <span className="font-semibold">{v}/{m}</span>
    </span>
  )
}

function FeatureCard({
  href, emoji, gradient, iconBg, iconRing, title, sub, pct, barColor, notStarted,
}: {
  href: string
  emoji: string
  gradient: string
  iconBg: string
  iconRing: string
  title: string
  sub: string
  pct: number
  barColor: string
  notStarted?: boolean
}) {
  const done = !notStarted && pct >= 100
  return (
    <Link href={href}>
      <div className={cn(
        'relative overflow-hidden rounded-2xl border bg-gradient-to-br p-4 transition-all active:scale-95',
        notStarted ? 'border-white/8 hover:border-white/20 opacity-90' : 'border-white/8 hover:border-white/20',
        gradient
      )}>
        <div className="flex items-center justify-between mb-3">
          <div className={cn('h-10 w-10 rounded-xl flex items-center justify-center text-xl ring-1', iconBg, iconRing)}>
            {emoji}
          </div>
          {done ? (
            <span className="text-[10px] font-bold text-emerald-400">✓</span>
          ) : notStarted ? (
            <span className="text-[9px] font-semibold uppercase tracking-wider text-gold flex items-center gap-0.5">
              Start <ChevronRight size={11} />
            </span>
          ) : (
            <ChevronRight size={14} className="text-muted-foreground/60" />
          )}
        </div>
        <p className="font-bold text-foreground text-[15px] leading-tight">{title}</p>
        <p className={cn('text-xs mt-0.5',
          notStarted ? 'text-muted-foreground/70 italic' : 'text-muted-foreground'
        )}>{notStarted ? sub : sub}</p>
        <div className="mt-3 h-1.5 w-full rounded-full bg-white/10">
          {!notStarted && (
            <div className={cn('h-full rounded-full transition-all', barColor)}
              style={{ width: `${Math.min(100, Math.max(0, pct))}%` }} />
          )}
        </div>
      </div>
    </Link>
  )
}

