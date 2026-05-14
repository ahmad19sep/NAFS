'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Plus, MoreVertical, Bell, X, Pencil, Pause, Play, Trash2, BarChart3, Check, Minus, Sparkles, RefreshCw } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Habit, HabitLog, HabitType, ScheduleKind, Weekday } from '@/types'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'
import { computeHabitsHistory } from '@/lib/history'
import { useBodyScrollLock } from '@/hooks/useBodyScrollLock'

// ---------- helpers ----------
const WEEKDAY_CODES: Weekday[] = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']
const WEEKDAY_LABEL: Record<Weekday, string> = {
  mon: 'Mon', tue: 'Tue', wed: 'Wed', thu: 'Thu', fri: 'Fri', sat: 'Sat', sun: 'Sun',
}

function todayWeekday(): Weekday {
  const codes: Weekday[] = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']
  return codes[new Date().getDay()]
}

function isScheduledToday(habit: Habit): boolean {
  if (habit.is_paused) return false
  if (habit.schedule_kind === 'weekdays') {
    return (habit.schedule_days ?? []).includes(todayWeekday())
  }
  return true  // daily + per_week always show today
}

function isCompletedToday(habit: Habit, log?: HabitLog): boolean {
  if (!log) return false
  if (!log.completed) return false
  if (habit.type === 'counter') return log.value >= habit.target_value
  if (habit.type === 'duration') return log.duration_mins >= habit.time_target_mins
  return true
}

function formatSchedule(habit: Habit): string {
  const parts: string[] = []
  if (habit.schedule_kind === 'daily') parts.push('Daily')
  else if (habit.schedule_kind === 'weekdays')
    parts.push((habit.schedule_days ?? []).map((d) => WEEKDAY_LABEL[d]).join(' · ') || 'Pick days')
  else if (habit.schedule_kind === 'per_week') parts.push(`${habit.weekly_target ?? 1}× / week`)

  if (habit.type === 'duration' && habit.time_target_mins) parts.push(`${habit.time_target_mins} min`)
  if (habit.type === 'counter' && habit.target_value) parts.push(`${habit.target_value} ${habit.unit || ''}`.trim())
  if (habit.reminder_time) {
    const [h, m] = habit.reminder_time.split(':').map(Number)
    const ampm = h >= 12 ? 'PM' : 'AM'
    const h12 = ((h + 11) % 12) + 1
    parts.push(`${h12}:${String(m).padStart(2, '0')} ${ampm}`)
  }
  return parts.join(' · ')
}

const TYPE_META: Record<HabitType, { emoji: string; label: string; hint: string }> = {
  simple:   { emoji: '✅', label: 'Yes / No',  hint: 'Did I do it today?' },
  counter:  { emoji: '🔢', label: 'Counter',   hint: 'Reach a daily count (e.g. 8 glasses)' },
  duration: { emoji: '⏱',  label: 'Duration',  hint: 'Spend time on it (e.g. 30 min)' },
  subject:  { emoji: '📖', label: 'Subject',   hint: 'Track progress through a book / course' },
}

// ---------- props ----------
interface Props {
  userId: string
  habits: Habit[]
  logs: HabitLog[]
  today: string
}

interface FormState {
  id?: string
  name: string
  emoji: string
  type: HabitType
  target_value: number
  unit: string
  time_target_mins: number
  subject_name: string
  subject_total: number
  subject_position: number
  subject_unit: string
  schedule_kind: ScheduleKind
  schedule_days: Weekday[]
  weekly_target: number
  reminder_time: string  // 'HH:MM' or ''
  why: string
  score_weight: number
}

const blankForm: FormState = {
  name: '', emoji: '⭐', type: 'simple',
  target_value: 1, unit: '', time_target_mins: 0,
  subject_name: '', subject_total: 0, subject_position: 0, subject_unit: 'pages',
  schedule_kind: 'daily', schedule_days: [], weekly_target: 3,
  reminder_time: '', why: '', score_weight: 2,
}

function habitToForm(h: Habit): FormState {
  return {
    id: h.id,
    name: h.name, emoji: h.emoji, type: h.type,
    target_value: h.target_value, unit: h.unit, time_target_mins: h.time_target_mins,
    subject_name: h.subject_name ?? '',
    subject_total: h.subject_total ?? 0,
    subject_position: h.subject_position ?? 0,
    subject_unit: h.subject_unit ?? 'pages',
    schedule_kind: h.schedule_kind,
    schedule_days: h.schedule_days ?? [],
    weekly_target: h.weekly_target ?? 3,
    reminder_time: h.reminder_time?.slice(0, 5) ?? '',
    why: h.why ?? '',
    score_weight: h.score_weight,
  }
}

