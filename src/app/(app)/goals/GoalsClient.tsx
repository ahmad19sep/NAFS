'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Plus, ChevronDown, ChevronUp, Sparkles, Trash2, Target, Calendar, TrendingUp, Check, Compass, RefreshCw, AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  type GoalType, GOAL_TYPES, GOAL_TYPE_BY_KEY, GOAL_CATEGORIES,
  defaultDeadlineFor, daysUntilDeadline, progressFromMilestones,
} from '@/lib/goals'

interface Milestone {
  id: string
  goal_id: string
  title: string
  done: boolean
  done_at: string | null
  target_date: string | null
}

interface AiAlignment {
  score: number
  doing_well: string
  missing: string
  suggested_action: string
  analyzed_at: string
}

interface Goal {
  id: string
  title: string
  emoji: string
  description: string
  goal_type: GoalType
  category: string | null
  start_date: string
  deadline: string | null
  progress_pct: number
  ai_plan: string | null
  ai_alignment: AiAlignment | null
  linked_habit_ids: string[]
  goal_milestones: Milestone[]
  target_value: number | null
  current_value: number | null
  unit: string | null
  created_at: string
}

interface Habit { id: string; name: string; emoji: string; current_streak: number }

interface Props {
  userId: string
  goals: Goal[]
  habits: Habit[]
  today: string
}

const TYPE_TONE: Record<GoalType, { card: string; chip: string; bar: string }> = {
  weekly:    { card: 'from-cyan-500/10 via-white/3 to-transparent border-cyan-400/20',     chip: 'bg-cyan-500/15 text-cyan-300 border-cyan-400/30',     bar: 'from-cyan-500 to-cyan-300' },
  monthly:   { card: 'from-gold/10 via-white/3 to-transparent border-gold/25',              chip: 'bg-gold/15 text-gold border-gold/30',                  bar: 'from-gold to-yellow-300' },
  quarterly: { card: 'from-pink-500/10 via-white/3 to-transparent border-pink-400/20',     chip: 'bg-pink-500/15 text-pink-300 border-pink-400/30',     bar: 'from-pink-500 to-pink-300' },
  yearly:    { card: 'from-emerald-500/10 via-white/3 to-transparent border-emerald-400/20', chip: 'bg-emerald-500/15 text-emerald-300 border-emerald-400/30', bar: 'from-emerald-500 to-emerald-300' },
  long_term: { card: 'from-violet-500/10 via-white/3 to-transparent border-violet-400/20',  chip: 'bg-violet-500/15 text-violet-300 border-violet-400/30',  bar: 'from-violet-500 to-violet-300' },
}

type TabKey = 'all' | GoalType

