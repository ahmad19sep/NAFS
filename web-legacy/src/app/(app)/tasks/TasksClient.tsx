'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Plus, X, Trash2, Pencil, Clock, AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'
import {
  computeDailyTasksHistory, computeWeeklyTasksHistory, computeMonthlyTasksHistory,
} from '@/lib/history'
import {
  periodAnchorFor, previousPeriodAnchor, periodLabel, isMissed, isCurrentOrGrace, PRIORITY_RANK,
  minutesUntilDue, formatDueTime,
  type Task, type TaskType, type TaskPriority,
} from '@/lib/tasks'

interface Props {
  tasks: Task[]
  today: string
}

const TABS: { key: TaskType; label: string; emoji: string }[] = [
  { key: 'daily',   label: 'Daily',   emoji: '☀️' },
  { key: 'weekly',  label: 'Weekly',  emoji: '📅' },
  { key: 'monthly', label: 'Monthly', emoji: '🗓️' },
]

const PRIORITY_META: Record<TaskPriority, { label: string; color: string; dot: string }> = {
  high:   { label: 'High',   color: 'text-red-300',    dot: 'bg-red-500' },
  medium: { label: 'Med',    color: 'text-gold',       dot: 'bg-gold' },
  low:    { label: 'Low',    color: 'text-muted-foreground', dot: 'bg-white/30' },
}