// ============================================================
// MAIN
// ============================================================
export default function HabitsClient({ userId, habits, logs, today }: Props) {
  const router = useRouter()
  const [formOpen, setFormOpen] = useState<FormState | null>(null)
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null)

  const history = useMemo(() => computeHabitsHistory(habits, logs, today), [habits, logs, today])

  useEffect(() => {
    function close() { setMenuOpenId(null) }
    window.addEventListener('click', close)
    return () => window.removeEventListener('click', close)
  }, [])

  // ---- aggregate ----
  const todayLogs = useMemo(() => logs.filter((l) => l.date === today), [logs, today])
  const logFor = (id: string) => todayLogs.find((l) => l.habit_id === id)

  const visible = habits.filter((h) => h.is_active)
  const scheduled = visible.filter((h) => isScheduledToday(h))
  const doneToday = scheduled.filter((h) => isCompletedToday(h, logFor(h.id))).length
  const pct = scheduled.length > 0 ? Math.round((doneToday / scheduled.length) * 100) : 0
  const longestStreak = visible.reduce((m, h) => Math.max(m, h.longest_streak ?? 0), 0)

  async function logHabit(habitId: string, payload: Partial<{
    completed: boolean; value: number; duration_mins: number; notes: string | null; subject_delta: number
  }>) {
    await fetch('/api/habits/log', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ habitId, date: today, ...payload }),
    })
    router.refresh()
  }

  async function deleteHabit(id: string) {
    if (!confirm('Delete this habit? History will be removed too.')) return
    await fetch(`/api/habits/${id}`, { method: 'DELETE' })
    router.refresh()
  }

  async function dismissAiStarter(id: string) {
    await fetch(`/api/habits/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ai_starter_pack: null }),
    })
    router.refresh()
  }

  const [generatingAiFor, setGeneratingAiFor] = useState<string | null>(null)
  async function generateAiStarter(id: string) {
    setGeneratingAiFor(id)
    try {
      const res = await fetch('/api/ai/habit-starter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ habitId: id }),
      })
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        alert(b?.error || 'AI tips failed — try again')
      } else {
        router.refresh()
      }
    } catch (e: any) {
      alert(e?.message || 'Network error')
    }
    setGeneratingAiFor(null)
  }

  async function addRelatedHabit(h: any) {
    await fetch('/api/habits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId,
        name: h.name,
        emoji: h.emoji || '⭐',
        type: h.type,
        target_value: h.target_value ?? 1,
        unit: h.unit ?? '',
        time_target_mins: h.time_target_mins ?? 0,
        category: 'custom',
        score_weight: 2,
        schedule_kind: 'daily',
      }),
    })
    router.refresh()
  }
  async function togglePause(h: Habit) {
    await fetch(`/api/habits/${h.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ is_paused: !h.is_paused }),
    })
    router.refresh()
  }

  return (
    <div className="mx-auto max-w-md px-4 space-y-4 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <div className="flex items-baseline justify-between mt-0.5">
          <h1 className="text-2xl font-bold text-foreground">Habits</h1>
          <button onClick={() => setFormOpen({ ...blankForm })}
            className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-3.5 py-2 text-sm font-semibold
                       text-foreground hover:bg-white/10 hover:border-gold/40 transition-all active:scale-95">
            <Plus size={14} className="text-gold" /> New
          </button>
        </div>
        <p className="text-sm text-muted-foreground mt-0.5">
          {scheduled.length} active · {doneToday} done today
        </p>
      </div>

      {/* Aggregate progress card */}
      <div className="nafs-card p-4">
        <div className="flex items-center justify-between mb-2">
          <p className="text-sm font-semibold text-foreground">Today's habit progress</p>
          <span className={cn('text-xl font-bold tabular-nums',
            pct >= 100 && scheduled.length > 0 ? 'text-emerald-400'
            : pct >= 50 ? 'text-gold' : 'text-muted-foreground'
          )}>{pct}%</span>
        </div>
        <div className="h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
          <div className={cn('h-full rounded-full transition-all',
            pct >= 100 && scheduled.length > 0 ? 'bg-emerald-400' : 'bg-gold'
          )} style={{ width: `${pct}%` }} />
        </div>
        <div className="flex justify-between items-center mt-2 text-xs">
          <span className="text-muted-foreground">{doneToday} / {scheduled.length} done</span>
          {longestStreak > 0 && (
            <span className="text-orange-400 font-semibold">🔥 longest streak: {longestStreak}d</span>
          )}
        </div>
      </div>

      {/* History teaser */}
      {visible.length > 0 && (
        <HistoryTeaserCard
          days={history}
          title="Habits history"
          href="/history?tab=habits"
          emoji="🔄"
          accent="teal"
        />
      )}

      {/* Empty state */}
      {visible.length === 0 && (
        <div className="text-center py-14">
          <p className="text-5xl">💪</p>
          <p className="mt-3 font-semibold text-foreground">No habits yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Tap New to create your first habit.</p>
        </div>
      )}

      {/* Habit cards */}
      <div className="space-y-3">
        {visible.map((h) => (
          <HabitCard key={h.id} habit={h} log={logFor(h.id)}
            menuOpen={menuOpenId === h.id}
            onMenuToggle={(e) => { e.stopPropagation(); setMenuOpenId(menuOpenId === h.id ? null : h.id) }}
            onEdit={() => { setMenuOpenId(null); setFormOpen(habitToForm(h)) }}
            onDelete={() => { setMenuOpenId(null); deleteHabit(h.id) }}
            onPause={() => { setMenuOpenId(null); togglePause(h) }}
            onLog={(p) => logHabit(h.id, p)}
            onDismissAi={() => dismissAiStarter(h.id)}
            onGenerateAi={() => generateAiStarter(h.id)}
            generatingAi={generatingAiFor === h.id}
            onAddRelated={addRelatedHabit}
          />
        ))}
      </div>

      {/* Create / Edit sheet */}
      {formOpen && (
        <HabitForm
          form={formOpen}
          onClose={() => setFormOpen(null)}
          onSaved={() => { setFormOpen(null); router.refresh() }}
          userId={userId}
        />
      )}
    </div>
  )
}

