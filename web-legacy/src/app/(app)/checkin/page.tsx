'use client'

export const dynamic = 'force-dynamic'

import { useState, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Mic, MicOff, Send, Plus, Check } from 'lucide-react'
import { cn, todayString } from '@/lib/utils'

export default function CheckinPage() {
  const router = useRouter()
  const supabase = createClient()

  const [tab, setTab] = useState<'morning' | 'evening'>(() =>
    new Date().getHours() >= 15 ? 'evening' : 'morning'
  )

  const today = todayString()
  const [loading, setLoading] = useState(true)
  const [checkin, setCheckin] = useState<any>(null)

  // Morning state
  const [tasks, setTasks] = useState<{ text: string; done: boolean }[]>([])
  const [newTask, setNewTask] = useState('')

  // Evening state
  const [eveningText, setEveningText] = useState('')
  const [recording, setRecording] = useState(false)
  const [aiVerdict, setAiVerdict] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const mediaRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])

  useEffect(() => {
    async function load() {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) { router.push('/auth'); return }

      const { data } = await supabase
        .from('daily_checkins')
        .select('*')
        .eq('user_id', user.id)
        .eq('date', today)
        .single()

      if (data) {
        setCheckin(data)
        setTasks(data.tasks ?? [])
        setEveningText(data.evening_text ?? '')
        setAiVerdict(data.ai_verdict ?? null)
      }
      setLoading(false)
    }
    load()
  }, [])

  async function persistTasks(updated: { text: string; done: boolean }[]) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    await supabase.from('daily_checkins').upsert({
      user_id: user.id,
      date: today,
      tasks: updated,
      updated_at: new Date().toISOString(),
    }, { onConflict: 'user_id,date' })
  }

  async function saveMorning() {
    await persistTasks(tasks)
    router.push('/dashboard')
  }

  function addTask() {
    if (!newTask.trim()) return
    const updated = [...tasks, { text: newTask.trim(), done: false }]
    setTasks(updated)
    setNewTask('')
    persistTasks(updated)   // auto-save immediately
  }

  function toggleTask(i: number) {
    const updated = [...tasks]
    updated[i] = { ...updated[i], done: !updated[i].done }
    setTasks(updated)
    persistTasks(updated)   // auto-save immediately
  }

  function removeTask(i: number) {
    const updated = tasks.filter((_, j) => j !== i)
    setTasks(updated)
    persistTasks(updated)
  }

  async function submitEvening() {
    if (!eveningText.trim()) return
    setSubmitting(true)

    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    // Get AI verdict
    try {
      const res = await fetch('/api/ai/evening-verdict', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: user.id, eveningText, tasks, date: today }),
      })
      const { verdict } = await res.json()
      setAiVerdict(verdict)

      await supabase.from('daily_checkins').upsert({
        user_id: user.id,
        date: today,
        tasks,
        evening_text: eveningText,
        ai_verdict: verdict,
        updated_at: new Date().toISOString(),
      }, { onConflict: 'user_id,date' })
    } catch {}

    setSubmitting(false)
  }

  async function startRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(stream)
      chunksRef.current = []
      recorder.ondataavailable = (e) => chunksRef.current.push(e.data)
      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop())
        // Convert voice to text using Web Speech API (already captured via SpeechRecognition below)
      }
      mediaRef.current = recorder
      recorder.start()
      setRecording(true)
    } catch {}
  }

  function stopRecording() {
    mediaRef.current?.stop()
    setRecording(false)
  }

  function startSpeechRecognition() {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (!SpeechRecognition) {
      alert('Speech recognition not supported on this browser. Try Chrome on Android.')
      return
    }
    const recognition = new SpeechRecognition()
    recognition.continuous = true
    recognition.interimResults = false
    recognition.lang = 'en-US'
    recognition.onresult = (e: any) => {
      const transcript = Array.from(e.results).map((r: any) => r[0].transcript).join(' ')
      setEveningText((prev) => prev + (prev ? ' ' : '') + transcript)
    }
    recognition.onend = () => setRecording(false)
    recognition.start()
    setRecording(true)
    ;(window as any)._nafsRecognition = recognition
  }

  function stopSpeech() {
    ;(window as any)._nafsRecognition?.stop()
    setRecording(false)
  }

  if (loading) return <div className="flex items-center justify-center min-h-screen"><div className="text-muted-foreground">Loading…</div></div>

  return (
    <div className="mx-auto max-w-md px-4 space-y-6">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
        </p>
        <h1 className="text-2xl font-bold text-foreground">Daily Check-in</h1>
        <p className="text-sm text-muted-foreground tabular-nums">
          {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true })}
        </p>
      </div>

      {/* Tab switcher */}
      <div className="flex rounded-2xl border border-white/10 bg-white/5 p-1">
        <button onClick={() => setTab('morning')}
          className={cn('flex-1 rounded-xl py-2.5 text-sm font-semibold transition-all',
            tab === 'morning' ? 'bg-primary text-white' : 'text-muted-foreground hover:text-foreground'
          )}>
          ☀️ Morning plan
        </button>
        <button onClick={() => setTab('evening')}
          className={cn('flex-1 rounded-xl py-2.5 text-sm font-semibold transition-all',
            tab === 'evening' ? 'bg-primary text-white' : 'text-muted-foreground hover:text-foreground'
          )}>
          🌙 Evening review
        </button>
      </div>

      {/* Morning plan */}
      {tab === 'morning' && (
        <div className="space-y-4">
          <div>
            <p className="font-semibold text-foreground mb-1">What are your top tasks today?</p>
            <p className="text-xs text-muted-foreground">Keep it to 3–5. You&apos;ll review them tonight.</p>
          </div>

          {/* Task list */}
          <div className="space-y-2">
            {tasks.map((task, i) => (
              <button key={i} onClick={() => toggleTask(i)}
                className={cn('flex items-center gap-3 w-full nafs-card p-4 text-left transition-all',
                  task.done && 'border-emerald-500/30 bg-emerald-500/5'
                )}>
                <div className={cn('h-6 w-6 rounded-full border-2 flex-shrink-0 flex items-center justify-center transition-all',
                  task.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30'
                )}>
                  {task.done && <Check size={14} className="text-white" />}
                </div>
                <span className={cn('text-sm flex-1', task.done && 'line-through text-muted-foreground')}>
                  {task.text}
                </span>
                <button onClick={(e) => { e.stopPropagation(); removeTask(i) }}
                  className="text-muted-foreground hover:text-red-400 text-sm">✕</button>
              </button>
            ))}
          </div>

          {/* Add task */}
          {tasks.length < 7 && (
            <div className="flex gap-2">
              <input
                type="text"
                value={newTask}
                onChange={(e) => setNewTask(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && addTask()}
                placeholder="Add a task…"
                className="log-input flex-1"
              />
              <button onClick={addTask} disabled={!newTask.trim()}
                className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-white
                           disabled:opacity-40 hover:bg-teal-light transition-all active:scale-95">
                <Plus size={20} />
              </button>
            </div>
          )}

          {tasks.length === 0 && (
            <p className="text-center text-sm text-muted-foreground py-4">
              Add at least one task to plan your day.
            </p>
          )}

          <button onClick={saveMorning}
            className="w-full rounded-2xl bg-gradient-to-r from-primary to-teal-light py-4
                       font-bold text-white shadow-lg transition-all hover:opacity-90 active:scale-95">
            Save morning plan →
          </button>
        </div>
      )}

      {/* Evening review */}
      {tab === 'evening' && (
        <div className="space-y-5">
          {!aiVerdict ? (
            <>
              <div>
                <p className="font-semibold text-foreground mb-1">Tell me about your day</p>
                <p className="text-xs text-muted-foreground">
                  Speak or type — what did you do, what did you skip, how did you feel?
                  AI will compare it to your goals and habits.
                </p>
              </div>

              {/* Tasks review */}
              {tasks.length > 0 && (
                <div className="nafs-card p-4 space-y-2">
                  <p className="section-header mb-2">Morning tasks — did you do them?</p>
                  {tasks.map((task, i) => (
                    <button key={i} onClick={() => toggleTask(i)}
                      className="flex items-center gap-3 w-full text-left">
                      <div className={cn('h-5 w-5 rounded-full border-2 flex-shrink-0 flex items-center justify-center',
                        task.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30'
                      )}>
                        {task.done && <span className="text-white text-xs">✓</span>}
                      </div>
                      <span className={cn('text-sm', task.done && 'line-through text-muted-foreground')}>{task.text}</span>
                    </button>
                  ))}
                </div>
              )}

              {/* Voice / text input */}
              <div className="relative">
                <textarea
                  value={eveningText}
                  onChange={(e) => setEveningText(e.target.value)}
                  placeholder="Today I worked on… I missed… I felt… The hardest part was…"
                  rows={6}
                  className="log-input resize-none pr-14"
                />
                <button
                  onClick={recording ? stopSpeech : startSpeechRecognition}
                  className={cn(
                    'absolute bottom-3 right-3 flex h-10 w-10 items-center justify-center rounded-xl transition-all',
                    recording
                      ? 'bg-red-500 text-white animate-pulse'
                      : 'bg-primary/20 text-primary hover:bg-primary/30'
                  )}>
                  {recording ? <MicOff size={18} /> : <Mic size={18} />}
                </button>
              </div>

              {recording && (
                <p className="text-center text-sm text-red-400 animate-pulse">🎙️ Recording… tap mic to stop</p>
              )}

              <button
                onClick={submitEvening}
                disabled={!eveningText.trim() || submitting}
                className="w-full rounded-2xl bg-gradient-to-r from-primary to-teal-light py-4
                           font-bold text-white shadow-lg transition-all hover:opacity-90
                           disabled:opacity-40 active:scale-95">
                {submitting ? 'AI is analyzing your day…' : 'Get AI verdict →'}
              </button>
            </>
          ) : (
            /* AI verdict */
            <div className="space-y-4 pb-8">
              <div className="nafs-card border-gold/30 p-5">
                <div className="flex items-center gap-2 mb-3">
                  <span className="text-xl">⚖️</span>
                  <p className="font-bold text-gold">Today&apos;s Verdict</p>
                </div>
                <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">{aiVerdict}</p>
              </div>

              {tasks.length > 0 && (
                <div className="nafs-card p-4">
                  <p className="section-header mb-3">Task completion</p>
                  <div className="flex items-center gap-3">
                    <div className="text-3xl font-bold text-gold tabular-nums">
                      {tasks.filter((t) => t.done).length}/{tasks.length}
                    </div>
                    <div className="flex-1">
                      <div className="h-2 w-full rounded-full bg-white/10">
                        <div className="h-full rounded-full bg-gold transition-all"
                          style={{ width: `${tasks.length ? (tasks.filter((t) => t.done).length / tasks.length) * 100 : 0}%` }} />
                      </div>
                      <p className="text-xs text-muted-foreground mt-1">tasks completed</p>
                    </div>
                  </div>
                </div>
              )}

              <button onClick={() => setAiVerdict(null)}
                className="w-full rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
                Edit evening notes
              </button>
              <button onClick={() => router.push('/dashboard')}
                className="w-full rounded-xl bg-primary py-3 text-sm font-semibold text-white hover:bg-teal-light">
                Back to home
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
