'use client'

import { useMemo } from 'react'
import Link from 'next/link'
import {
  Settings, ChevronRight, TrendingUp, TrendingDown, Minus, Check,
  ListChecks, Repeat, Flame, MoonStar, HeartPulse, Trophy, Sparkles,
  type LucideIcon,
} from 'lucide-react'
import { cn, scoreColor } from '@/lib/utils'
import { healthProgress } from '@/lib/health-progress'
import { selectNextUp } from '@/lib/next-up'
import { levelFor } from '@/lib/levels'
import { moodFor, pickQuote } from '@/lib/quotes'
import { findRepeatedMisses, describeMiss } from '@/lib/misses'
import { latestBySubject, subjectFor, type CoachNote } from '@/lib/coach-notes'
import CoachNoteInput from '@/components/CoachNoteInput'
import { type CustomMetric } from '@/lib/health'
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
  /** Distinct days with anything recorded, all time. Drives the level chip. */
  lifetimeDays: number
  /** What the user told the coach, last 90 days, newest first. */
  coachNotes: CoachNote[]
}

function getGreeting(name: string) {
  const h = new Date().getHours()
  if (h < 5)  return { text: `Up late, ${name}` }
  if (h < 12) return { text: `Good morning, ${name}` }
  if (h < 17) return { text: `Good afternoon, ${name}` }
  if (h < 21) return { text: `Good evening, ${name}` }
  return            { text: `Good night, ${name}` }
}

function getVerdict(score: number): { text: string; tone: 'great' | 'good' | 'okay' | 'low' } {
  const h = new Date().getHours()
  if (score >= 85) return { text: 'On fire today — keep this rhythm', tone: 'great' }
  if (score >= 70) return { text: 'Strong day — finish what you started', tone: 'great' }
  if (score >= 50) return { text: h < 18 ? 'Solid start — push for more' : 'Decent day — close it well', tone: 'good' }
  if (score >= 25) return { text: h < 18 ? 'Still time to climb. Start with one win.' : 'Quiet day. Take a small win before sleep.', tone: 'okay' }
  return { text: h < 18 ? 'The day\'s still yours. Begin somewhere.' : 'Reset. Tomorrow is a new shot.', tone: 'low' }
}