export default function TasksClient({ tasks, today }: Props) {
  const router = useRouter()
  const [tab, setTab] = useState<TaskType>('daily')
  const [showCreate, setShowCreate] = useState(false)

  // Create form state
  const [title, setTitle] = useState('')
  const [note, setNote] = useState('')
  const [priority, setPriority] = useState<TaskPriority>('medium')
  const [dueTime, setDueTime] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  // Filter tasks for the current tab
  const tabTasks = useMemo(() => tasks.filter((t) => t.type === tab), [tasks, tab])

  const currentAnchor = periodAnchorFor(tab, today)
  const prevAnchor = previousPeriodAnchor(tab, currentAnchor)

  // Visible: current period + grace (previous period)
  const visible = tabTasks
    .filter((t) => isCurrentOrGrace(t, today))
    .sort((a, b) => {
      // Current before grace
      const aCurr = a.period_date === currentAnchor ? 0 : 1
      const bCurr = b.period_date === currentAnchor ? 0 : 1
      if (aCurr !== bCurr) return aCurr - bCurr
      // Then by priority
      const pa = PRIORITY_RANK[a.priority]
      const pb = PRIORITY_RANK[b.priority]
      if (pa !== pb) return pa - pb
      // Then by status (active before completed)
      const sa = a.status === 'active' ? 0 : 1
      const sb = b.status === 'active' ? 0 : 1
      if (sa !== sb) return sa - sb
      return a.created_at < b.created_at ? 1 : -1
    })

  const currentTasks = visible.filter((t) => t.period_date === currentAnchor)
  const graceTasks = visible.filter((t) => t.period_date === prevAnchor)
  const currentDone = currentTasks.filter((t) => t.status === 'completed').length
  const currentTotal = currentTasks.length
  const currentPct = currentTotal > 0 ? Math.round((currentDone / currentTotal) * 100) : 0

  // History
  const history = useMemo(() => {
    if (tab === 'daily')   return computeDailyTasksHistory(tabTasks, today)
    if (tab === 'weekly')  return computeWeeklyTasksHistory(tabTasks, today, 12)
    return computeMonthlyTasksHistory(tabTasks, today, 6)
  }, [tab, tabTasks, today])

  function selectTab(t: TaskType) {
    setTab(t)
  }

  // ---- API actions ----
  async function createTask() {
    if (!title.trim()) return
    setSaving(true); setFormError(null)
    const res = await fetch('/api/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title, note, priority, type: tab,
        due_time: tab === 'daily' && dueTime ? dueTime : null,
      }),
    })
    setSaving(false)
    if (!res.ok) {
      const b = await res.json().catch(() => ({}))
      setFormError(b?.error || `Save failed (${res.status})`)
      return
    }
    setTitle(''); setNote(''); setPriority('medium'); setDueTime('')
    setShowCreate(false)
    router.refresh()
  }

  async function toggleTask(id: string) {
    await fetch(`/api/tasks/${id}/toggle`, { method: 'POST' })
    router.refresh()
  }
  async function deleteTask(id: string) {
    if (!confirm('Delete this task?')) return
    await fetch(`/api/tasks/${id}`, { method: 'DELETE' })
    router.refresh()
  }

  // Period title (for the aggregate)
  const periodNoun = tab === 'daily' ? "Today's" : tab === 'weekly' ? "This week's" : "This month's"
  const longestLabel = tab === 'daily' ? 'days' : tab === 'weekly' ? 'weeks' : 'months'

  // Longest streak of perfect periods (100%) going back
  const longestStreak = (() => {
    let cur = 0, longest = 0
    for (const d of history) {
      if (d.max === 0) continue
      if (d.pct === 100) { cur++; longest = Math.max(longest, cur) }
      else cur = 0
    }
    return longest
  })()

  return (
    <div className="mx-auto max-w-md px-4 space-y-4 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <div className="flex items-baseline justify-between mt-0.5">
          <h1 className="text-2xl font-bold text-foreground">Tasks</h1>
          <button onClick={() => setShowCreate(!showCreate)}
            className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-3.5 py-2 text-sm font-semibold
                       text-foreground hover:bg-white/10 hover:border-gold/40 transition-all active:scale-95">
            <Plus size={14} className="text-gold" /> New
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-3 gap-2">
        {TABS.map((t) => (
          <button key={t.key} onClick={() => selectTab(t.key)}
            className={cn('rounded-xl border py-2.5 text-sm font-semibold transition-all flex items-center justify-center gap-1.5',
              tab === t.key
                ? 'border-gold/50 bg-gold/10 text-gold'
                : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
            )}>
            <span>{t.emoji}</span>{t.label}
          </button>
        ))}
      </div>

      {/* Aggregate progress card */}
      <div className="nafs-card p-4">
        <div className="flex items-center justify-between mb-2">
          <p className="text-sm font-semibold text-foreground">{periodNoun} progress</p>
          <span className={cn('text-xl font-bold tabular-nums',
            currentPct >= 100 && currentTotal > 0 ? 'text-emerald-400'
            : currentPct >= 50 ? 'text-gold' : 'text-muted-foreground'
          )}>{currentPct}%</span>
        </div>
        <div className="h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
          <div className={cn('h-full rounded-full transition-all',
            currentPct >= 100 && currentTotal > 0 ? 'bg-emerald-400' : 'bg-gold'
          )} style={{ width: `${currentPct}%` }} />
        </div>
        <div className="flex justify-between items-center mt-2 text-xs">
          <span className="text-muted-foreground">{currentDone} / {currentTotal} done</span>
          {longestStreak > 0 && (
            <span className="text-orange-400 font-semibold">🔥 longest perfect: {longestStreak} {longestLabel}</span>
          )}
        </div>
      </div>

      {/* Create form (inline) */}
      {showCreate && (
        <div className="nafs-card p-4 space-y-3 animate-slide-up">
          <p className="text-sm font-semibold text-foreground">
            New {TABS.find((t) => t.key === tab)!.label.toLowerCase()} task
          </p>
          <input value={title} onChange={(e) => setTitle(e.target.value)}
            placeholder="What needs doing?" className="log-input" autoFocus
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && title.trim()) createTask() }}
          />
          <input value={note} onChange={(e) => setNote(e.target.value)}
            placeholder="Note (optional)" className="log-input text-sm" />
          <div>
            <label className="section-header mb-2 block">Priority</label>
            <div className="grid grid-cols-3 gap-2">
              {(['low', 'medium', 'high'] as TaskPriority[]).map((p) => (
                <button key={p} onClick={() => setPriority(p)}
                  className={cn('rounded-lg border py-2 text-xs font-semibold transition-all flex items-center justify-center gap-1.5',
                    priority === p
                      ? p === 'high' ? 'border-red-500/50 bg-red-500/15 text-red-300'
                      : p === 'medium' ? 'border-gold/50 bg-gold/15 text-gold'
                      : 'border-white/20 bg-white/8 text-foreground'
                      : 'border-white/10 bg-white/5 text-muted-foreground'
                  )}>
                  <span className={cn('h-2 w-2 rounded-full', PRIORITY_META[p].dot)} />
                  {PRIORITY_META[p].label}
                </button>
              ))}
            </div>
          </div>

          {/* Due time — daily only */}
          {tab === 'daily' && (
            <div>
              <label className="section-header mb-1.5 block flex items-center gap-1.5">
                <Clock size={11} /> Due time (optional)
              </label>
              <input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)}
                className="log-input" />
              <p className="text-[10px] text-muted-foreground mt-1">
                You'll get a push alert 1 hour before and a high-priority alert 30 min before.
              </p>
            </div>
          )}
          {formError && (
            <p className="text-xs text-red-400">{formError}</p>
          )}
          <div className="flex gap-2">
            <button onClick={() => { setShowCreate(false); setTitle(''); setNote(''); setFormError(null) }}
              className="flex-1 rounded-xl border border-white/10 py-2.5 text-sm text-muted-foreground hover:bg-white/5">
              Cancel
            </button>
            <button onClick={createTask} disabled={!title.trim() || saving}
              className="flex-[2] rounded-xl bg-primary py-2.5 text-sm font-semibold text-white
                         hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
              {saving ? 'Saving…' : 'Create task'}
            </button>
          </div>
        </div>
      )}

      {/* Empty state */}
      {visible.length === 0 && (
        <div className="text-center py-10">
          <p className="text-5xl">{TABS.find((t) => t.key === tab)!.emoji}</p>
          <p className="mt-3 font-semibold text-foreground">No {tab} tasks yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Tap New to add one.</p>
        </div>
      )}

      {/* Current period tasks */}
      {currentTasks.length > 0 && (
        <div className="space-y-2">
          <p className="section-header">{periodLabel(tab, currentAnchor, today)}</p>
          {currentTasks.map((t) => (
            <TaskRow key={t.id} task={t} today={today} onToggle={() => toggleTask(t.id)} onDelete={() => deleteTask(t.id)} />
          ))}
        </div>
      )}

      {/* Grace tasks (previous period) */}
      {graceTasks.length > 0 && (
        <div className="space-y-2">
          <p className="section-header text-muted-foreground/70">
            {periodLabel(tab, prevAnchor, today)} · grace window
          </p>
          {graceTasks.map((t) => (
            <TaskRow key={t.id} task={t} today={today} onToggle={() => toggleTask(t.id)} onDelete={() => deleteTask(t.id)} />
          ))}
        </div>
      )}

      {/* History teaser (period-aware) */}
      <HistoryTeaserCard
        days={history}
        title={tab === 'daily' ? 'Daily tasks history'
              : tab === 'weekly' ? 'Weekly tasks history'
              : 'Monthly tasks history'}
        subtitle={tab === 'daily' ? 'last 30 days · tap for breakdown'
              : tab === 'weekly' ? 'last 12 weeks · tap for breakdown'
              : 'last 6 months · tap for breakdown'}
        href={`/history?tab=tasks&period=${tab}`}
        emoji={tab === 'daily' ? '☀️' : tab === 'weekly' ? '📅' : '🗓️'}
        accent="emerald"
      />
    </div>
  )
}