// ============================================================
// HABIT CARD
// ============================================================
function HabitCard({ habit, log, onLog, onEdit, onDelete, onPause, menuOpen, onMenuToggle, onDismissAi, onGenerateAi, generatingAi, onAddRelated }: {
  habit: Habit
  log: HabitLog | undefined
  onLog: (p: Partial<{ completed: boolean; value: number; duration_mins: number; notes: string | null; subject_delta: number }>) => void
  onEdit: () => void
  onDelete: () => void
  onPause: () => void
  menuOpen: boolean
  onMenuToggle: (e: React.MouseEvent) => void
  onDismissAi: () => void
  onGenerateAi: () => void
  generatingAi: boolean
  onAddRelated: (h: any) => void
}) {
  const scheduled = isScheduledToday(habit)
  const done = isCompletedToday(habit, log)

  return (
    <div className={cn(
      'rounded-2xl border p-4 transition-all',
      done ? 'border-emerald-500/30 bg-emerald-500/8'
        : habit.is_paused ? 'border-white/5 bg-white/3 opacity-60'
        : 'border-white/10 bg-white/5'
    )}>
      {/* Header row */}
      <div className="flex items-start gap-3">
        <div className={cn(
          'h-10 w-10 rounded-xl flex items-center justify-center text-xl flex-shrink-0',
          done ? 'bg-emerald-500/20' : 'bg-white/8 border border-white/10'
        )}>
          {habit.emoji}
        </div>
        <div className="flex-1 min-w-0">
          <p className={cn('font-semibold leading-tight', done ? 'text-emerald-300' : 'text-foreground')}>
            {habit.name}
          </p>
          <p className="text-xs text-muted-foreground mt-0.5 truncate">{formatSchedule(habit)}</p>
        </div>
        {/* Streak / done indicator */}
        <div className="flex items-center gap-1 flex-shrink-0">
          {done ? (
            <span className="text-xs font-bold text-emerald-400 flex items-center gap-1">
              <Check size={12} /> done
            </span>
          ) : habit.current_streak > 0 ? (
            <span className="text-xs font-bold text-orange-400">🔥 {habit.current_streak}d</span>
          ) : null}
          {/* Kebab */}
          <div className="relative">
            <button onClick={onMenuToggle}
              className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-white/10 transition-colors">
              <MoreVertical size={14} />
            </button>
            {menuOpen && (
              <div className="absolute right-0 top-8 z-20 w-36 rounded-xl border border-white/10 bg-[#142035] shadow-xl overflow-hidden">
                <MenuItem icon={<Pencil size={13} />} label="Edit" onClick={onEdit} />
                <MenuItem icon={habit.is_paused ? <Play size={13} /> : <Pause size={13} />}
                  label={habit.is_paused ? 'Resume' : 'Pause'} onClick={onPause} />
                <MenuItem icon={<Trash2 size={13} />} label="Delete" onClick={onDelete} danger />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* If not scheduled today, show small note + return */}
      {!scheduled && !habit.is_paused && (
        <p className="mt-3 text-[11px] text-muted-foreground italic">Not scheduled today</p>
      )}
      {habit.is_paused && (
        <p className="mt-3 text-[11px] text-muted-foreground italic">Paused</p>
      )}

      {/* Type-specific widget */}
      {scheduled && !habit.is_paused && (
        <div className="mt-3">
          {habit.type === 'simple' && <SimpleWidget habit={habit} done={done} onLog={onLog} />}
          {habit.type === 'counter' && <CounterWidget habit={habit} log={log} onLog={onLog} />}
          {habit.type === 'duration' && <DurationWidget habit={habit} log={log} done={done} onLog={onLog} />}
          {habit.type === 'subject' && <SubjectWidget habit={habit} log={log} onLog={onLog} />}
        </div>
      )}

      {/* Why */}
      {habit.why && (
        <p className="mt-2 text-[11px] text-muted-foreground/70 italic">"{habit.why}"</p>
      )}

      {/* Manual AI tips trigger when none yet */}
      {!habit.ai_starter_pack && (
        <button onClick={(e) => { e.stopPropagation(); onGenerateAi() }} disabled={generatingAi}
          className="mt-2 w-full flex items-center justify-center gap-1.5 rounded-lg border border-cyan-400/30
                     bg-cyan-500/8 py-1.5 text-[11px] font-semibold text-cyan-300
                     hover:bg-cyan-500/15 transition-all disabled:opacity-50">
          {generatingAi ? <RefreshCw size={11} className="animate-spin" /> : <Sparkles size={11} />}
          {generatingAi ? 'Asking AI…' : 'Get AI tips'}
        </button>
      )}

      {/* AI starter pack */}
      {habit.ai_starter_pack && (
        <div className="mt-3 rounded-xl border border-cyan-400/30 bg-gradient-to-br from-cyan-500/10 via-blue-700/5 to-transparent p-3 space-y-2">
          <div className="flex items-start gap-2">
            <Sparkles size={12} className="text-cyan-400 mt-0.5 flex-shrink-0" />
            <div className="flex-1 min-w-0 space-y-1.5">
              {habit.ai_starter_pack.pattern_insight && (
                <p className="text-[11px] text-foreground leading-snug">
                  <span className="text-cyan-300 font-semibold">📊 </span>
                  {habit.ai_starter_pack.pattern_insight}
                </p>
              )}
              {habit.ai_starter_pack.how_to_succeed && (
                <p className="text-[11px] text-foreground leading-snug">
                  <span className="text-emerald-300 font-semibold">✓ </span>
                  {habit.ai_starter_pack.how_to_succeed}
                </p>
              )}
              {habit.ai_starter_pack.best_time && (
                <p className="text-[11px] text-foreground leading-snug">
                  <span className="text-gold font-semibold">⏰ </span>
                  Best time: {habit.ai_starter_pack.best_time}
                </p>
              )}
            </div>
            <button onClick={(e) => { e.stopPropagation(); onDismissAi() }}
              className="h-6 w-6 rounded-md hover:bg-white/10 flex items-center justify-center text-muted-foreground hover:text-red-400 flex-shrink-0">
              <X size={11} />
            </button>
          </div>

          {(habit.ai_starter_pack.related_habits?.length ?? 0) > 0 && (
            <div className="space-y-1 pt-1 border-t border-white/5">
              <p className="text-[10px] uppercase tracking-wider text-cyan-300">Stack with these</p>
              {habit.ai_starter_pack.related_habits!.map((r, i) => (
                <div key={i} className="rounded-lg border border-white/10 bg-white/5 p-2 flex items-center gap-2">
                  <span className="text-base">{r.emoji || '⭐'}</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-[11px] font-semibold text-foreground truncate">{r.name}</p>
                    <p className="text-[9px] text-muted-foreground">
                      {r.type === 'simple' ? 'Yes/No · daily'
                        : r.type === 'counter' ? `${r.target_value ?? '?'} ${r.unit ?? ''} · daily`
                        : `${r.time_target_mins ?? '?'} min · daily`}
                    </p>
                  </div>
                  <button onClick={(e) => { e.stopPropagation(); onAddRelated(r) }}
                    className="rounded-md bg-cyan-500 px-2 py-1 text-[9px] font-semibold text-white hover:bg-cyan-400">
                    + Add
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function MenuItem({ icon, label, onClick, danger }: {
  icon: React.ReactNode; label: string; onClick: () => void; danger?: boolean
}) {
  return (
    <button onClick={onClick}
      className={cn(
        'w-full flex items-center gap-2.5 px-3 py-2 text-xs text-left transition-colors',
        danger ? 'text-red-400 hover:bg-red-500/10' : 'text-foreground hover:bg-white/5'
      )}>
      {icon}{label}
    </button>
  )
}

// ============================================================
// WIDGETS
// ============================================================

// --- Simple ---
function SimpleWidget({ habit, done, onLog }: {
  habit: Habit; done: boolean
  onLog: (p: any) => void
}) {
  return (
    <button
      onClick={() => onLog({ completed: !done, value: !done ? 1 : 0 })}
      className={cn(
        'w-full rounded-xl py-3 text-sm font-semibold transition-all active:scale-95',
        done
          ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20'
          : 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20'
      )}>
      {done ? '✓ Done today — tap to undo' : 'Mark done'}
    </button>
  )
}

// --- Counter (cells) ---
function CounterWidget({ habit, log, onLog }: {
  habit: Habit; log: HabitLog | undefined
  onLog: (p: any) => void
}) {
  const value = log?.value ?? 0
  const target = Math.max(1, habit.target_value)
  const cellsToShow = Math.min(target, 12)
  const reached = value >= target

  function setValue(v: number) {
    const n = Math.max(0, Math.min(target * 2, v))  // allow over-fill up to 2x
    onLog({ value: n, completed: n >= target })
  }

  return (
    <div>
      <div className="grid gap-1.5" style={{ gridTemplateColumns: `repeat(${cellsToShow}, minmax(0,1fr))` }}>
        {Array.from({ length: cellsToShow }).map((_, i) => {
          const filled = i < value
          return (
            <button key={i}
              onClick={() => setValue(value === i + 1 ? i : i + 1)}
              className={cn(
                'aspect-square rounded-md border transition-all active:scale-90 flex items-center justify-center',
                filled
                  ? 'border-emerald-400/50 bg-emerald-500/25'
                  : 'border-white/10 bg-white/5 hover:bg-white/10'
              )}>
              {filled && <span className="text-emerald-300 text-xs">✓</span>}
            </button>
          )
        })}
      </div>
      <div className="flex items-center justify-between mt-2">
        <span className={cn('text-xs font-semibold tabular-nums',
          reached ? 'text-emerald-400' : 'text-foreground'
        )}>
          {value} / {target} {habit.unit || ''} {reached && '✓'}
        </span>
        <div className="flex items-center gap-1">
          <button onClick={() => setValue(value - 1)} disabled={value === 0}
            className="h-6 w-6 rounded-md bg-white/5 border border-white/10 text-foreground text-sm disabled:opacity-30 active:scale-90">−</button>
          <button onClick={() => setValue(value + 1)}
            className="h-6 w-6 rounded-md bg-white/5 border border-white/10 text-foreground text-sm active:scale-90">+</button>
        </div>
      </div>
    </div>
  )
}

// --- Duration ---
function DurationWidget({ habit, log, done, onLog }: {
  habit: Habit; log: HabitLog | undefined; done: boolean
  onLog: (p: any) => void
}) {
  const target = habit.time_target_mins || 30
  const [mins, setMins] = useState<number>(log?.duration_mins ?? target)

  useEffect(() => { setMins(log?.duration_mins ?? target) }, [log?.duration_mins, target])

  function save() {
    onLog({
      completed: mins >= target,
      value: mins,
      duration_mins: mins,
    })
  }

  return (
    <div className="flex items-stretch gap-2">
      <div className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-2 flex-1">
        <button onClick={() => setMins(Math.max(0, mins - 5))}
          className="h-7 w-7 rounded-md hover:bg-white/10 text-foreground text-base active:scale-90">−</button>
        <input type="number" value={mins} min={0} max={600}
          onChange={(e) => setMins(Math.max(0, Number(e.target.value) || 0))}
          className="flex-1 bg-transparent text-center text-base font-bold tabular-nums outline-none" />
        <span className="text-xs text-muted-foreground pr-1">min</span>
        <button onClick={() => setMins(mins + 5)}
          className="h-7 w-7 rounded-md hover:bg-white/10 text-foreground text-base active:scale-90">+</button>
      </div>
      <button onClick={save}
        className={cn('rounded-xl px-4 text-sm font-semibold transition-all active:scale-95',
          done
            ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-400'
            : 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20'
        )}>
        {done ? '✓ Saved' : 'Done'}
      </button>
    </div>
  )
}

// --- Subject ---
function SubjectWidget({ habit, log, onLog }: {
  habit: Habit; log: HabitLog | undefined
  onLog: (p: any) => void
}) {
  const todayDelta = log?.value ?? 0
  const [delta, setDelta] = useState<number>(todayDelta)
  useEffect(() => { setDelta(todayDelta) }, [todayDelta])

  const position = habit.subject_position ?? 0
  const total = habit.subject_total ?? 0
  const finished = total > 0 && position >= total
  const pct = total > 0 ? Math.min(100, (position / total) * 100) : 0
  const unit = habit.subject_unit || 'pages'
  const remaining = Math.max(0, total - position + todayDelta)

  function save() {
    onLog({ subject_delta: delta, value: delta, completed: delta > 0 })
  }

  return (
    <div className="rounded-xl border border-white/10 bg-white/3 p-3 space-y-3">
      {/* Subject header */}
      <div>
        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
          {finished ? 'Finished 🏆' : 'Currently'}
        </p>
        <p className="font-bold text-foreground text-sm leading-snug">{habit.subject_name || '—'}</p>
      </div>

      {/* Progress bar */}
      {total > 0 && (
        <div>
          <div className="flex items-center justify-between text-xs mb-1">
            <span className="text-muted-foreground tabular-nums">
              {position} / {total} {unit}
            </span>
            <span className={cn('font-semibold tabular-nums',
              finished ? 'text-emerald-400' : 'text-gold'
            )}>{Math.round(pct)}%</span>
          </div>
          <div className="h-2 w-full rounded-full bg-white/10 overflow-hidden">
            <div className={cn('h-full rounded-full transition-all',
              finished ? 'bg-emerald-400' : 'bg-gradient-to-r from-gold/80 to-gold'
            )} style={{ width: `${pct}%` }} />
          </div>
        </div>
      )}

      {/* Today's delta entry */}
      {!finished && (
        <div className="flex items-stretch gap-2 pt-1">
          <div className="flex items-center gap-1 rounded-xl border border-white/10 bg-white/5 px-1.5 flex-1">
            <button onClick={() => setDelta(Math.max(0, delta - 1))}
              className="h-7 w-7 rounded-md hover:bg-white/10 text-foreground text-base active:scale-90">−</button>
            <input type="number" value={delta} min={0} max={remaining || 9999}
              onChange={(e) => setDelta(Math.max(0, Number(e.target.value) || 0))}
              className="flex-1 bg-transparent text-center text-base font-bold tabular-nums outline-none w-10" />
            <span className="text-[10px] text-muted-foreground pr-1">{unit}/today</span>
            <button onClick={() => setDelta(delta + 1)}
              className="h-7 w-7 rounded-md hover:bg-white/10 text-foreground text-base active:scale-90">+</button>
          </div>
          <button onClick={save}
            className={cn('rounded-xl px-3 text-sm font-semibold transition-all active:scale-95',
              todayDelta > 0
                ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-400'
                : 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20'
            )}>
            {todayDelta > 0 ? `✓ +${todayDelta}` : 'Done'}
          </button>
        </div>
      )}

      {todayDelta > 0 && !finished && (
        <p className="text-[11px] text-emerald-400/80">+{todayDelta} {unit} logged today</p>
      )}
    </div>
  )
}

// ============================================================
// CREATE / EDIT FORM
// ============================================================
function HabitForm({ form, onClose, onSaved, userId }: {
  form: FormState
  onClose: () => void
  onSaved: () => void
  userId: string
}) {
  const [f, setF] = useState<FormState>(form)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isEdit = !!f.id
  useBodyScrollLock(true)

  async function save() {
    if (!f.name.trim()) return
    setSaving(true)
    setError(null)
    const payload: any = {
      userId,
      name: f.name,
      emoji: f.emoji || '⭐',
      type: f.type,
      target_value: f.type === 'counter' ? f.target_value : 1,
      unit: f.type === 'counter' ? f.unit : '',
      time_target_mins: f.type === 'duration' ? f.time_target_mins : 0,
      subject_name: f.type === 'subject' ? f.subject_name : null,
      subject_total: f.type === 'subject' ? f.subject_total : null,
      subject_position: f.type === 'subject' ? f.subject_position : 0,
      subject_unit: f.type === 'subject' ? (f.subject_unit || 'pages') : null,
      schedule_kind: f.schedule_kind,
      schedule_days: f.schedule_kind === 'weekdays' ? f.schedule_days : null,
      weekly_target: f.schedule_kind === 'per_week' ? f.weekly_target : null,
      reminder_time: f.reminder_time || null,
      why: f.why.trim() || null,
      score_weight: f.score_weight,
    }

    const url = isEdit ? `/api/habits/${f.id}` : '/api/habits'
    const method = isEdit ? 'PATCH' : 'POST'
    let res: Response
    try {
      res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    } catch (e: any) {
      setSaving(false)
      setError(`Network error: ${e?.message ?? e}`)
      return
    }

    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      setSaving(false)
      setError(body?.error || `Save failed (${res.status})`)
      return
    }

    // For new habits (not edits), fire AI starter pack in background
    if (!isEdit) {
      try {
        const body = await res.json().catch(() => ({}))
        const newId = body?.habit?.id
        if (newId) {
          fetch('/api/ai/habit-starter', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ habitId: newId }),
          }).catch(() => {})
        }
      } catch {}
    }

    setSaving(false)
    onSaved()
  }

  return (
    <div className="modal-overlay items-end sm:items-center"
      onClick={onClose}>
      <div className="modal-sheet flex flex-col"
        onClick={(e) => e.stopPropagation()}>
        <div className="modal-sheet-scroll flex-1">
        {/* Header */}
        <div className="sticky top-0 bg-[#0f2235] border-b border-white/10 px-5 py-4 flex items-center justify-between">
          <p className="font-bold text-foreground">{isEdit ? 'Edit habit' : 'New habit'}</p>
          <button onClick={onClose} className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center">
            <X size={16} className="text-muted-foreground" />
          </button>
        </div>

        <div className="p-5 space-y-5">
          {/* Emoji + Name */}
          <div className="flex gap-2">
            <input value={f.emoji} onChange={(e) => setF({ ...f, emoji: e.target.value })}
              maxLength={2} className="log-input w-14 text-center text-2xl" />
            <input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })}
              placeholder="Habit name (e.g. Read fiction)" className="log-input flex-1" />
          </div>

          {/* Type selector */}
          <div>
            <label className="section-header mb-2 block">Type</label>
            <div className="grid grid-cols-2 gap-2">
              {(Object.keys(TYPE_META) as HabitType[]).map((t) => (
                <button key={t} onClick={() => setF({ ...f, type: t })}
                  className={cn('rounded-xl border p-3 text-left transition-all',
                    f.type === t
                      ? 'border-primary bg-primary/15'
                      : 'border-white/10 bg-white/5 hover:border-white/20'
                  )}>
                  <p className="text-base">{TYPE_META[t].emoji} <span className="font-semibold text-foreground text-sm">{TYPE_META[t].label}</span></p>
                  <p className="text-[10px] text-muted-foreground mt-0.5 leading-tight">{TYPE_META[t].hint}</p>
                </button>
              ))}
            </div>
          </div>

          {/* Type-specific config */}
          {f.type === 'counter' && (
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="section-header mb-1 block">Daily target</label>
                <input type="number" min={1} value={f.target_value}
                  onChange={(e) => setF({ ...f, target_value: Math.max(1, Number(e.target.value) || 1) })}
                  className="log-input" />
              </div>
              <div>
                <label className="section-header mb-1 block">Unit</label>
                <input value={f.unit} placeholder="glasses, pages…"
                  onChange={(e) => setF({ ...f, unit: e.target.value })} className="log-input" />
              </div>
            </div>
          )}

          {f.type === 'duration' && (
            <div>
              <label className="section-header mb-1 block">Time target — {f.time_target_mins} min</label>
              <input type="range" min={5} max={240} step={5} value={f.time_target_mins || 30}
                onChange={(e) => setF({ ...f, time_target_mins: Number(e.target.value) })}
                className="w-full" />
            </div>
          )}

          {f.type === 'subject' && (
            <div className="space-y-2">
              <input value={f.subject_name} onChange={(e) => setF({ ...f, subject_name: e.target.value })}
                placeholder="Subject (e.g. Atomic Habits — James Clear)" className="log-input" />
              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="section-header mb-1 block">Now at</label>
                  <input type="number" min={0} value={f.subject_position}
                    onChange={(e) => setF({ ...f, subject_position: Math.max(0, Number(e.target.value) || 0) })}
                    className="log-input" />
                </div>
                <div>
                  <label className="section-header mb-1 block">Total</label>
                  <input type="number" min={1} value={f.subject_total}
                    onChange={(e) => setF({ ...f, subject_total: Math.max(0, Number(e.target.value) || 0) })}
                    className="log-input" />
                </div>
                <div>
                  <label className="section-header mb-1 block">Unit</label>
                  <input value={f.subject_unit} placeholder="pages"
                    onChange={(e) => setF({ ...f, subject_unit: e.target.value })} className="log-input" />
                </div>
              </div>
            </div>
          )}

          {/* Schedule */}
          <div>
            <label className="section-header mb-2 block">Schedule</label>
            <div className="grid grid-cols-3 gap-2 mb-2">
              {(['daily', 'weekdays', 'per_week'] as ScheduleKind[]).map((k) => (
                <button key={k} onClick={() => setF({ ...f, schedule_kind: k })}
                  className={cn('rounded-xl border py-2.5 text-xs font-semibold transition-all',
                    f.schedule_kind === k
                      ? 'border-primary bg-primary/15 text-primary'
                      : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                  )}>
                  {k === 'daily' ? 'Daily' : k === 'weekdays' ? 'Specific days' : 'X / week'}
                </button>
              ))}
            </div>

            {f.schedule_kind === 'weekdays' && (
              <div className="flex flex-wrap gap-1.5">
                {WEEKDAY_CODES.map((d) => {
                  const sel = f.schedule_days.includes(d)
                  return (
                    <button key={d}
                      onClick={() => setF({
                        ...f,
                        schedule_days: sel
                          ? f.schedule_days.filter((x) => x !== d)
                          : [...f.schedule_days, d],
                      })}
                      className={cn('rounded-lg border px-3 py-1.5 text-xs font-medium transition-all',
                        sel
                          ? 'border-gold bg-gold/15 text-gold'
                          : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                      )}>
                      {WEEKDAY_LABEL[d]}
                    </button>
                  )
                })}
              </div>
            )}

            {f.schedule_kind === 'per_week' && (
              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">
                  {f.weekly_target}× per week
                </label>
                <input type="range" min={1} max={7} step={1} value={f.weekly_target}
                  onChange={(e) => setF({ ...f, weekly_target: Number(e.target.value) })}
                  className="w-full" />
              </div>
            )}
          </div>

          {/* Reminder */}
          <div>
            <label className="section-header mb-1 block flex items-center gap-1">
              <Bell size={11} /> Reminder (optional)
            </label>
            <input type="time" value={f.reminder_time}
              onChange={(e) => setF({ ...f, reminder_time: e.target.value })}
              className="log-input" />
          </div>

          {/* Score weight */}
          <div>
            <label className="section-header mb-2 block">Score weight</label>
            <div className="flex gap-1.5">
              {[1, 2, 3, 4, 5].map((w) => (
                <button key={w} onClick={() => setF({ ...f, score_weight: w })}
                  className={cn('flex-1 rounded-lg border py-2 text-sm font-bold transition-all',
                    f.score_weight === w
                      ? 'border-gold bg-gold/15 text-gold'
                      : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                  )}>
                  {w}
                </button>
              ))}
            </div>
          </div>

          {/* Why (motivation) */}
          <div>
            <label className="section-header mb-1 block">Why this habit (optional)</label>
            <input value={f.why} onChange={(e) => setF({ ...f, why: e.target.value })}
              placeholder="e.g. Become a faster reader" className="log-input" />
          </div>

          {error && (
            <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-400">
              <p className="font-semibold mb-1">Couldn't save</p>
              <p className="text-red-300/80">{error}</p>
            </div>
          )}

          <div className="flex gap-2 pt-2">
            <button onClick={onClose}
              className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
              Cancel
            </button>
            <button onClick={save} disabled={!f.name.trim() || saving}
              className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                         hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
              {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create habit'}
            </button>
          </div>
        </div>
        </div>
      </div>
    </div>
  )
}