export default function HomeClient({
  profile, habits, habitLogs, habitLogs30, prayerLog, prayerLogs30,
  challenges, allChallenges, challengeCheckins30,
  todayTasks, tasks30, goals, aiReports,
  healthLog, healthLogs30, today, lifetimeDays, coachNotes,
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

  // UI-01: the same selector the Health page uses. Home used to hardcode a
  // denominator of 4 while Health derived its own from the enabled categories,
  // so one day read 0/4 here and 0/5 there. Both were self-consistent, which is
  // what made it confusing rather than obviously broken.
  const health = healthProgress(
    healthLog as any,
    (profile?.health_defaults_hidden ?? []) as string[],
    (profile?.health_extras_config ?? []) as CustomMetric[],
  )
  const healthDone = health.done
  const healthTotal = health.total

  // ---- Faith mode: Deen module is opt-in (signup question / Profile toggle).
  // Default true when the column is missing so pre-migration DBs keep working.
  const deenOn = (profile?.deen_enabled ?? true) as boolean

  // ---- Engagement signals: only count a feature in scoring after user
  // has interacted with it at least once. New users start at 0/0, not 0/N.
  const deenEngaged       = deenOn && ((prayerLogs30 ?? []).length > 0 || !!prayerLog)
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

  // ---- What's actually left today ----
  const doneHabitIds = useMemo(
    () => new Set(
      (habits as Habit[])
        .filter((h) => isHabitDoneToday(h, (habitLogs as HabitLog[]).find((l) => l.habit_id === h.id)))
        .map((h) => h.id),
    ),
    [habits, habitLogs],
  )
  const nextUp = useMemo(() => selectNextUp({
    today,
    tasks: todayTasks as any,
    habits: habits as any,
    doneHabitIds,
    deenEnabled: deenOn,
    // Recorded, not "prayed": a deliberately logged miss is still a decision
    // made, and Next Up is about what still needs attention.
    prayersRecorded: prayerValues.filter((v) => v > 0).length,
  }), [today, todayTasks, habits, doneHabitIds, deenOn, prayerValues])

  const plannedRemaining =
    (tasksTotal - tasksDone) + (habitsTotal - habitsDone)
    + (deenOn ? Math.max(0, 5 - prayerValues.filter((v) => v > 0).length) : 0)

  // ---- A line for the day, chosen by how the day is going ----
  // Curated, never generated: a misquoted ayah is worse than no quote. A day
  // with nothing logged yet gets a steady line, not a verdict.
  const mood = moodFor({ score: noEngagement ? null : overallScore, nothingLoggedYet: noEngagement })
  const quote = pickQuote(mood, deenOn, today)

  // ---- Patterns worth a look: what keeps not happening ----
  // Deterministic, from the records. Unrecorded is never counted as missed.
  const misses = useMemo(() => findRepeatedMisses({
    today,
    habits: habits as any,
    habitLogs: habitLogs30 as any,
    prayerLogs: prayerLogs30 as any,
    deenEnabled: deenOn,
  }), [today, habits, habitLogs30, prayerLogs30, deenOn])

  // What they said last time about each of these, so they — and the coach —
  // can see when the reason is the same one again.
  const lastReasons = useMemo(() => latestBySubject(coachNotes, 'miss_reason'), [coachNotes])
  const todayBadDayNote = coachNotes.find((n) => n.kind === 'low_score' && n.date === today) ?? null

  // ---- 30-day history (for sparkline + delta vs yesterday) ----
  const habitsHistory     = useMemo(() => computeHabitsHistory(habits as Habit[], habitLogs30 as HabitLog[], today), [habits, habitLogs30, today])
  const deenHistory       = useMemo(() => deenOn ? computeDeenHistory(prayerLogs30, today) : [], [deenOn, prayerLogs30, today])
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
  const PLACEHOLDERS = new Set(['', 'friend', 'Friend', 'No name'])
  const rawName: string = (profile?.name as string | undefined) ?? ''
  const trimmed = rawName.trim()
  const emailPrefix = (profile?.email as string | undefined)?.split('@')[0] ?? ''
  const firstName = PLACEHOLDERS.has(trimmed)
    ? (emailPrefix || 'friend')
    : trimmed.split(' ')[0]
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
      <div className="anim-up flex items-start justify-between pt-4">
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground/70">
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
          </p>
          <h1 className="text-[22px] font-semibold text-foreground leading-tight mt-1 truncate">
            {greeting.text}
          </h1>
          {/* Level chip — where you are right now. Counts days recorded, so it
              only ever goes up. Hidden until the first day, rather than showing
              a "Level 0". Taps through to the full card on Profile. */}
          {(() => {
            const lvl = levelFor(lifetimeDays)
            if (!lvl.current) return null
            return (
              <Link href="/profile"
                className="mt-1.5 inline-flex items-center gap-1.5 text-[11px] text-muted-foreground
                           hover:text-foreground transition-colors">
                <span className="rounded-full border border-gold/30 bg-gold/10 px-2 py-0.5 font-semibold text-gold">
                  Level {lvl.current.number}
                </span>
                <span>{lvl.current.name}</span>
                {lvl.next && (
                  <span className="text-muted-foreground/60">· {lvl.daysToNext}d to next</span>
                )}
              </Link>
            )
          })()}
        </div>
        <Link href="/profile">
          <div className="h-10 w-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all active:scale-95">
            <Settings size={17} className="text-muted-foreground" />
          </div>
        </Link>
      </div>

      {/* ───────────── Hero score card ───────────── */}
      <div className="anim-up relative overflow-hidden rounded-3xl border border-white/10
                      bg-gradient-to-br from-[#16314a] via-[#0f2235] to-[#0b1a2b]
                      p-5">
        {/* Background glow */}
        <div className="pointer-events-none absolute -top-12 -right-12 h-48 w-48 rounded-full
                        bg-gold/[0.08] blur-3xl" />

        <div className="relative flex items-center gap-5">
          {/* Big ring */}
          <div className="relative flex-shrink-0">
            <svg width="108" height="108" style={{ transform: 'rotate(-90deg)' }}>
              <defs>
                <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stopColor="#E8C547" />
                  <stop offset="100%" stopColor="#9A7B1E" />
                </linearGradient>
              </defs>
              <circle cx="54" cy="54" r="46" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="9" />
              <circle cx="54" cy="54" r="46" fill="none"
                stroke="url(#ringGrad)"
                strokeWidth="9" strokeLinecap="round"
                strokeDasharray={289}
                strokeDashoffset={289 * (1 - overallScore / 100)}
                style={{ transition: 'stroke-dashoffset 1s cubic-bezier(0.16,1,0.3,1)',
                         filter: 'drop-shadow(0 0 6px rgba(201,162,39,0.35))' }}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn('font-display text-[34px] font-semibold tabular-nums leading-none tracking-tight', scoreColor(overallScore))}>
                {overallScore}
              </span>
              <span className="text-[9px] uppercase tracking-[0.18em] text-muted-foreground mt-1">today</span>
            </div>
          </div>

          {/* Right column */}
          <div className="flex-1 min-w-0">
            {noEngagement ? (
              <>
                <p className="text-sm font-semibold text-foreground leading-snug">
                  Welcome to Ascend 👋
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
                <div className="flex flex-wrap gap-x-3 gap-y-1.5 mt-2.5 text-[11px]">
                  {deenEngaged       && <MiniStat icon={MoonStar}   v={prayerDone} m={prayerTotal} />}
                  {tasksEngaged      && <MiniStat icon={ListChecks} v={tasksDone} m={tasksTotal} />}
                  {habitsEngaged     && <MiniStat icon={Repeat}     v={habitsDone} m={habitsTotal} />}
                  {challengesEngaged && <MiniStat icon={Flame}      v={challengesDone} m={challengesTotal} />}
                  {healthEngaged     && <MiniStat icon={HeartPulse} v={healthDone} m={healthTotal} />}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* ───────────── A line for the day ─────────────
          Quiet, one line, holds still for the day. Faith mode picks Quran and
          hadith from a curated list; otherwise stoic. */}
      {quote && (
        <div className="anim-up rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
          <p className="text-sm text-foreground leading-relaxed italic">“{quote.text}”</p>
          <p className="mt-1 text-[10px] uppercase tracking-wider text-muted-foreground">— {quote.source}</p>
          {/* On a rough day, ask what is going on — once — and keep the answer
              for the coach. Never asked on a day with nothing logged yet. */}
          {mood === 'struggling' && (
            <div className="mt-2 border-t border-white/10 pt-2">
              {todayBadDayNote ? (
                <p className="text-[11px] text-muted-foreground leading-snug">
                  You told the coach: <span className="italic text-foreground/85">“{todayBadDayNote.content}”</span>
                </p>
              ) : (
                <CoachNoteInput kind="low_score" subject={null}
                  prompt="Rough day? Tell the coach what's going on"
                  placeholder="What's getting in the way today? What are you thinking?" />
              )}
            </div>
          )}
        </div>
      )}

      {/* ───────────── Next up ─────────────
          What is actually left today, above the navigation grid. Each row goes
          straight to the control that can record it, so acting on it is one
          tap rather than a hunt through the app. */}
      {nextUp.length > 0 ? (
        <div className="anim-up rounded-2xl border border-white/10 bg-white/[0.04] p-4">
          <div className="flex items-baseline justify-between mb-3">
            <p className="text-sm font-semibold text-foreground">Next up</p>
            <p className="text-[11px] text-muted-foreground">{plannedRemaining} left today</p>
          </div>
          <div className="space-y-2">
            {nextUp.map((item) => (
              <Link key={`${item.kind}-${item.id}`} href={item.href}
                className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 p-3
                           transition-all active:scale-95 hover:border-gold/30 hover:bg-white/[0.07]">
                <span className="text-lg">{item.emoji}</span>
                <span className="flex-1 min-w-0">
                  <span className="block truncate text-sm font-medium text-foreground">{item.label}</span>
                  {item.detail && (
                    <span className="block text-[11px] text-muted-foreground">{item.detail}</span>
                  )}
                </span>
                <ChevronRight size={15} className="text-muted-foreground/50 shrink-0" />
              </Link>
            ))}
          </div>
        </div>
      ) : !noEngagement && (
        // Everything scheduled is done. Say so and stop — offering more work
        // here would punish finishing.
        <div className="anim-up rounded-2xl border border-emerald-500/25 bg-emerald-500/[0.07] p-4 text-center">
          <p className="text-sm font-semibold text-emerald-300">Today&apos;s plan is complete</p>
          <p className="mt-1 text-[11px] text-muted-foreground">
            Nothing left scheduled. Anything else today is a bonus.
          </p>
        </div>
      )}

      {/* ───────────── Patterns worth a look ─────────────
          Things that keep not happening, with the counts. This names the
          pattern and points at the coach, who is prompted to ask what got in
          the way and turn the answer into tasks. It does not punish: the only
          consequence in this app is one the user sets on themselves (a sadqa
          pledge on a challenge). */}
      {misses.length > 0 && (
        <div className="anim-up rounded-2xl border border-orange-500/25 bg-orange-500/[0.06] p-4">
          <div className="flex items-baseline justify-between mb-2">
            <p className="text-sm font-semibold text-orange-300">Patterns worth a look</p>
            <p className="text-[11px] text-muted-foreground">last 7 days</p>
          </div>
          <div className="space-y-3">
            {misses.slice(0, 3).map((m) => {
              const subject = subjectFor(m)
              return (
                <div key={subject} className="text-sm">
                  <div className="flex items-center gap-2">
                    <span>{m.emoji}</span>
                    <span className="flex-1 text-foreground">{describeMiss(m)}</span>
                  </div>
                  {/* Why? — saved in their words; shown back next time it repeats. */}
                  <div className="pl-7">
                    <CoachNoteInput kind="miss_reason" subject={subject} tone="orange"
                      previous={lastReasons.get(subject) ?? null}
                      prompt="Why? Tell the coach" placeholder="What got in the way?" />
                  </div>
                </div>
              )
            })}
          </div>
          <p className="mt-2.5 text-[11px] text-muted-foreground">
            Only what you recorded. Days with nothing logged aren&apos;t counted against you.
          </p>
          <Link href="/coach"
            className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-orange-300 hover:text-orange-200">
            Ask the coach why <ChevronRight size={12} />
          </Link>
        </div>
      )}

      {/* ───────────── Features grid ───────────── */}
      <div className="anim-up anim-d2">
        <div className="flex items-center justify-between mb-3">
          <p className="section-header">Features</p>
          <span className="text-[10px] text-muted-foreground tabular-nums">{new Date().getHours() < 21 ? 'tap to log' : 'review today'}</span>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <FeatureCard href="/tasks" icon={ListChecks} title="Tasks"
            sub={`${tasksDone} of ${tasksTotal} done`}
            pct={tasksTotal ? (tasksDone / tasksTotal) * 100 : 0} notStarted={!tasksEngaged} />

          <FeatureCard href="/habits" icon={Repeat} title="Habits"
            sub={`${habitsDone} of ${habitsTotal} done`}
            pct={habitsTotal ? (habitsDone / habitsTotal) * 100 : 0} notStarted={!habitsEngaged} />

          <FeatureCard href="/challenges" icon={Flame} title="Challenges"
            sub={`${challengesDone} of ${challengesTotal} today`}
            pct={challengesTotal ? (challengesDone / challengesTotal) * 100 : 0} notStarted={!challengesEngaged} />

          {deenOn && (
          <FeatureCard href="/deen" icon={MoonStar} title="Deen"
            sub={deenEngaged ? `${prayerDone} of ${prayerTotal} pts` : 'Track prayers'}
            pct={deenEngaged && prayerTotal ? (prayerDone / prayerTotal) * 100 : 0} notStarted={!deenEngaged} />
          )}

          <FeatureCard href="/health" icon={HeartPulse} title="Health"
            sub={healthEngaged ? `${healthDone} of ${healthTotal} logged` : 'Set up profile'}
            pct={healthEngaged ? (healthDone / healthTotal) * 100 : 0} notStarted={!healthEngaged} />

          <FeatureCard href="/goals" icon={Trophy} title="Goals"
            sub={`${milestonesDone} of ${milestonesTotal} milestones`}
            pct={milestonesTotal ? (milestonesDone / milestonesTotal) * 100 : 0} notStarted={milestonesTotal === 0} />
        </div>
      </div>

      {/* ───────────── History teaser ───────────── */}
      <div className="anim-up anim-d3">
      <HistoryTeaserCard
        days={liveTotalHistory}
        title="Your last 30 days"
        href="/history?tab=overall"
        subtitle={`avg ${avg30}% · tap for breakdown`}
        emoji="📊"
        accent="gold"
      />
      </div>

      {/* ───────────── AI coach ───────────── */}
      <Link href="/coach" className="anim-up anim-d4 block">
        <div className="nafs-card p-4 flex items-center gap-3 hover:border-gold/25 transition-all active:scale-[0.99]">
          <div className="h-10 w-10 rounded-xl border border-gold/20 bg-gold/[0.07] flex items-center justify-center flex-shrink-0">
            <Sparkles size={16} className="text-gold" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-foreground text-sm">Your coach</p>
            <p className="text-xs text-muted-foreground mt-0.5 tabular-nums">
              {insights} new insight{insights !== 1 ? 's' : ''} · {reminders} reminder{reminders !== 1 ? 's' : ''}
            </p>
          </div>
          <ChevronRight size={15} className="text-muted-foreground/40 flex-shrink-0" />
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

function MiniStat({ icon: Icon, v, m }: { icon: LucideIcon; v: number; m: number }) {
  const pct = m > 0 ? v / m : 0
  return (
    <span className={cn('inline-flex items-center gap-1 tabular-nums',
      pct >= 1 ? 'text-emerald-400' : pct >= 0.5 ? 'text-foreground' : 'text-muted-foreground'
    )}>
      <Icon size={11} strokeWidth={2.2} className="opacity-80" />
      <span className="font-semibold">{v}/{m}</span>
    </span>
  )
}

function FeatureCard({
  href, icon: Icon, title, sub, pct, notStarted,
}: {
  href: string
  icon: LucideIcon
  title: string
  sub: string
  pct: number
  notStarted?: boolean
}) {
  const done = !notStarted && pct >= 100
  return (
    <Link href={href}>
      <div className="nafs-card p-4 transition-all duration-200 active:scale-[0.97] hover:border-white/20">
        <div className="flex items-start justify-between">
          <div className={cn(
            'h-9 w-9 rounded-xl border flex items-center justify-center transition-colors',
            done
              ? 'border-emerald-400/25 bg-emerald-400/10 text-emerald-300'
              : 'border-gold/20 bg-gold/[0.07] text-gold'
          )}>
            <Icon size={16} strokeWidth={2} />
          </div>
          {done ? (
            <Check size={14} strokeWidth={3} className="text-emerald-400 mt-1" />
          ) : notStarted ? (
            <span className="text-[9px] font-semibold uppercase tracking-[0.12em] text-gold/90 flex items-center gap-0.5 mt-1.5">
              Start <ChevronRight size={10} />
            </span>
          ) : (
            <ChevronRight size={14} className="text-muted-foreground/40 mt-1" />
          )}
        </div>
        <p className="mt-3.5 font-semibold text-foreground text-[15px] leading-tight">{title}</p>
        <p className="text-xs text-muted-foreground mt-0.5 tabular-nums">{sub}</p>
        <div className="mt-3 h-1 w-full rounded-full bg-white/[0.07]">
          {!notStarted && (
            <div className={cn('h-full rounded-full transition-all duration-500',
              done ? 'bg-emerald-400' : 'bg-gradient-to-r from-gold-dark to-gold')}
              style={{ width: `${Math.min(100, Math.max(0, pct))}%` }} />
          )}
        </div>
      </div>
    </Link>
  )
}

