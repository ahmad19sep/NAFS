'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Plus, Clock, Star, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Habit {
  id: string
  name: string
  emoji: string
  type: 'boolean' | 'count' | 'duration'
  target_value: number
  time_target_mins: number
  unit: string
  category: string
  score_weight: number
  note_template: string | null   // e.g. "Which book?", "What did you work on?"
  current_streak: number
  longest_streak: number
  sort_order: number
}

interface HabitLog {
  id: string
  habit_id: string
  date: string
  value: number
  completed: boolean
  duration_mins?: number
  notes?: string | null
}

const NOTE_SUGGESTIONS: Record<string, string> = {
  read: 'Which book and which chapter/pages?',
  book: 'Which book and which chapter/pages?',
  quran: 'Which surah and how many pages?',
  code: 'What did you build or work on?',
  work: 'What did you work on?',
  exercise: 'What workout did you do?',
  gym: 'What workout did you do?',
  run: 'Where did you run and how far?',
  study: 'What subject and topic?',
  write: 'What did you write?',
  journal: 'What did you reflect on?',
  meditat: 'What technique?',
  learn: 'What did you learn?',
}

function getSuggestedTemplate(habitName: string): string {
  const lower = habitName.toLowerCase()
  for (const [key, val] of Object.entries(NOTE_SUGGESTIONS)) {
    if (lower.includes(key)) return val
  }
  return ''
}

function getLast14Days(today: string): string[] {
  const days = []
  for (let i = 13; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    days.push(d.toISOString().split('T')[0])
  }
  return days
}

interface Props {
  userId: string
  habits: Habit[]
  logs: HabitLog[]
  today: string
}

