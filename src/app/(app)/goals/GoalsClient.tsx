'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Plus, ChevronDown, ChevronUp, Sparkles } from 'lucide-react'
import { cn, daysUntil, formatDateShort } from '@/lib/utils'

interface Milestone {
  id: string
  goal_id: string
  title: string
  done: boolean
  target_date: string | null
}

interface Goal {
  id: string
  title: string
  emoji: string
  description: string
  deadline: string | null
  progress_pct: number
  ai_plan: string | null
  linked_habit_ids: string[]
  goal_milestones: Milestone[]
  created_at: string
}

interface Habit {
  id: string
  name: string
  emoji: string
  current_streak: number
}

interface Props {
  userId: string
  goals: Goal[]
  habits: Habit[]
}

export default function GoalsClient({ userId, goals, habits }: Props) {
  const router = useRouter()
  const supabase = createClient()

  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [emoji, setEmoji] = useState('⭐')
  const [description, setDescription] = useState('')
  const [deadline, setDeadline] = useState('')
  const [milestones, setMilestones] = useState([''])
  const [linkedHabits, setLinkedHabits] = useState<string[]>([])
  const [creating, setCreating] = useState(false)
  const [generatingPlan, setGeneratingPlan] = useState<string | null>(null)
  const [expandedGoal, setExpandedGoal] = useState<string | null>(null)

  async function createGoal() {
    if (!title.trim()) return
    setCreating(true)

    const { data: goal } = await supabase.from('goals').insert({
      user_id: userId,
      title,
      emoji,
      description,
      deadline: deadline || null,
      progress_pct: 0,
      linked_habit_ids: linkedHabits,
    }).select().single()

    if (goal) {
      const validMilestones = milestones.filter((m) => m.trim())
      if (validMilestones.length > 0) {
        await supabase.from('goal_milestones').insert(
          validMilestones.map((m) => ({ goal_id: goal.id, title: m, done: false }))
        )
      }
    }

    setCreating(false)
    setShowCreate(false)
    setTitle('')
    setDescription('')
    setDeadline('')
    setMilestones([''])
    setLinkedHabits([])
    router.refresh()
  }

  async function toggleMilestone(milestoneId: string, done: boolean, goal: Goal) {
    await supabase.from('goal_milestones').update({ done: !done }).eq('id', milestoneId)

    // Update progress %
    const totalMs = goal.goal_milestones.length
    const doneMs = goal.goal_milestones.filter((m) => m.id === milestoneId ? !done : m.done).length
    const pct = totalMs > 0 ? Math.round((doneMs / totalMs) * 100) : 0
    await supabase.from('goals').update({ progress_pct: pct }).eq('id', goal.id)
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

  return (
    <div className="mx-auto max-w-md px-4 space-y-6">
      <div className="flex items-center justify-between pt-3">
        <div>
          <p className="text-xs text-muted-foreground">
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
          </p>
          <h1 className="text-2xl font-bold text-foreground">Goals</h1>
          <p className="text-sm text-muted-foreground mt-0.5">{goals.length} goal{goals.length !== 1 ? 's' : ''}</p>
        </div>
        <button onClick={() => setShowCreate(!showCreate)}
          className="flex items-center gap-1.5 rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white
                     transition-all hover:bg-teal-light active:scale-95">
          <Plus size={16} /> New goal
        </button>
      </div>

      {/* Create form */}
      {showCreate && (
        <div className="nafs-card p-5 space-y-4 animate-slide-up">
          <p className="font-semibold text-foreground">New goal</p>

          <div className="flex gap-3">
            <input type="text" value={emoji} onChange={(e) => setEmoji(e.target.value)}
              className="log-input w-16 text-center text-2xl" maxLength={2} />
            <input type="text" value={title} onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Launch my business, Get fit, Memorize Quran"
              className="log-input flex-1" />
          </div>

          <textarea value={description} onChange={(e) => setDescription(e.target.value)}
            placeholder="What does success look like? Be specific."
            rows={3} className="log-input resize-none" />

          <div>
            <label className="section-header mb-1 block">Target date (optional)</label>
            <input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)}
              className="log-input" min={new Date().toISOString().split('T')[0]} />
          </div>

          {/* Milestones */}
          <div>
            <label className="section-header mb-2 block">Milestones</label>
            {milestones.map((m, i) => (
              <div key={i} className="flex gap-2 mb-2">
                <input type="text" value={m}
                  onChange={(e) => {
                    const updated = [...milestones]
                    updated[i] = e.target.value
                    setMilestones(updated)
                  }}
                  placeholder={`Step ${i + 1}`}
                  className="log-input flex-1 text-sm" />
                {milestones.length > 1 && (
                  <button onClick={() => setMilestones(milestones.filter((_, j) => j !== i))}
                    className="text-red-400 text-lg px-2">✕</button>
                )}
              </div>
            ))}
            <button onClick={() => setMilestones([...milestones, ''])}
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
                  <button key={h.id}
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

      {goals.length === 0 && !showCreate && (
        <div className="text-center py-14">
          <p className="text-5xl">⭐</p>
          <p className="mt-3 font-semibold text-foreground">No goals yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Set a goal, add milestones, let AI build your plan.</p>
        </div>
      )}

      {/* Goals list */}
      <div className="space-y-4 pb-8">
        {goals.map((goal) => {
          const isExpanded = expandedGoal === goal.id
          const daysLeft = goal.deadline ? daysUntil(goal.deadline) : null
          const linkedHabitObjs = habits.filter((h) => goal.linked_habit_ids?.includes(h.id))

          return (
            <div key={goal.id} className="nafs-card overflow-hidden">
              {/* Goal header */}
              <button
                onClick={() => setExpandedGoal(isExpanded ? null : goal.id)}
                className="w-full p-5 text-left"
              >
                <div className="flex items-start gap-3">
                  <span className="text-3xl">{goal.emoji}</span>
                  <div className="flex-1 min-w-0">
                    <p className="font-bold text-foreground">{goal.title}</p>
                    {goal.description && (
                      <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">{goal.description}</p>
                    )}
                    {goal.deadline && (
                      <p className={cn('text-xs mt-1', daysLeft !== null && daysLeft < 30 ? 'text-orange-400' : 'text-muted-foreground')}>
                        📅 {new Date(goal.deadline + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                        {daysLeft !== null && daysLeft > 0 && ` · ${daysLeft} days left`}
                        {daysLeft !== null && daysLeft <= 0 && ' · Deadline passed'}
                      </p>
                    )}
                    <p className="text-[10px] text-muted-foreground/60 mt-0.5">
                      Created {new Date(goal.created_at + '').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                    </p>
                  </div>
                  <div className="flex-shrink-0 flex items-center gap-2">
                    <span className="text-sm font-bold text-gold">{goal.progress_pct}%</span>
                    {isExpanded ? <ChevronUp size={16} className="text-muted-foreground" /> : <ChevronDown size={16} className="text-muted-foreground" />}
                    <button
                      onClick={(e) => { e.stopPropagation(); deleteGoal(goal.id) }}
                      className="h-7 w-7 rounded-lg flex items-center justify-center text-xs
                                 text-muted-foreground hover:text-red-400 hover:bg-red-500/10 transition-all">
                      🗑️
                    </button>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="mt-3 h-2 w-full rounded-full bg-white/10">
                  <div className="h-full rounded-full bg-gradient-to-r from-primary to-gold transition-all"
                    style={{ width: `${goal.progress_pct}%` }} />
                </div>
              </button>

              {/* Expanded content */}
              {isExpanded && (
                <div className="border-t border-white/10 p-5 space-y-5">
                  {/* Milestones */}
                  {goal.goal_milestones.length > 0 && (
                    <div>
                      <p className="section-header mb-3">Milestones</p>
                      <div className="space-y-2">
                        {goal.goal_milestones.map((m) => (
                          <button key={m.id}
                            onClick={() => toggleMilestone(m.id, m.done, goal)}
                            className="flex items-center gap-3 w-full text-left group">
                            <div className={cn('h-5 w-5 rounded flex-shrink-0 border-2 flex items-center justify-center transition-all',
                              m.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30 group-hover:border-white/50'
                            )}>
                              {m.done && <span className="text-white text-xs">✓</span>}
                            </div>
                            <p className={cn('text-sm', m.done ? 'line-through text-muted-foreground' : 'text-foreground')}>
                              {m.title}
                            </p>
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

                  {/* AI Plan */}
                  {goal.ai_plan ? (
                    <div>
                      <p className="section-header mb-2">AI plan</p>
                      <div className="rounded-xl bg-primary/10 border border-primary/20 p-4">
                        <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">{goal.ai_plan}</p>
                      </div>
                    </div>
                  ) : (
                    <button
                      onClick={() => generateAIPlan(goal)}
                      disabled={generatingPlan === goal.id}
                      className="flex w-full items-center justify-center gap-2 rounded-xl border border-gold/30
                                 bg-gold/5 py-3 text-sm font-semibold text-gold
                                 hover:bg-gold/10 transition-all disabled:opacity-50">
                      <Sparkles size={16} />
                      {generatingPlan === goal.id ? 'Generating plan…' : 'Generate AI plan'}
                    </button>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )

  async function deleteGoal(id: string) {
    if (!confirm('Delete this goal? Milestones will be removed too.')) return
    await fetch(`/api/goals/${id}`, { method: 'DELETE' })
    router.refresh()
  }
}
