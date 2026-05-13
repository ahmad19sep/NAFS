'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { cn, todayString } from '@/lib/utils'
import { Droplet, Footprints, Moon, Dumbbell, Scale } from 'lucide-react'

export default function HealthPage() {
  const router = useRouter()
  const supabase = createClient()
  const today = todayString()

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const [water, setWater] = useState(0)
  const [steps, setSteps] = useState('')
  const [sleep, setSleep] = useState('')
  const [exercise, setExercise] = useState(false)
  const [exerciseMins, setExerciseMins] = useState('')
  const [weight, setWeight] = useState('')
  const [notes, setNotes] = useState('')

  useEffect(() => {
    async function load() {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return
      const { data } = await supabase.from('health_logs')
        .select('*').eq('user_id', user.id).eq('date', today).single()
      if (data) {
        setWater(data.water_glasses ?? 0)
        setSteps(String(data.steps ?? ''))
        setSleep(String(data.sleep_hours ?? ''))
        setExercise(data.exercise_done ?? false)
        setExerciseMins(String(data.exercise_minutes ?? ''))
        setWeight(String(data.weight_kg ?? ''))
        setNotes(data.notes ?? '')
      }
      setLoading(false)
    }
    load()
  }, [])

  async function save() {
    setSaving(true)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    await supabase.from('health_logs').upsert({
      user_id: user.id,
      date: today,
      water_glasses: water,
      steps: steps ? Number(steps) : null,
      sleep_hours: sleep ? Number(sleep) : null,
      exercise_done: exercise,
      exercise_minutes: exerciseMins ? Number(exerciseMins) : null,
      weight_kg: weight ? Number(weight) : null,
      notes: notes || null,
      updated_at: new Date().toISOString(),
    }, { onConflict: 'user_id,date' })
    setSaving(false)
    router.push('/dashboard')
  }

  if (loading) return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="h-8 w-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
    </div>
  )

  const tracked = [water > 0, !!steps, !!sleep, exercise].filter(Boolean).length

  return (
    <div className="mx-auto max-w-md px-4 space-y-5 pb-8">
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <h1 className="text-2xl font-bold text-foreground">Health</h1>
        <p className="text-sm text-muted-foreground mt-0.5">{tracked}/4 logged today</p>
      </div>

      {/* Water */}
      <div className="nafs-card p-5">
        <div className="flex items-center gap-2 mb-3">
          <Droplet size={18} className="text-blue-400" />
          <p className="font-semibold text-foreground">Water</p>
          <span className="ml-auto text-sm font-bold tabular-nums text-blue-400">{water}/8 glasses</span>
        </div>
        <div className="grid grid-cols-8 gap-1.5">
          {Array.from({ length: 8 }).map((_, i) => (
            <button key={i} onClick={() => setWater(water === i + 1 ? i : i + 1)}
              className={cn('aspect-square rounded-lg border transition-all active:scale-90 flex items-center justify-center text-base',
                i < water ? 'border-blue-400/50 bg-blue-500/20' : 'border-white/10 bg-white/5'
              )}>
              {i < water ? '💧' : ''}
            </button>
          ))}
        </div>
      </div>

      {/* Sleep + Steps */}
      <div className="grid grid-cols-2 gap-3">
        <div className="nafs-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Moon size={16} className="text-purple-400" />
            <p className="text-sm font-semibold text-foreground">Sleep</p>
          </div>
          <input type="number" value={sleep} onChange={(e) => setSleep(e.target.value)}
            placeholder="7.5" step="0.5" min="0" max="24"
            className="log-input text-center text-2xl font-bold" />
          <p className="text-xs text-muted-foreground text-center mt-1">hours</p>
        </div>
        <div className="nafs-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Footprints size={16} className="text-emerald-400" />
            <p className="text-sm font-semibold text-foreground">Steps</p>
          </div>
          <input type="number" value={steps} onChange={(e) => setSteps(e.target.value)}
            placeholder="8000" min="0"
            className="log-input text-center text-2xl font-bold" />
          <p className="text-xs text-muted-foreground text-center mt-1">today</p>
        </div>
      </div>

      {/* Exercise */}
      <div className="nafs-card p-5 space-y-3">
        <button onClick={() => setExercise(!exercise)}
          className={cn('w-full flex items-center gap-3 rounded-xl border p-3 transition-all',
            exercise ? 'border-pink-500/40 bg-pink-500/10' : 'border-white/10 bg-white/5'
          )}>
          <Dumbbell size={20} className={exercise ? 'text-pink-400' : 'text-muted-foreground'} />
          <span className="flex-1 text-left font-semibold text-foreground">
            {exercise ? '✅ Exercised today' : 'Mark exercise done'}
          </span>
        </button>
        {exercise && (
          <div>
            <label className="section-header mb-1 block">Minutes</label>
            <input type="number" value={exerciseMins} onChange={(e) => setExerciseMins(e.target.value)}
              placeholder="30" min="0" className="log-input" />
          </div>
        )}
      </div>

      {/* Weight */}
      <div className="nafs-card p-4">
        <div className="flex items-center gap-2 mb-2">
          <Scale size={16} className="text-gold" />
          <p className="text-sm font-semibold text-foreground">Weight (optional)</p>
        </div>
        <div className="flex items-center gap-2">
          <input type="number" value={weight} onChange={(e) => setWeight(e.target.value)}
            placeholder="75" step="0.1" min="0"
            className="log-input flex-1 text-center text-xl font-bold" />
          <span className="text-muted-foreground">kg</span>
        </div>
      </div>

      {/* Notes */}
      <div>
        <label className="section-header mb-2 block">Notes</label>
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
          placeholder="How are you feeling today?" rows={3}
          className="log-input resize-none" />
      </div>

      <button onClick={save} disabled={saving}
        className="w-full rounded-2xl bg-gradient-to-r from-primary to-teal-light py-4
                   font-bold text-white shadow-lg transition-all hover:opacity-90
                   disabled:opacity-50 active:scale-95">
        {saving ? 'Saving…' : 'Save health log'}
      </button>
    </div>
  )
}