export default function HabitsClient({ userId, habits, logs, today }: Props) {
  const router = useRouter()

  // Add form state
  const [showAdd, setShowAdd] = useState(false)
  const [newName, setNewName] = useState('')
  const [newEmoji, setNewEmoji] = useState('⭐')
  const [newType, setNewType] = useState<'boolean' | 'count' | 'duration'>('boolean')
  const [newTimeMins, setNewTimeMins] = useState(30)
  const [newScoreWeight, setNewScoreWeight] = useState(2)
  const [newTargetValue, setNewTargetValue] = useState(1)
  const [newUnit, setNewUnit] = useState('')
  const [newNoteTemplate, setNewNoteTemplate] = useState('')
  const [saving, setSaving] = useState(false)

  // Completion popup state
  const [activePopup, setActivePopup] = useState<string | null>(null)  // habitId
  const [popupNotes, setPopupNotes] = useState('')
  const [popupDuration, setPopupDuration] = useState('')
  const [logging, setLogging] = useState<string | null>(null)

  const last14 = getLast14Days(today)
  const todayLogs = logs.filter((l) => l.date === today)

  function getTodayLog(habitId: string) {
    return todayLogs.find((l) => l.habit_id === habitId)
  }

  function openPopup(habit: Habit) {
    if (logging) return
    const existing = getTodayLog(habit.id)
    if (existing?.completed) {
      // Un-complete
      logHabit(habit.id, false, 0, null, 0)
      return
    }
    setActivePopup(habit.id)
    setPopupNotes(existing?.notes ?? '')
    setPopupDuration(String(habit.time_target_mins || ''))
  }

  async function logHabit(habitId: string, completed: boolean, value: number, notes: string | null, duration_mins: number) {
    setLogging(habitId)
    setActivePopup(null)
    await fetch('/api/habits/log', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ habitId, date: today, completed, value, notes, duration_mins }),
    })
    setLogging(null)
    setPopupNotes('')
    setPopupDuration('')
    router.refresh()
  }

  function confirmPopup(habit: Habit) {
    const mins = Number(popupDuration) || habit.time_target_mins
    const completed = habit.type === 'duration'
      ? mins >= (habit.time_target_mins || 1) * 0.8
      : true
    logHabit(habit.id, completed, habit.type === 'duration' ? mins : habit.target_value,
      popupNotes.trim() || null, mins)
  }

  async function addHabit() {
    if (!newName.trim()) return
    setSaving(true)
    const template = newNoteTemplate.trim() || getSuggestedTemplate(newName)
    await fetch('/api/habits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: newName, emoji: newEmoji, type: newType,
        target_value: newTargetValue, time_target_mins: newTimeMins,
        unit: newUnit, score_weight: newScoreWeight,
        note_template: template || null, userId,
      }),
    })
    setSaving(false)
    setShowAdd(false)
    setNewName(''); setNewEmoji('⭐'); setNewNoteTemplate('')
    router.refresh()
  }

  async function deleteHabit(id: string) {
    if (!confirm('Delete this habit?')) return
    await fetch(`/api/habits/${id}`, { method: 'DELETE' })
    router.refresh()
  }

  const activeHabit = habits.find((h) => h.id === activePopup)
  const islamicHabits = habits.filter((h) => h.category === 'islamic')
  const otherHabits = habits.filter((h) => h.category !== 'islamic')
  const totalMax = habits.reduce((s, h) => s + h.score_weight, 0)
  const totalEarned = habits.reduce((s, h) => getTodayLog(h.id)?.completed ? s + h.score_weight : s, 0)
  const doneCount = todayLogs.filter((l) => l.completed).length

  return (
    <div className="mx-auto max-w-md px-4 space-y-5">

      {/* Header */}
      <div className="flex items-center justify-between pt-3">
        <div>
          <p className="text-xs text-muted-foreground">
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
          </p>
          <h1 className="text-2xl font-bold text-foreground">Habits</h1>
          <p className="text-sm text-muted-foreground">
            {doneCount}/{habits.length} done · {totalEarned}/{totalMax} pts
          </p>
        </div>
        <button onClick={() => setShowAdd(!showAdd)}
          className="flex items-center gap-1.5 rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white
                     hover:bg-teal-light transition-all active:scale-95">
          <Plus size={16} /> Add
        </button>
      </div>

      {/* Add habit form */}
      {showAdd && (
        <div className="nafs-card p-5 space-y-4 animate-slide-up">
          <p className="font-semibold text-foreground">New habit</p>

          {/* Emoji + Name */}
          <div className="flex gap-3">
            <input type="text" value={newEmoji} onChange={(e) => setNewEmoji(e.target.value)}
              className="log-input w-14 text-center text-2xl" maxLength={2} />
            <input type="text" value={newName} onChange={(e) => {
              setNewName(e.target.value)
              setNewNoteTemplate(getSuggestedTemplate(e.target.value))
            }}
              placeholder="Habit name" className="log-input flex-1" />
          </div>

          {/* Type */}
          <div>
            <label className="section-header mb-2 block">Type</label>
            <div className="grid grid-cols-3 gap-2">
              {(['boolean', 'count', 'duration'] as const).map((t) => (
                <button key={t} onClick={() => setNewType(t)}
                  className={cn('rounded-xl border py-2 text-xs font-semibold transition-all',
                    newType === t ? 'border-primary bg-primary/20 text-primary' : 'border-white/10 bg-white/5 text-muted-foreground'
                  )}>
                  {t === 'boolean' ? 'Yes / No' : t === 'count' ? 'Count' : 'Duration'}
                </button>
              ))}
            </div>
          </div>

          {/* Time target */}
          <div>
            <label className="section-header mb-2 block flex items-center gap-1">
              <Clock size={11} /> Time target (mins/day)
            </label>
            <div className="flex items-center gap-3">
              <input type="range" min={0} max={240} step={5} value={newTimeMins}
                onChange={(e) => setNewTimeMins(Number(e.target.value))} className="flex-1" />
              <span className="w-12 text-right font-bold text-gold text-sm tabular-nums">
                {newTimeMins > 0 ? `${newTimeMins}m` : 'None'}
              </span>
            </div>
          </div>

          {/* Score weight */}
          <div>
            <label className="section-header mb-2 block flex items-center gap-1">
              <Star size={11} /> Score weight
            </label>
            <div className="flex gap-2">
              {[1, 2, 3, 4, 5].map((w) => (
                <button key={w} onClick={() => setNewScoreWeight(w)}
                  className={cn('flex-1 rounded-xl border py-2.5 text-sm font-bold transition-all',
                    newScoreWeight === w ? 'border-gold bg-gold/20 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground'
                  )}>
                  {w}
                </button>
              ))}
            </div>
          </div>

          {/* Tracking note template */}
          <div>
            <label className="section-header mb-1 block">What to track daily?</label>
            <input type="text" value={newNoteTemplate}
              onChange={(e) => setNewNoteTemplate(e.target.value)}
              placeholder='e.g. "Which book and chapter?" or "What did you build?"'
              className="log-input text-sm" />
            <p className="text-xs text-muted-foreground mt-1">
              This question appears each time you complete the habit.
            </p>
          </div>

          {newType !== 'boolean' && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="section-header mb-1 block">Target</label>
                <input type="number" value={newTargetValue} min={1}
                  onChange={(e) => setNewTargetValue(Number(e.target.value))} className="log-input" />
              </div>
              <div>
                <label className="section-header mb-1 block">Unit</label>
                <input type="text" value={newUnit} placeholder="pages, glasses…"
                  onChange={(e) => setNewUnit(e.target.value)} className="log-input" />
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <button onClick={() => setShowAdd(false)}
              className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
              Cancel
            </button>
            <button onClick={addHabit} disabled={!newName.trim() || saving}
              className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                         hover:bg-teal-light disabled:opacity-40 active:scale-95">
              {saving ? 'Adding…' : 'Add habit'}
            </button>
          </div>
        </div>
      )}

      {/* Completion popup */}
      {activeHabit && (
        <div className="fixed inset-0 bg-black/70 flex items-end z-50"
          onClick={() => setActivePopup(null)}>
          <div className="w-full max-w-md mx-auto bg-[#0f2235] rounded-t-3xl p-6 space-y-5 shadow-2xl"
            onClick={(e) => e.stopPropagation()}>

            {/* Habit title */}
            <div className="flex items-center gap-3">
              <span className="text-4xl">{activeHabit.emoji}</span>
              <div>
                <p className="text-lg font-bold text-foreground">{activeHabit.name}</p>
                {activeHabit.time_target_mins > 0 && (
                  <p className="text-sm text-muted-foreground">Target: {activeHabit.time_target_mins} min</p>
                )}
              </div>
            </div>

            {/* Duration input if needed */}
            {activeHabit.type === 'duration' && activeHabit.time_target_mins > 0 && (
              <div>
                <label className="section-header mb-2 block">How long did you do it?</label>
                <div className="flex items-center gap-3">
                  <input type="number" value={popupDuration}
                    onChange={(e) => setPopupDuration(e.target.value)}
                    className="log-input flex-1 text-2xl text-center font-bold tabular-nums"
                    min={1} max={600} placeholder={String(activeHabit.time_target_mins)} autoFocus />
                  <span className="text-lg font-medium text-muted-foreground">mins</span>
                </div>
              </div>
            )}

            {/* Note template question */}
            {activeHabit.note_template && (
              <div>
                <label className="section-header mb-2 block">{activeHabit.note_template}</label>
                <input type="text" value={popupNotes}
                  onChange={(e) => setPopupNotes(e.target.value)}
                  placeholder="Type here…"
                  className="log-input"
                  autoFocus={!(activeHabit.type === 'duration' && activeHabit.time_target_mins > 0)}
                  onKeyDown={(e) => e.key === 'Enter' && confirmPopup(activeHabit)}
                />
              </div>
            )}

            {/* If no template, show generic notes */}
            {!activeHabit.note_template && (
              <div>
                <label className="section-header mb-2 block">Add a note (optional)</label>
                <input type="text" value={popupNotes}
                  onChange={(e) => setPopupNotes(e.target.value)}
                  placeholder="What did you do?"
                  className="log-input"
                  onKeyDown={(e) => e.key === 'Enter' && confirmPopup(activeHabit)}
                />
              </div>
            )}

            <div className="flex gap-3">
              <button onClick={() => setActivePopup(null)}
                className="flex-1 rounded-xl border border-white/10 py-3.5 text-sm text-muted-foreground hover:bg-white/5">
                Cancel
              </button>
              <button onClick={() => confirmPopup(activeHabit)}
                className="flex-[2] rounded-xl bg-emerald-500 py-3.5 text-sm font-bold text-white
                           hover:bg-emerald-400 active:scale-95 transition-all">
                ✅ Mark done
              </button>
            </div>
          </div>
        </div>
      )}

      {habits.length === 0 && (
        <div className="text-center py-12">
          <p className="text-5xl">💪</p>
          <p className="mt-3 font-semibold text-foreground">No habits yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Tap Add to create your first habit.</p>
        </div>
      )}

      {/* Islamic habits */}
      {islamicHabits.length > 0 && (
        <div className="space-y-2">
          <p className="section-header">🕌 Deen</p>
          {islamicHabits.map((h) => (
            <HabitCard key={h.id} habit={h} logs={logs} last14={last14} today={today}
              logging={logging} onTap={() => openPopup(h)} onDelete={() => deleteHabit(h.id)} />
          ))}
        </div>
      )}

      {/* Other habits */}
      {otherHabits.length > 0 && (
        <div className="space-y-2 pb-8">
          <p className="section-header">📋 Habits</p>
          {otherHabits.map((h) => (
            <HabitCard key={h.id} habit={h} logs={logs} last14={last14} today={today}
              logging={logging} onTap={() => openPopup(h)} onDelete={() => deleteHabit(h.id)} />
          ))}
        </div>
      )}
    </div>
  )
}