export default function GoalsClient({ userId, goals, habits, today }: Props) {
  const router = useRouter()
  const supabase = createClient()

  const [tab, setTab] = useState<TabKey>('all')

  // Create form
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [emoji, setEmoji] = useState('⭐')
  const [description, setDescription] = useState('')
  const [goalType, setGoalType] = useState<GoalType>('monthly')
  const [category, setCategory] = useState<string | null>(null)
  const [deadline, setDeadline] = useState(defaultDeadlineFor('monthly', today))
  const [milestones, setMilestones] = useState([''])
  const [linkedHabits, setLinkedHabits] = useState<string[]>([])
  const [hasNumericTarget, setHasNumericTarget] = useState(false)
  const [targetValue, setTargetValue] = useState('')
  const [currentValue, setCurrentValue] = useState('')
  const [unit, setUnit] = useState('')
  const [creating, setCreating] = useState(false)

  // Per-goal UI
  const [expandedGoal, setExpandedGoal] = useState<string | null>(null)
  const [generatingPlan, setGeneratingPlan] = useState<string | null>(null)
  const [analyzingAlignment, setAnalyzingAlignment] = useState<string | null>(null)

  function selectType(t: GoalType) {
    setGoalType(t)
    setDeadline(defaultDeadlineFor(t, today))
  }

  // ---- create / update ----
  async function createGoal() {
    if (!title.trim()) return
    setCreating(true)
    const { data: goal } = await supabase.from('goals').insert({
      user_id: userId,
      title: title.trim(),
      emoji,
      description: description.trim() || null,
      goal_type: goalType,
      category,
      start_date: today,
      deadline: deadline || null,
      progress_pct: 0,
      linked_habit_ids: linkedHabits,
      target_value: hasNumericTarget && targetValue ? Number(targetValue) : null,
      current_value: hasNumericTarget && currentValue ? Number(currentValue) : null,
      unit: hasNumericTarget && unit.trim() ? unit.trim() : null,
    }).select().single()

    if (goal) {
      const valid = milestones.filter((m) => m.trim())
      if (valid.length > 0) {
        await supabase.from('goal_milestones').insert(
          valid.map((m) => ({ goal_id: goal.id, title: m.trim(), done: false }))
        )
      }
    }

    // Reset
    setCreating(false); setShowCreate(false)
    setTitle(''); setEmoji('⭐'); setDescription(''); setCategory(null)
    setMilestones(['']); setLinkedHabits([])
    setGoalType('monthly'); setDeadline(defaultDeadlineFor('monthly', today))
    setHasNumericTarget(false); setTargetValue(''); setCurrentValue(''); setUnit('')
    router.refresh()
  }

  async function toggleMilestone(milestoneId: string, currentlyDone: boolean, goal: Goal) {
    const newDone = !currentlyDone
    await supabase.from('goal_milestones').update({
      done: newDone,
      done_at: newDone ? new Date().toISOString() : null,
    }).eq('id', milestoneId)

    // Recompute progress
    const updated = goal.goal_milestones.map((m) =>
      m.id === milestoneId ? { ...m, done: newDone } : m
    )
    const pct = progressFromMilestones(updated)
    await supabase.from('goals').update({ progress_pct: pct }).eq('id', goal.id)
    router.refresh()
  }

  async function updateNumericProgress(goalId: string, current: number, target: number | null) {
    const pct = target ? Math.min(100, Math.round((current / target) * 100)) : null
    const update: any = { current_value: current }
    if (pct !== null) update.progress_pct = pct
    await supabase.from('goals').update(update).eq('id', goalId)
    router.refresh()
  }

  async function generateAIPlan(goal: Goal) {
    setGeneratingPlan(goal.id)
    try {
      const res = await fetch('/api/ai/goal-plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: goal.title,
          description: goal.description,
          deadline: goal.deadline,
          linkedHabits: habits.filter((h) => goal.linked_habit_ids?.includes(h.id)).map((h) => h.name),
        }),
      })
      const { plan } = await res.json()
      if (plan) {
        await supabase.from('goals').update({ ai_plan: plan }).eq('id', goal.id)
        router.refresh()
      }
    } catch {}
    setGeneratingPlan(null)
  }

  async function checkAlignment(goal: Goal) {
    setAnalyzingAlignment(goal.id)
    try {
      const res = await fetch('/api/ai/goal-alignment', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ goalId: goal.id }),
      })
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        alert(b?.error || 'Alignment check failed')
      } else {
        router.refresh()
      }
    } catch (e: any) {
      alert(e?.message || 'Network error')
    }
    setAnalyzingAlignment(null)
  }

  async function deleteGoal(id: string) {
    if (!confirm('Delete this goal? Milestones will be removed too.')) return
    await fetch(`/api/goals/${id}`, { method: 'DELETE' })
    router.refresh()
  }

  // ---- Filter + counts ----
  const filtered = useMemo(() => (
    tab === 'all' ? goals : goals.filter((g) => g.goal_type === tab)
  ), [goals, tab])

  const tabCounts: Record<TabKey, number> = useMemo(() => {
    const c: any = { all: goals.length }
    GOAL_TYPES.forEach((t) => { c[t.key] = goals.filter((g) => g.goal_type === t.key).length })
    return c
  }, [goals])

  const avgPct = filtered.length > 0
    ? Math.round(filtered.reduce((s, g) => s + (g.progress_pct ?? 0), 0) / filtered.length)
    : 0

  return (
    <div className="mx-auto max-w-md px-4 space-y-4 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <div className="flex items-baseline justify-between mt-0.5">
          <h1 className="text-2xl font-bold text-foreground">Goals</h1>
          <button onClick={() => setShowCreate(!showCreate)}
            className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-3.5 py-2 text-sm font-semibold
                       text-foreground hover:bg-white/10 hover:border-gold/40 transition-all active:scale-95">
            <Plus size={14} className="text-gold" /> New
          </button>
        </div>
        <p className="text-sm text-muted-foreground mt-0.5">
          {goals.length} goal{goals.length !== 1 ? 's' : ''} · {avgPct}% average
        </p>
      </div>

      {/* Aggregate progress card */}
      <div className="nafs-card p-4">
        <div className="flex items-center justify-between mb-2">
          <p className="text-sm font-semibold text-foreground">
            {tab === 'all' ? "Overall progress" : `${GOAL_TYPE_BY_KEY[tab as GoalType].label} progress`}
          </p>
          <span className={cn('text-xl font-bold tabular-nums',
            avgPct >= 75 ? 'text-emerald-400' : avgPct >= 40 ? 'text-gold' : 'text-muted-foreground'
          )}>{avgPct}%</span>
        </div>
        <div className="h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
          <div className="h-full rounded-full bg-gradient-to-r from-primary via-teal-light to-gold transition-all"
            style={{ width: `${avgPct}%` }} />
        </div>
      </div>

      {/* Type tabs */}
      <div className="-mx-4 px-4 overflow-x-auto scrollbar-hide">
        <div className="flex gap-1.5 min-w-max">
          <TabBtn label={`All`}      count={tabCounts.all} active={tab === 'all'} onClick={() => setTab('all')} />
          {GOAL_TYPES.map((t) => (
            <TabBtn key={t.key} label={t.label} emoji={t.emoji}
              count={tabCounts[t.key]} active={tab === t.key} onClick={() => setTab(t.key)} />
          ))}
        </div>
      </div>

      {/* Create form */}
      {showCreate && (
        <div className="nafs-card p-5 space-y-4 animate-slide-up">
          <p className="font-semibold text-foreground">New goal</p>

          {/* Emoji + title */}
          <div className="flex gap-3">
            <input value={emoji} onChange={(e) => setEmoji(e.target.value)}
              className="log-input w-14 text-center text-2xl" maxLength={2} />
            <input value={title} onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Memorize Surah Yaseen, Launch side project"
              className="log-input flex-1" />
          </div>

          {/* Type selector */}
          <div>
            <label className="section-header mb-2 block">Goal type</label>
            <div className="grid grid-cols-5 gap-1.5">
              {GOAL_TYPES.map((t) => (
                <button key={t.key} onClick={() => selectType(t.key)}
                  className={cn('rounded-lg border py-2 text-[10px] font-semibold transition-all flex flex-col items-center gap-0.5',
                    goalType === t.key
                      ? 'border-gold/50 bg-gold/10 text-gold'
                      : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                  )}>
                  <span className="text-base">{t.emoji}</span>
                  <span>{t.shortLabel}</span>
                </button>
              ))}
            </div>
            <p className="text-[10px] text-muted-foreground mt-1.5">
              {GOAL_TYPE_BY_KEY[goalType].label} · default {GOAL_TYPE_BY_KEY[goalType].defaultDays} days
            </p>
          </div>

          {/* Category */}
          <div>
            <label className="section-header mb-2 block">Category (optional)</label>
            <div className="flex flex-wrap gap-1.5">
              {GOAL_CATEGORIES.map((c) => (
                <button key={c.key} onClick={() => setCategory(category === c.key ? null : c.key)}
                  className={cn('rounded-full border px-3 py-1.5 text-xs font-medium transition-all flex items-center gap-1',
                    category === c.key
                      ? 'border-primary bg-primary/20 text-primary'
                      : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                  )}>
                  <span>{c.emoji}</span>{c.label}
                </button>
              ))}
            </div>
          </div>

          {/* Description */}
          <textarea value={description} onChange={(e) => setDescription(e.target.value)}
            placeholder="What does success look like?" rows={2}
            className="log-input resize-none text-sm" />

          {/* Deadline */}
          <div>
            <label className="section-header mb-1 block flex items-center gap-1.5">
              <Calendar size={11} /> Deadline
            </label>
            <input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)}
              className="log-input" min={today} />
          </div>

          {/* Numeric target toggle */}
          <button type="button" onClick={() => setHasNumericTarget(!hasNumericTarget)}
            className={cn('w-full rounded-xl border p-3 text-left flex items-center gap-3 transition-all',
              hasNumericTarget ? 'border-gold/40 bg-gold/10 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground'
            )}>
            <TrendingUp size={16} />
            <div className="flex-1">
              <p className="font-semibold text-sm">Track a number</p>
              <p className="text-[10px] opacity-70">e.g. Save $5,000 · Read 12 books · Lose 5kg</p>
            </div>
            <div className={cn('h-5 w-9 rounded-full transition-all relative',
              hasNumericTarget ? 'bg-gold' : 'bg-white/15'
            )}>
              <div className={cn('h-5 w-5 rounded-full bg-white transition-transform',
                hasNumericTarget && 'translate-x-4'
              )} />
            </div>
          </button>

          {hasNumericTarget && (
            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Now</label>
                <input type="number" value={currentValue} onChange={(e) => setCurrentValue(e.target.value)}
                  placeholder="0" className="log-input text-sm" />
              </div>
              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Target</label>
                <input type="number" value={targetValue} onChange={(e) => setTargetValue(e.target.value)}
                  placeholder="100" className="log-input text-sm" />
              </div>
              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Unit</label>
                <input value={unit} onChange={(e) => setUnit(e.target.value)}
                  placeholder="$, books…" className="log-input text-sm" />
              </div>
            </div>
          )}

          {/* Milestones */}
          <div>
            <label className="section-header mb-2 block">Milestones</label>
            {milestones.map((m, i) => (
              <div key={i} className="flex gap-2 mb-2">
                <input type="text" value={m}
                  onChange={(e) => { const u = [...milestones]; u[i] = e.target.value; setMilestones(u) }}
                  placeholder={`Step ${i + 1}`}
                  className="log-input flex-1 text-sm" />
                {milestones.length > 1 && (
                  <button type="button" onClick={() => setMilestones(milestones.filter((_, j) => j !== i))}
                    className="text-red-400 text-lg px-2">✕</button>
                )}
              </div>
            ))}
            <button type="button" onClick={() => setMilestones([...milestones, ''])}
              className="text-xs text-gold hover:text-gold-light transition-colors">
              + Add milestone
            </button>
          </div>

          {/* Link habits */}
          {habits.length > 0 && (
            <div>
              <label className="section-header mb-2 block">Link to habits</label>
              <div className="flex flex-wrap gap-2">
                {habits.map((h) => (
                  <button type="button" key={h.id}
                    onClick={() => setLinkedHabits((prev) =>
                      prev.includes(h.id) ? prev.filter((id) => id !== h.id) : [...prev, h.id]
                    )}
                    className={cn('rounded-full border px-3 py-1.5 text-xs font-medium transition-all',
                      linkedHabits.includes(h.id)
                        ? 'border-primary bg-primary/20 text-primary'
                        : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                    )}>
                    {h.emoji} {h.name}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <button onClick={() => setShowCreate(false)}
              className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
              Cancel
            </button>
            <button onClick={createGoal} disabled={!title.trim() || creating}
              className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                         hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
              {creating ? 'Creating…' : 'Create goal'}
            </button>
          </div>
        </div>
      )}

      {/* Empty state */}
      {filtered.length === 0 && !showCreate && (
        <div className="text-center py-14">
          <p className="text-5xl">{tab === 'all' ? '🎯' : GOAL_TYPE_BY_KEY[tab as GoalType].emoji}</p>
          <p className="mt-3 font-semibold text-foreground">
            {tab === 'all' ? 'No goals yet' : `No ${GOAL_TYPE_BY_KEY[tab as GoalType].label.toLowerCase()} goals yet`}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">Tap New to add one.</p>
        </div>
      )}

      {/* Goals list */}
      <div className="space-y-3 pb-4">
        {filtered.map((goal) => (
          <GoalCard key={goal.id} goal={goal} habits={habits} today={today}
            expanded={expandedGoal === goal.id}
            generatingPlan={generatingPlan === goal.id}
            analyzingAlignment={analyzingAlignment === goal.id}
            onToggleExpand={() => setExpandedGoal(expandedGoal === goal.id ? null : goal.id)}
            onToggleMilestone={(mId, done) => toggleMilestone(mId, done, goal)}
            onUpdateNumeric={(v) => updateNumericProgress(goal.id, v, goal.target_value)}
            onGeneratePlan={() => generateAIPlan(goal)}
            onCheckAlignment={() => checkAlignment(goal)}
            onDelete={() => deleteGoal(goal.id)}
          />
        ))}
      </div>
    </div>
  )
}

// ============================================================
// Tab button
// ============================================================
function TabBtn({ label, emoji, count, active, onClick }: {
  label: string; emoji?: string; count: number; active: boolean; onClick: () => void
}) {
  return (
    <button onClick={onClick}
      className={cn('rounded-xl border px-3.5 py-2 text-sm font-semibold transition-all whitespace-nowrap flex items-center gap-1.5',
        active ? 'border-gold/50 bg-gold/10 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
      )}>
      {emoji && <span>{emoji}</span>}
      {label}
      <span className={cn('rounded-full text-[10px] tabular-nums px-1.5 py-0',
        active ? 'bg-gold/20' : 'bg-white/10'
      )}>{count}</span>
    </button>
  )
}

// ============================================================
// Goal card
// ============================================================
function GoalCard({
  goal, habits, today, expanded, generatingPlan, analyzingAlignment,
  onToggleExpand, onToggleMilestone, onUpdateNumeric, onGeneratePlan, onCheckAlignment, onDelete,
}: {
  goal: Goal
  habits: Habit[]
  today: string
  expanded: boolean
  generatingPlan: boolean
  analyzingAlignment: boolean
  onToggleExpand: () => void
  onToggleMilestone: (id: string, done: boolean) => void
  onUpdateNumeric: (newCurrent: number) => void
  onGeneratePlan: () => void
  onCheckAlignment: () => void
  onDelete: () => void
}) {
  const meta = GOAL_TYPE_BY_KEY[goal.goal_type]
  const tone = TYPE_TONE[goal.goal_type]
  const daysLeft = daysUntilDeadline(goal.deadline, today)
  const linkedHabitObjs = habits.filter((h) => goal.linked_habit_ids?.includes(h.id))
  const cat = GOAL_CATEGORIES.find((c) => c.key === goal.category)
  const milestonesDoneCount = goal.goal_milestones.filter((m) => m.done).length
  const isComplete = goal.progress_pct >= 100

  return (
    <div className={cn('relative overflow-hidden rounded-2xl border bg-gradient-to-br', tone.card)}>
      <div className="pointer-events-none absolute -right-6 -top-6 h-24 w-24 rounded-full bg-white/3 blur-2xl" />

      {/* Header (tappable to expand) */}
      <button onClick={onToggleExpand} className="relative w-full p-4 text-left">
        <div className="flex items-start gap-3">
          <span className="text-3xl flex-shrink-0">{goal.emoji}</span>
          <div className="flex-1 min-w-0">
            {/* Type + category chips */}
            <div className="flex items-center gap-1.5 mb-1 flex-wrap">
              <span className={cn('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider', tone.chip)}>
                {meta.emoji} {meta.label}
              </span>
              {cat && (
                <span className="inline-flex items-center gap-0.5 rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[10px] text-muted-foreground">
                  {cat.emoji} {cat.label}
                </span>
              )}
              {isComplete && (
                <span className="inline-flex items-center gap-0.5 text-[10px] text-emerald-400 font-bold">
                  <Check size={10} /> Complete
                </span>
              )}
              {goal.ai_alignment && (
                <span className={cn('inline-flex items-center gap-0.5 rounded-full border px-1.5 py-0.5 text-[10px] font-bold tabular-nums',
                  goal.ai_alignment.score >= 75 ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400'
                  : goal.ai_alignment.score >= 50 ? 'border-gold/30 bg-gold/10 text-gold'
                  : goal.ai_alignment.score >= 25 ? 'border-orange-500/30 bg-orange-500/10 text-orange-400'
                  : 'border-red-500/30 bg-red-500/10 text-red-400'
                )}>
                  <Compass size={9} /> {goal.ai_alignment.score}% aligned
                </span>
              )}
            </div>
            <p className="font-bold text-foreground leading-tight">{goal.title}</p>
            {goal.description && (
              <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">{goal.description}</p>
            )}
            {goal.deadline && (
              <p className={cn('text-[11px] mt-1.5',
                daysLeft !== null && daysLeft < 0 ? 'text-red-400'
                : daysLeft !== null && daysLeft < 7 ? 'text-orange-400'
                : 'text-muted-foreground'
              )}>
                📅 {new Date(goal.deadline + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                {daysLeft !== null && daysLeft > 0 && ` · ${daysLeft} day${daysLeft !== 1 ? 's' : ''} left`}
                {daysLeft !== null && daysLeft === 0 && ' · today'}
                {daysLeft !== null && daysLeft < 0 && ` · ${Math.abs(daysLeft)} day${Math.abs(daysLeft) !== 1 ? 's' : ''} overdue`}
              </p>
            )}
          </div>
          <div className="flex flex-col items-end gap-1 flex-shrink-0">
            <span className={cn('text-lg font-bold tabular-nums',
              isComplete ? 'text-emerald-400' : goal.progress_pct >= 50 ? 'text-gold' : 'text-foreground'
            )}>{goal.progress_pct}%</span>
            <div className="flex items-center gap-1">
              {expanded ? <ChevronUp size={14} className="text-muted-foreground" /> : <ChevronDown size={14} className="text-muted-foreground" />}
              <button onClick={(e) => { e.stopPropagation(); onDelete() }}
                className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors">
                <Trash2 size={11} />
              </button>
            </div>
          </div>
        </div>

        {/* Numeric progress, if set */}
        {goal.target_value != null && (
          <div className="mt-3 flex items-center gap-2 text-[11px]">
            <Target size={11} className="text-gold" />
            <span className="font-semibold tabular-nums text-foreground">
              {goal.current_value ?? 0} / {goal.target_value} {goal.unit ?? ''}
            </span>
          </div>
        )}

        {/* Milestone count */}
        {goal.goal_milestones.length > 0 && (
          <p className="mt-1.5 text-[11px] text-muted-foreground">
            {milestonesDoneCount} / {goal.goal_milestones.length} milestone{goal.goal_milestones.length !== 1 ? 's' : ''} done
          </p>
        )}

        {/* Progress bar */}
        <div className="mt-3 h-2 w-full rounded-full bg-white/10">
          <div className={cn('h-full rounded-full transition-all bg-gradient-to-r', tone.bar)}
            style={{ width: `${goal.progress_pct}%` }} />
        </div>
      </button>

      {/* Expanded content */}
      {expanded && (
        <div className="relative border-t border-white/10 p-4 space-y-4">
          {/* Numeric progress editor */}
          {goal.target_value != null && (
            <div className="space-y-1.5">
              <p className="section-header">Progress</p>
              <div className="flex items-center gap-2">
                <button onClick={() => onUpdateNumeric(Math.max(0, (goal.current_value ?? 0) - 1))}
                  className="h-8 w-8 rounded-lg bg-white/5 border border-white/10 text-foreground font-bold">−</button>
                <input type="number" value={goal.current_value ?? 0}
                  onChange={(e) => onUpdateNumeric(Math.max(0, Number(e.target.value) || 0))}
                  className="log-input flex-1 text-center text-base font-bold" />
                <span className="text-xs text-muted-foreground w-14 text-right">/ {goal.target_value} {goal.unit}</span>
                <button onClick={() => onUpdateNumeric((goal.current_value ?? 0) + 1)}
                  className="h-8 w-8 rounded-lg bg-white/5 border border-white/10 text-foreground font-bold">+</button>
              </div>
            </div>
          )}

          {/* Milestones */}
          {goal.goal_milestones.length > 0 && (
            <div>
              <p className="section-header mb-2">Milestones</p>
              <div className="space-y-1.5">
                {goal.goal_milestones.map((m) => (
                  <button key={m.id}
                    onClick={() => onToggleMilestone(m.id, m.done)}
                    className="flex items-center gap-3 w-full text-left group">
                    <div className={cn('h-5 w-5 rounded flex-shrink-0 border-2 flex items-center justify-center transition-all',
                      m.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30 group-hover:border-white/50'
                    )}>
                      {m.done && <span className="text-white text-xs">✓</span>}
                    </div>
                    <p className={cn('text-sm flex-1', m.done ? 'line-through text-muted-foreground' : 'text-foreground')}>
                      {m.title}
                    </p>
                    {m.done && m.done_at && (
                      <span className="text-[10px] text-emerald-400/70 tabular-nums">
                        {new Date(m.done_at).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Linked habits */}
          {linkedHabitObjs.length > 0 && (
            <div>
              <p className="section-header mb-2">Linked habits</p>
              <div className="flex flex-wrap gap-2">
                {linkedHabitObjs.map((h) => (
                  <div key={h.id} className="flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs">
                    <span>{h.emoji}</span>
                    <span className="text-foreground">{h.name}</span>
                    {h.current_streak > 0 && <span className="text-orange-400">🔥 {h.current_streak}</span>}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* AI Alignment */}
          <AlignmentCard alignment={goal.ai_alignment} loading={analyzingAlignment} onCheck={onCheckAlignment} />

          {/* AI Plan */}
          {goal.ai_plan ? (
            <div>
              <p className="section-header mb-2">AI plan</p>
              <div className="rounded-xl bg-primary/10 border border-primary/20 p-3">
                <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">{goal.ai_plan}</p>
              </div>
            </div>
          ) : (
            <button onClick={onGeneratePlan} disabled={generatingPlan}
              className="flex w-full items-center justify-center gap-2 rounded-xl border border-gold/30
                         bg-gold/5 py-2.5 text-sm font-semibold text-gold
                         hover:bg-gold/10 transition-all disabled:opacity-50">
              <Sparkles size={14} />
              {generatingPlan ? 'Generating plan…' : 'Generate AI plan'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// ============================================================
// AI Alignment card — shows whether real activity matches the goal
// ============================================================
function AlignmentCard({ alignment, loading, onCheck }: {
  alignment: AiAlignment | null
  loading: boolean
  onCheck: () => void
}) {
  if (!alignment) {
    return (
      <button onClick={onCheck} disabled={loading}
        className="flex w-full items-center justify-center gap-2 rounded-xl border border-fuchsia-400/30
                   bg-fuchsia-500/8 py-2.5 text-sm font-semibold text-fuchsia-300
                   hover:bg-fuchsia-500/15 transition-all disabled:opacity-50">
        <Compass size={14} />
        {loading ? 'Analyzing your activity…' : 'Check goal alignment'}
      </button>
    )
  }

  const score = alignment.score
  const tone =
    score >= 75 ? { txt: 'text-emerald-400', card: 'border-emerald-500/30 bg-emerald-500/8', verdict: 'Strong alignment' }
    : score >= 50 ? { txt: 'text-gold', card: 'border-gold/30 bg-gold/8', verdict: 'Some alignment' }
    : score >= 25 ? { txt: 'text-orange-400', card: 'border-orange-500/30 bg-orange-500/8', verdict: 'Weak alignment' }
    : { txt: 'text-red-400', card: 'border-red-500/30 bg-red-500/8', verdict: 'Drifting away' }

  const dt = new Date(alignment.analyzed_at)
  const timeStr = dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) +
                  ' · ' + dt.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })

  return (
    <div className={cn('rounded-xl border p-4 space-y-3', tone.card)}>
      <div className="flex items-start gap-3">
        <Compass size={18} className={cn('mt-0.5', tone.txt)} />
        <div className="flex-1 min-w-0">
          <p className="text-[10px] uppercase tracking-wider text-muted-foreground">AI alignment</p>
          <div className="flex items-baseline gap-2">
            <p className={cn('text-2xl font-bold tabular-nums', tone.txt)}>{score}%</p>
            <p className={cn('text-sm font-bold', tone.txt)}>{tone.verdict}</p>
          </div>
        </div>
        <button onClick={onCheck} disabled={loading}
          className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 flex items-center justify-center text-muted-foreground hover:bg-white/10 transition-colors disabled:opacity-50"
          aria-label="Re-analyze">
          <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>

      {alignment.doing_well && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-emerald-400 mb-0.5">✓ Doing well</p>
          <p className="text-sm text-foreground leading-snug">{alignment.doing_well}</p>
        </div>
      )}
      {alignment.missing && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-red-400 mb-0.5 flex items-center gap-1">
            <AlertTriangle size={9} /> What's missing
          </p>
          <p className="text-sm text-foreground leading-snug">{alignment.missing}</p>
        </div>
      )}
      {alignment.suggested_action && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-gold mb-0.5">→ This week, do this</p>
          <p className="text-sm text-foreground leading-snug font-medium">{alignment.suggested_action}</p>
        </div>
      )}
      <p className="text-[10px] text-muted-foreground/60 pt-1 border-t border-white/5">
        Last analyzed {timeStr}
      </p>
    </div>
  )
}