// ============================================================
// Row
// ============================================================
function TaskRow({ task, today, onToggle, onDelete }: {
  task: Task; today: string
  onToggle: () => void; onDelete: () => void
}) {
  const missed = isMissed(task, today)
  const done = task.status === 'completed'
  const minsLeft = !done && !missed ? minutesUntilDue(task, today) : null
  const dueLabel = formatDueTime(task.due_time)

  // Live re-render every minute so countdown stays accurate
  const [, setTick] = useState(0)
  useEffect(() => {
    if (!task.due_time || done || missed) return
    const id = setInterval(() => setTick((t) => t + 1), 60_000)
    return () => clearInterval(id)
  }, [task.due_time, done, missed])

  return (
    <div className={cn(
      'rounded-2xl border p-3 transition-all flex items-start gap-3',
      done ? 'border-emerald-500/30 bg-emerald-500/8'
        : missed ? 'border-red-500/25 bg-red-500/8'
        : minsLeft !== null && minsLeft <= 30 && minsLeft > 0 ? 'border-red-500/40 bg-red-500/10 animate-pulse-gold'
        : minsLeft !== null && minsLeft <= 60 && minsLeft > 30 ? 'border-orange-500/40 bg-orange-500/8'
        : 'border-white/10 bg-white/5'
    )}>
      {/* Check */}
      <button onClick={onToggle}
        className={cn(
          'mt-0.5 h-6 w-6 rounded-md border-2 flex-shrink-0 flex items-center justify-center transition-all',
          done ? 'border-emerald-400 bg-emerald-400'
            : missed ? 'border-red-400/40 hover:border-red-400/70'
            : 'border-white/25 hover:border-white/50'
        )}>
        {done && <span className="text-white text-xs font-bold">✓</span>}
      </button>

      {/* Body */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className={cn('text-sm font-semibold leading-snug',
            done ? 'text-emerald-300 line-through'
              : missed ? 'text-red-300/90 line-through'
              : 'text-foreground'
          )}>
            {task.title}
          </p>
        </div>
        {task.note && (
          <p className={cn('text-xs mt-0.5', done || missed ? 'text-muted-foreground/60' : 'text-muted-foreground')}>
            {task.note}
          </p>
        )}
        <div className="flex items-center gap-2 mt-1.5 flex-wrap">
          <span className={cn('inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider',
            PRIORITY_META[task.priority].color
          )}>
            <span className={cn('h-1.5 w-1.5 rounded-full', PRIORITY_META[task.priority].dot)} />
            {PRIORITY_META[task.priority].label}
          </span>
          {dueLabel && !done && (
            <span className={cn('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold',
              missed ? 'text-red-400 bg-red-500/10'
              : minsLeft !== null && minsLeft <= 30 && minsLeft > 0 ? 'text-red-300 bg-red-500/20 border border-red-500/30'
              : minsLeft !== null && minsLeft <= 60 && minsLeft > 30 ? 'text-orange-300 bg-orange-500/15 border border-orange-500/30'
              : minsLeft !== null && minsLeft < 0 ? 'text-red-400 bg-red-500/10'
              : 'text-muted-foreground bg-white/5'
            )}>
              {minsLeft !== null && minsLeft <= 60 && minsLeft > 0 && <AlertTriangle size={9} />}
              <Clock size={9} /> {dueLabel}
              {minsLeft !== null && minsLeft > 0 && minsLeft <= 90 && (
                <span className="opacity-80">· {minsLeft}m left</span>
              )}
              {minsLeft !== null && minsLeft <= 0 && !done && (
                <span className="opacity-80">· overdue</span>
              )}
            </span>
          )}
          {missed && (
            <span className="text-[10px] font-bold uppercase tracking-wider text-red-400">· missed</span>
          )}
        </div>
      </div>

      {/* Delete */}
      <button onClick={onDelete}
        className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors">
        <Trash2 size={12} />
      </button>
    </div>
  )
}