function HabitCard({ habit, logs, last14, today, logging, onTap, onDelete }: {
  habit: Habit; logs: HabitLog[]; last14: string[]; today: string
  logging: string | null; onTap: () => void; onDelete: () => void
}) {
  const todayLog = logs.find((l) => l.habit_id === habit.id && l.date === today)
  const done = todayLog?.completed ?? false
  const notes = todayLog?.notes
  const actualMins = todayLog?.duration_mins

  return (
    <div className={cn(
      'rounded-2xl border p-4 transition-all',
      done
        ? 'border-emerald-500/30 bg-emerald-500/8'
        : 'border-white/10 bg-white/5'
    )}>
      <div className="flex items-center gap-3">

        {/* Checkmark */}
        <button onClick={onTap} disabled={logging === habit.id}
          className={cn(
            'h-12 w-12 flex-shrink-0 rounded-full border-2 flex items-center justify-center transition-all',
            done
              ? 'border-emerald-400 bg-emerald-400 shadow-[0_0_12px_rgba(52,211,153,0.4)]'
              : 'border-white/25 bg-transparent hover:border-white/50',
            logging === habit.id && 'opacity-40'
          )}>
          {done
            ? <span className="text-white text-xl font-bold">✓</span>
            : <span className="text-2xl">{habit.emoji}</span>}
        </button>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Title row */}
          <div className="flex items-center justify-between">
            <p className={cn('font-semibold text-sm', done ? 'text-emerald-300' : 'text-foreground')}>
              {habit.name}
            </p>
            <div className="flex items-center gap-2 flex-shrink-0">
              {habit.current_streak > 0 && (
                <span className="text-xs font-bold text-orange-400">
                  🔥 {habit.current_streak}
                </span>
              )}
              <button onClick={onDelete}
                className="h-6 w-6 rounded-lg flex items-center justify-center text-xs
                           text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-all">
                ✕
              </button>
            </div>
          </div>

          {/* Notes / what was done */}
          {done && notes && (
            <p className="text-xs text-gold mt-0.5 truncate">
              📝 {notes}
            </p>
          )}

          {done && actualMins && actualMins > 0 && (
            <p className="text-xs text-emerald-400/70 mt-0.5">
              ⏱ {actualMins} min
              {habit.time_target_mins > 0 && ` / ${habit.time_target_mins} min target`}
            </p>
          )}

          {/* Template hint when not done */}
          {!done && habit.note_template && (
            <p className="text-xs text-muted-foreground/60 mt-0.5 truncate italic">
              {habit.note_template}
            </p>
          )}

          {/* Meta */}
          <div className="flex items-center gap-3 mt-1.5">
            {habit.time_target_mins > 0 && (
              <span className="text-[10px] text-muted-foreground flex items-center gap-0.5">
                <Clock size={9} /> {habit.time_target_mins}m
              </span>
            )}
            <span className="text-[10px] text-gold/70 flex items-center gap-0.5">
              <Star size={9} /> {habit.score_weight}pt
            </span>
            {habit.longest_streak > 1 && (
              <span className="text-[10px] text-muted-foreground">best {habit.longest_streak}🔥</span>
            )}
          </div>

          {/* 14-day heatmap */}
          <div className="flex gap-0.5 mt-2">
            {last14.map((date) => {
              const log = logs.find((l) => l.habit_id === habit.id && l.date === date)
              const isDone = log?.completed ?? false
              const isToday = date === today
              return (
                <div key={date} title={date}
                  className={cn('h-2.5 flex-1 rounded-sm transition-colors',
                    isDone ? 'bg-emerald-400'
                    : isToday ? 'bg-white/20 ring-1 ring-white/30'
                    : 'bg-white/8'
                  )} />
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
