'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { cn, todayString } from '@/lib/utils'
import { Droplet, Footprints, Moon, Sun, Dumbbell, Scale, Ruler, Plus, X, Trash2, EyeOff, Eye, Sparkles, RefreshCw } from 'lucide-react'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'
import { computeHealthHistory } from '@/lib/history'
import { computeBMI, sleepHoursBetween } from '@/lib/bmi'
import {
  type CustomMetric, type CustomMetricType, type ExtrasValues,
  makeMetricId, isMetricDone,
} from '@/lib/health'

// ============================================================
// Page
// ============================================================
export default function HealthPage() {
  const router = useRouter()
  const supabase = createClient()
  const today = todayString()

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [userId, setUserId] = useState<string | null>(null)

  // Profile (one-time)
  const [heightCm, setHeightCm] = useState<number | null>(null)
  const [profileWeight, setProfileWeight] = useState<number | null>(null)
  const [usualSleep, setUsualSleep] = useState<string>('')
  const [usualWake, setUsualWake] = useState<string>('')
  const [extrasConfig, setExtrasConfig] = useState<CustomMetric[]>([])
  const [hiddenDefaults, setHiddenDefaults] = useState<string[]>([])

  // Daily entry
  const [water, setWater] = useState(0)
  const [steps, setSteps] = useState('')
  const [exercise, setExercise] = useState(false)
  const [exerciseMins, setExerciseMins] = useState('')
  const [weight, setWeight] = useState('')
  const [notes, setNotes] = useState('')
  const [extrasValues, setExtrasValues] = useState<ExtrasValues>({})

  // Setup modal
  const [showSetup, setShowSetup] = useState(false)
  const [setupHeight, setSetupHeight] = useState('')
  const [setupWeight, setSetupWeight] = useState('')
  const [setupSleep, setSetupSleep] = useState('22:30')
  const [setupWake, setSetupWake] = useState('06:30')
  const [setupSaving, setSetupSaving] = useState(false)
  const [setupError, setSetupError] = useState<string | null>(null)

  // Add-metric form
  const [showAddMetric, setShowAddMetric] = useState(false)
  const [newMetricName, setNewMetricName] = useState('')
  const [newMetricEmoji, setNewMetricEmoji] = useState('💊')
  const [newMetricType, setNewMetricType] = useState<CustomMetricType>('boolean')
  const [newMetricTarget, setNewMetricTarget] = useState('')
  const [newMetricUnit, setNewMetricUnit] = useState('')

  const [history30, setHistory30] = useState<any[]>([])

  // AI recommendation state
  type AiHealthRec = {
    summary: string
    priorities: string[]
    suggested_goals: Array<{ title: string; type: 'weekly' | 'monthly' | 'yearly'; category: string }>
    suggested_habits: Array<{
      name: string; emoji: string; type: 'simple' | 'counter' | 'duration'
      target_value?: number; unit?: string; time_target_mins?: number
    }>
    generated_at: string
  }
  const [aiRec, setAiRec] = useState<AiHealthRec | null>(null)
  const [aiLoading, setAiLoading] = useState(false)
  const [aiDismissed, setAiDismissed] = useState(false)
  const [adding, setAdding] = useState<string | null>(null)
  const [addedKeys, setAddedKeys] = useState<Set<string>>(new Set())

  useEffect(() => {
    async function load() {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return
      setUserId(user.id)

      const thirtyAgo = new Date()
      thirtyAgo.setDate(thirtyAgo.getDate() - 29)
      const thirtyAgoStr = thirtyAgo.toISOString().split('T')[0]

      const [{ data: profile }, { data: todayLog }, { data: rangeLogs }] = await Promise.all([
        // Use select('*') so missing optional columns don't break the load
        supabase.from('users').select('*').eq('id', user.id).maybeSingle(),
        supabase.from('health_logs').select('*').eq('user_id', user.id).eq('date', today).maybeSingle(),
        supabase.from('health_logs').select('*').eq('user_id', user.id).gte('date', thirtyAgoStr),
      ])

      setHeightCm(profile?.height_cm ?? null)
      setProfileWeight(profile?.weight_kg ?? null)
      setUsualSleep(profile?.usual_sleep_time?.slice(0, 5) ?? '')
      setUsualWake(profile?.usual_wake_time?.slice(0, 5) ?? '')
      setExtrasConfig((profile?.health_extras_config ?? []) as CustomMetric[])
      setHiddenDefaults((profile?.health_defaults_hidden ?? []) as string[])
      setAiRec((profile?.ai_health_recommendation ?? null) as AiHealthRec | null)

      // First-time setup is required only when height OR weight is missing.
      // Sleep schedule is optional and can be set later in profile.
      const isFirstTime = !profile?.height_cm || !profile?.weight_kg
      if (isFirstTime) {
        setShowSetup(true)
        setSetupHeight(String(profile?.height_cm ?? ''))
        setSetupWeight(String(profile?.weight_kg ?? ''))
        setSetupSleep(profile?.usual_sleep_time?.slice(0, 5) ?? '22:30')
        setSetupWake(profile?.usual_wake_time?.slice(0, 5) ?? '06:30')
      }

      if (todayLog) {
        setWater(todayLog.water_glasses ?? 0)
        setSteps(String(todayLog.steps ?? ''))
        setExercise(todayLog.exercise_done ?? false)
        setExerciseMins(String(todayLog.exercise_minutes ?? ''))
        setWeight(String(todayLog.weight_kg ?? ''))
        setNotes(todayLog.notes ?? '')
        setExtrasValues((todayLog.extras ?? {}) as ExtrasValues)
      }
      setHistory30(rangeLogs ?? [])
      setLoading(false)
    }
    load()
  }, [])

  // ---- derived ----
  const bmi = useMemo(
    () => (heightCm && profileWeight ? computeBMI(profileWeight, heightCm) : null),
    [heightCm, profileWeight]
  )
  const usualSleepHrs = useMemo(() => sleepHoursBetween(usualSleep, usualWake), [usualSleep, usualWake])
  const history = useMemo(() => computeHealthHistory(history30, today), [history30, today])

  // ---- actions ----
  async function saveSetup() {
    setSetupError(null)
    const h = Number(setupHeight)
    const w = Number(setupWeight)
    if (!h || h < 80 || h > 250) { setSetupError('Please enter a valid height (80–250 cm)'); return }
    if (!w || w < 20 || w > 400) { setSetupError('Please enter a valid weight (20–400 kg)'); return }
    setSetupSaving(true)

    const { data: { user }, error: authErr } = await supabase.auth.getUser()
    if (authErr || !user) { setSetupSaving(false); setSetupError('Not signed in'); return }

    // Try the full update first (height + weight + sleep). If sleep columns
    // don't exist yet (migration not run), retry with just height + weight.
    const fullPayload: Record<string, any> = { height_cm: h, weight_kg: w }
    if (setupSleep) fullPayload.usual_sleep_time = setupSleep
    if (setupWake)  fullPayload.usual_wake_time  = setupWake

    let { data: updated, error } = await supabase
      .from('users').update(fullPayload).eq('id', user.id)
      .select('height_cm, weight_kg').maybeSingle()

    // Fallback: missing column → retry without sleep fields
    if (error && /column .* does not exist/i.test(error.message)) {
      const retry = await supabase
        .from('users').update({ height_cm: h, weight_kg: w }).eq('id', user.id)
        .select('height_cm, weight_kg').maybeSingle()
      updated = retry.data
      error   = retry.error
    }

    setSetupSaving(false)
    if (error) { setSetupError(error.message); return }
    if (!updated) { setSetupError('Update was blocked. Are you signed in?'); return }

    setHeightCm(updated.height_cm)
    setProfileWeight(updated.weight_kg)
    if (setupSleep) setUsualSleep(setupSleep)
    if (setupWake)  setUsualWake(setupWake)
    setShowSetup(false)

    // Fire-and-forget AI recommendation if one doesn't exist yet
    if (!aiRec) generateAiRecommendation()
  }

  async function generateAiRecommendation() {
    setAiLoading(true)
    try {
      const res = await fetch('/api/ai/health-recommend', { method: 'POST' })
      const data = await res.json().catch(() => ({}))
      if (res.ok && data.recommendation) {
        setAiRec(data.recommendation)
      } else {
        console.warn('health-recommend failed:', data?.error)
      }
    } catch (err) {
      console.warn('health-recommend network error:', err)
    }
    setAiLoading(false)
  }

  async function addSuggestedGoal(g: { title: string; type: 'weekly' | 'monthly' | 'yearly'; category: string }, key: string) {
    setAdding(key)
    const start = today
    const days = g.type === 'weekly' ? 7 : g.type === 'monthly' ? 30 : 365
    const deadline = new Date(today + 'T12:00:00')
    deadline.setDate(deadline.getDate() + days)
    if (!userId) { setAdding(null); return }
    const { error } = await supabase.from('goals').insert({
      user_id: userId,
      title: g.title,
      emoji: '🎯',
      goal_type: g.type,
      category: g.category || 'health',
      start_date: start,
      deadline: deadline.toISOString().split('T')[0],
      progress_pct: 0,
      linked_habit_ids: [],
    })
    setAdding(null)
    if (!error) setAddedKeys((s) => new Set(s).add(key))
  }

  async function addSuggestedHabit(h: AiHealthRec['suggested_habits'][number], key: string) {
    setAdding(key)
    const res = await fetch('/api/habits', {
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
        category: 'health',
        score_weight: 2,
        schedule_kind: 'daily',
      }),
    })
    setAdding(null)
    if (res.ok) setAddedKeys((s) => new Set(s).add(key))
  }

  async function addCustomMetric() {
    if (!newMetricName.trim()) return
    const m: CustomMetric = {
      id: makeMetricId(newMetricName),
      name: newMetricName.trim(),
      emoji: newMetricEmoji || '⭐',
      type: newMetricType,
      target: newMetricType === 'boolean' ? null
            : newMetricTarget ? Number(newMetricTarget) : null,
      unit: newMetricType === 'boolean' ? null : newMetricUnit.trim() || null,
    }
    const next = [...extrasConfig, m]
    setExtrasConfig(next)
    if (userId) {
      await supabase.from('users').update({ health_extras_config: next }).eq('id', userId)
    }
    setShowAddMetric(false)
    setNewMetricName(''); setNewMetricEmoji('💊')
    setNewMetricType('boolean'); setNewMetricTarget(''); setNewMetricUnit('')
  }

  async function removeCustomMetric(id: string) {
    if (!confirm('Remove this metric? Past data is kept in history.')) return
    const next = extrasConfig.filter((m) => m.id !== id)
    setExtrasConfig(next)
    if (userId) {
      await supabase.from('users').update({ health_extras_config: next }).eq('id', userId)
    }
    // Clear today's value too
    const v = { ...extrasValues }
    delete v[id]
    setExtrasValues(v)
  }

  function updateExtra(id: string, value: number | boolean | null) {
    setExtrasValues((prev) => ({ ...prev, [id]: value }))
  }

  async function hideDefault(id: string, label: string) {
    if (!confirm(`Hide "${label}"? You can show it again from the bottom of the page.`)) return
    const next = Array.from(new Set([...hiddenDefaults, id]))
    setHiddenDefaults(next)
    if (userId) {
      await supabase.from('users').update({ health_defaults_hidden: next }).eq('id', userId)
    }
  }
  async function restoreDefaults() {
    setHiddenDefaults([])
    if (userId) {
      await supabase.from('users').update({ health_defaults_hidden: [] }).eq('id', userId)
    }
  }
  const isHidden = (id: string) => hiddenDefaults.includes(id)

  async function save() {
    if (!userId) return
    setSaving(true)
    const dailyWeightNum = weight ? Number(weight) : null

    const { error: logErr } = await supabase.from('health_logs').upsert({
      user_id: userId, date: today,
      water_glasses: water,
      steps: steps ? Number(steps) : null,
      exercise_done: exercise,
      exercise_minutes: exerciseMins ? Number(exerciseMins) : null,
      weight_kg: dailyWeightNum,
      notes: notes || null,
      extras: extrasValues,
      updated_at: new Date().toISOString(),
    }, { onConflict: 'user_id,date' })

    if (!logErr && dailyWeightNum) {
      await supabase.from('users').update({ weight_kg: dailyWeightNum }).eq('id', userId)
      setProfileWeight(dailyWeightNum)
    }

    setSaving(false)
    router.push('/dashboard')
  }

  if (loading) return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="h-8 w-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
    </div>
  )

  // ============================================================
  // First-time setup — blocks everything else until done
  // ============================================================
  if (showSetup) {
    const previewBmi = (() => {
      const h = Number(setupHeight), w = Number(setupWeight)
      return h && w ? computeBMI(w, h) : null
    })()
    const previewSleep = sleepHoursBetween(setupSleep, setupWake)

    return (
      <div className="mx-auto max-w-md px-4 pb-8 pt-5">
        <div className="text-center mb-6">
          <div className="mx-auto h-14 w-14 rounded-2xl bg-red-500/15 border border-red-400/30 flex items-center justify-center text-2xl mb-3">
            ❤️
          </div>
          <h1 className="text-xl font-bold text-foreground">Set up your health profile</h1>
          <p className="text-sm text-muted-foreground mt-1">
            We'll ask once — these power your BMI banner and sleep insights.
          </p>
        </div>

        <div className="nafs-card p-5 space-y-5">
          {/* Height + Weight */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="section-header mb-1.5 block flex items-center gap-1.5">
                <Ruler size={11} /> Height
              </label>
              <div className="flex items-center gap-2">
                <input type="number" value={setupHeight} onChange={(e) => setSetupHeight(e.target.value)}
                  placeholder="170" min={80} max={250}
                  className="log-input flex-1 text-center text-lg font-bold" autoFocus />
                <span className="text-muted-foreground text-xs">cm</span>
              </div>
            </div>
            <div>
              <label className="section-header mb-1.5 block flex items-center gap-1.5">
                <Scale size={11} /> Weight
              </label>
              <div className="flex items-center gap-2">
                <input type="number" value={setupWeight} onChange={(e) => setSetupWeight(e.target.value)}
                  placeholder="70" min={20} max={400} step="0.1"
                  className="log-input flex-1 text-center text-lg font-bold" />
                <span className="text-muted-foreground text-xs">kg</span>
              </div>
            </div>
          </div>

          {previewBmi && (
            <div className={cn('rounded-xl border p-2.5 text-center',
              previewBmi.tone === 'emerald' ? 'border-emerald-500/30 bg-emerald-500/10' :
              previewBmi.tone === 'gold'    ? 'border-gold/30 bg-gold/10' :
              previewBmi.tone === 'orange'  ? 'border-orange-500/30 bg-orange-500/10' :
                                               'border-red-500/30 bg-red-500/10'
            )}>
              <p className="text-xs">
                <span className="text-muted-foreground">BMI · </span>
                <span className="font-bold tabular-nums">{previewBmi.value}</span>
                <span className="ml-1 font-semibold">{previewBmi.emoji} {previewBmi.label}</span>
              </p>
            </div>
          )}

          {/* Sleep schedule (optional) */}
          <div>
            <label className="section-header mb-2 block flex items-center gap-1.5">
              <Moon size={11} /> Usual sleep schedule <span className="text-[10px] text-muted-foreground/70 normal-case tracking-normal font-normal">— optional</span>
            </label>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block flex items-center gap-1">
                  <Moon size={10} /> Slept at
                </label>
                <input type="time" value={setupSleep} onChange={(e) => setSetupSleep(e.target.value)}
                  className="log-input text-center text-base font-semibold" />
              </div>
              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block flex items-center gap-1">
                  <Sun size={10} /> Woke at
                </label>
                <input type="time" value={setupWake} onChange={(e) => setSetupWake(e.target.value)}
                  className="log-input text-center text-base font-semibold" />
              </div>
            </div>
            {previewSleep !== null && (
              <p className={cn('text-[11px] mt-1.5 text-center',
                previewSleep >= 7 && previewSleep <= 9 ? 'text-emerald-400' : 'text-orange-400'
              )}>
                ≈ {previewSleep} hours · {previewSleep >= 7 && previewSleep <= 9 ? 'healthy range' : 'consider 7–9 hrs'}
              </p>
            )}
          </div>

          {setupError && (
            <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-300">
              <p className="font-semibold mb-1">Couldn't save</p>
              <p className="opacity-80">{setupError}</p>
            </div>
          )}

          <button onClick={saveSetup} disabled={setupSaving}
            className="w-full rounded-2xl bg-gradient-to-r from-primary to-teal-light py-3.5
                       font-bold text-white shadow-lg disabled:opacity-50 active:scale-95">
            {setupSaving ? 'Saving…' : 'Continue'}
          </button>
        </div>

        <p className="text-center text-[10px] text-muted-foreground/60 mt-4">
          Change these any time from the Health page.
        </p>
      </div>
    )
  }

  // ============================================================
  // Daily entry view
  // ============================================================
  const visibleDefaultIds = ['water', 'steps', 'exercise'].filter((id) => !isHidden(id))
  const defaultDone: Record<string, boolean> = {
    water: water > 0, steps: !!steps, exercise,
  }
  const tracked = [
    ...visibleDefaultIds.map((id) => defaultDone[id]),
    ...(extrasConfig.map((m) => isMetricDone(m, extrasValues[m.id]))),
  ].filter(Boolean).length
  const total = visibleDefaultIds.length + extrasConfig.length

  return (
    <div className="mx-auto max-w-md px-4 space-y-5 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <h1 className="text-2xl font-bold text-foreground">Health</h1>
        <p className="text-sm text-muted-foreground mt-0.5">{tracked}/{total} logged today</p>
      </div>

      {/* BMI banner */}
      {bmi && (
        <div className={cn(
          'relative overflow-hidden rounded-2xl border p-4 flex items-center gap-4',
          bmi.tone === 'emerald' ? 'border-emerald-500/30 bg-emerald-500/8' :
          bmi.tone === 'gold'    ? 'border-gold/30 bg-gold/8' :
          bmi.tone === 'orange'  ? 'border-orange-500/30 bg-orange-500/8' :
                                    'border-red-500/30 bg-red-500/8'
        )}>
          <div className={cn(
            'h-14 w-14 rounded-2xl flex items-center justify-center text-3xl flex-shrink-0 border',
            bmi.tone === 'emerald' ? 'bg-emerald-500/15 border-emerald-500/30' :
            bmi.tone === 'gold'    ? 'bg-gold/15 border-gold/30' :
            bmi.tone === 'orange'  ? 'bg-orange-500/15 border-orange-500/30' :
                                      'bg-red-500/15 border-red-500/30'
          )}>{bmi.emoji}</div>
          <div className="flex-1 min-w-0">
            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
              BMI · {profileWeight}kg / {heightCm}cm
              {usualSleepHrs !== null && ` · sleeps ~${usualSleepHrs}h`}
            </p>
            <div className="flex items-baseline gap-2">
              <p className={cn('text-3xl font-bold tabular-nums',
                bmi.tone === 'emerald' ? 'text-emerald-400' :
                bmi.tone === 'gold'    ? 'text-gold' :
                bmi.tone === 'orange'  ? 'text-orange-400' :
                                          'text-red-400'
              )}>{bmi.value}</p>
              <p className="text-sm font-bold text-foreground">{bmi.label}</p>
            </div>
            <p className="text-[10px] text-muted-foreground/80 mt-0.5">Healthy range: 18.5 – 24.9</p>
          </div>
          <button
            onClick={() => {
              setSetupHeight(String(heightCm ?? ''))
              setSetupWeight(String(profileWeight ?? ''))
              setSetupSleep(usualSleep || '22:30')
              setSetupWake(usualWake || '06:30')
              setShowSetup(true)
            }}
            className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 flex items-center justify-center text-muted-foreground hover:bg-white/10 transition-colors flex-shrink-0">
            <Ruler size={13} />
          </button>
        </div>
      )}

      {/* AI Health recommendation */}
      <AiHealthCard
        rec={aiRec}
        loading={aiLoading}
        dismissed={aiDismissed}
        onGenerate={() => { setAiDismissed(false); generateAiRecommendation() }}
        onDismiss={() => setAiDismissed(true)}
        onShow={() => setAiDismissed(false)}
        onAddGoal={addSuggestedGoal}
        onAddHabit={addSuggestedHabit}
        addingKey={adding}
        addedKeys={addedKeys}
      />

      {/* History teaser */}
      <HistoryTeaserCard
        days={history}
        title="Health history"
        href="/history?tab=health"
        emoji="❤️"
        accent="red"
      />

      {/* ---------- Default daily metrics ---------- */}

      {/* Water */}
      {!isHidden('water') && (
        <div className="nafs-card p-5">
          <div className="flex items-center gap-2 mb-3">
            <Droplet size={18} className="text-blue-400" />
            <p className="font-semibold text-foreground">Water</p>
            <span className={cn('ml-auto text-sm font-bold tabular-nums', water >= 8 ? 'text-emerald-400' : 'text-blue-400')}>
              {water}/8 glasses {water >= 8 && '✓'}
            </span>
            <button onClick={() => hideDefault('water', 'Water')}
              className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              aria-label="Hide water">
              <Trash2 size={11} />
            </button>
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
          <div className="mt-3 h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
            <div className={cn('h-full rounded-full transition-all',
              water >= 8 ? 'bg-emerald-400' : 'bg-blue-400'
            )} style={{ width: `${Math.min(100, (water / 8) * 100)}%` }} />
          </div>
        </div>
      )}

      {/* Steps */}
      {!isHidden('steps') && (() => {
        const stepsNum = steps ? Number(steps) : 0
        const stepsTarget = 8000
        const stepsPct = Math.min(100, (stepsNum / stepsTarget) * 100)
        const stepsHit = stepsNum >= stepsTarget
        return (
          <div className="nafs-card p-4">
            <div className="flex items-center gap-2 mb-2">
              <Footprints size={16} className="text-emerald-400" />
              <p className="text-sm font-semibold text-foreground">Steps</p>
              <span className={cn('ml-auto text-xs font-bold tabular-nums', stepsHit ? 'text-emerald-400' : 'text-muted-foreground')}>
                {stepsNum.toLocaleString()} / {stepsTarget.toLocaleString()} {stepsHit && '✓'}
              </span>
              <button onClick={() => hideDefault('steps', 'Steps')}
                className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                aria-label="Hide steps">
                <Trash2 size={11} />
              </button>
            </div>
            <input type="number" value={steps} onChange={(e) => setSteps(e.target.value)}
              placeholder="8000" min="0"
              className="log-input text-center text-2xl font-bold" />
            <div className="mt-3 h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
              <div className={cn('h-full rounded-full transition-all',
                stepsHit ? 'bg-emerald-400' : 'bg-emerald-500/70'
              )} style={{ width: `${stepsPct}%` }} />
            </div>
          </div>
        )
      })()}

      {/* Exercise */}
      {!isHidden('exercise') && (() => {
        const exMins = exerciseMins ? Number(exerciseMins) : 0
        const exTarget = 30
        const exPct = Math.min(100, (exMins / exTarget) * 100)
        const exHit = exMins >= exTarget
        return (
          <div className="nafs-card p-5 space-y-3">
            <div className="flex items-center gap-2">
              <button onClick={() => setExercise(!exercise)}
                className={cn('flex-1 flex items-center gap-3 rounded-xl border p-3 transition-all',
                  exercise ? 'border-pink-500/40 bg-pink-500/10' : 'border-white/10 bg-white/5'
                )}>
                <Dumbbell size={20} className={exercise ? 'text-pink-400' : 'text-muted-foreground'} />
                <span className="flex-1 text-left font-semibold text-foreground">
                  {exercise ? '✅ Exercised today' : 'Mark exercise done'}
                </span>
              </button>
              <button onClick={() => hideDefault('exercise', 'Exercise')}
                className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                aria-label="Hide exercise">
                <Trash2 size={11} />
              </button>
            </div>
            {exercise && (
              <div>
                <div className="flex items-baseline justify-between mb-1">
                  <label className="section-header">Minutes</label>
                  <span className={cn('text-xs font-bold tabular-nums',
                    exHit ? 'text-emerald-400' : 'text-pink-400'
                  )}>{exMins} / {exTarget} min {exHit && '✓'}</span>
                </div>
                <input type="number" value={exerciseMins} onChange={(e) => setExerciseMins(e.target.value)}
                  placeholder="30" min="0" className="log-input" />
                <div className="mt-2 h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
                  <div className={cn('h-full rounded-full transition-all',
                    exHit ? 'bg-emerald-400' : 'bg-pink-400'
                  )} style={{ width: `${exPct}%` }} />
                </div>
              </div>
            )}
          </div>
        )
      })()}

      {/* ---------- Custom metrics ---------- */}
      <div className="nafs-card p-4 space-y-2">
        <div className="flex items-center justify-between mb-1">
          <p className="section-header">My metrics</p>
          <button onClick={() => setShowAddMetric(!showAddMetric)}
            className="text-xs text-gold flex items-center gap-1 hover:text-gold-light">
            <Plus size={12} /> Add metric
          </button>
        </div>
        {extrasConfig.length === 0 && !showAddMetric && (
          <p className="text-xs text-muted-foreground text-center py-2">
            Track anything — vitamins, coffee, headache, mood. Tap +Add metric.
          </p>
        )}

        {/* Add form */}
        {showAddMetric && (
          <div className="space-y-2 p-3 rounded-xl border border-white/10 bg-white/5">
            <div className="flex gap-2">
              <input value={newMetricEmoji} onChange={(e) => setNewMetricEmoji(e.target.value)}
                maxLength={2} className="log-input w-12 text-center text-lg" />
              <input value={newMetricName} onChange={(e) => setNewMetricName(e.target.value)}
                placeholder="Metric name (e.g. Vitamins)" className="log-input flex-1 text-sm" />
            </div>
            <div>
              <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Type</label>
              <div className="grid grid-cols-3 gap-1.5">
                {(['boolean', 'counter', 'number'] as CustomMetricType[]).map((t) => (
                  <button key={t} onClick={() => setNewMetricType(t)}
                    className={cn('rounded-lg border py-2 text-xs font-semibold',
                      newMetricType === t ? 'border-primary bg-primary/20 text-primary' : 'border-white/10 bg-white/5 text-muted-foreground'
                    )}>
                    {t === 'boolean' ? 'Yes/No' : t === 'counter' ? 'Counter' : 'Number'}
                  </button>
                ))}
              </div>
            </div>
            {newMetricType !== 'boolean' && (
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Target (optional)</label>
                  <input type="number" value={newMetricTarget} onChange={(e) => setNewMetricTarget(e.target.value)}
                    placeholder="3" className="log-input text-sm" />
                </div>
                <div>
                  <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Unit (optional)</label>
                  <input value={newMetricUnit} onChange={(e) => setNewMetricUnit(e.target.value)}
                    placeholder="cups, mg…" className="log-input text-sm" />
                </div>
              </div>
            )}
            <div className="flex gap-2 pt-1">
              <button onClick={() => setShowAddMetric(false)}
                className="flex-1 rounded-lg border border-white/10 py-2 text-xs text-muted-foreground">Cancel</button>
              <button onClick={addCustomMetric} disabled={!newMetricName.trim()}
                className="flex-1 rounded-lg bg-primary py-2 text-xs font-semibold text-white disabled:opacity-40">
                Add
              </button>
            </div>
          </div>
        )}

        {extrasConfig.map((m) => (
          <ExtraMetricRow key={m.id} metric={m}
            value={extrasValues[m.id]}
            onChange={(v) => updateExtra(m.id, v)}
            onRemove={() => removeCustomMetric(m.id)} />
        ))}
      </div>

      {/* Weight (daily check-in) */}
      {!isHidden('weight') && (
        <div className="nafs-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Scale size={16} className="text-gold" />
            <p className="text-sm font-semibold text-foreground">Today's weight (optional)</p>
            <button onClick={() => hideDefault('weight', 'Weight')}
              className="ml-auto h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              aria-label="Hide weight">
              <Trash2 size={11} />
            </button>
          </div>
          <div className="flex items-center gap-2">
            <input type="number" value={weight} onChange={(e) => setWeight(e.target.value)}
              placeholder={String(profileWeight ?? '75')} step="0.1" min="0"
              className="log-input flex-1 text-center text-xl font-bold" />
            <span className="text-muted-foreground">kg</span>
          </div>
          <p className="text-[10px] text-muted-foreground/70 mt-1">Logging weight updates your BMI banner.</p>
        </div>
      )}

      {/* Restore hidden */}
      {hiddenDefaults.length > 0 && (
        <button onClick={restoreDefaults}
          className="w-full flex items-center justify-center gap-2 rounded-xl border border-white/10 bg-white/3
                     px-4 py-3 text-xs text-muted-foreground hover:bg-white/8 hover:text-foreground transition-all">
          <Eye size={12} /> Show {hiddenDefaults.length} hidden metric{hiddenDefaults.length === 1 ? '' : 's'}
        </button>
      )}

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

// ============================================================
// Custom metric row
// ============================================================
// ============================================================
// AI health recommendation card
// ============================================================
function AiHealthCard({
  rec, loading, dismissed, onGenerate, onDismiss, onShow, onAddGoal, onAddHabit, addingKey, addedKeys,
}: {
  rec: any | null
  loading: boolean
  dismissed: boolean
  onGenerate: () => void
  onDismiss: () => void
  onShow: () => void
  onAddGoal: (g: any, key: string) => void
  onAddHabit: (h: any, key: string) => void
  addingKey: string | null
  addedKeys: Set<string>
}) {
  if (rec && dismissed) {
    return (
      <button onClick={onShow}
        className="flex w-full items-center justify-center gap-2 rounded-2xl border border-fuchsia-400/20
                   bg-fuchsia-500/8 py-2.5 text-xs font-semibold text-fuchsia-300
                   hover:bg-fuchsia-500/12 transition-all">
        <Sparkles size={12} />
        Show AI health plan
      </button>
    )
  }

  if (!rec && !loading) {
    return (
      <button onClick={onGenerate}
        className="flex w-full items-center justify-center gap-2 rounded-2xl border border-fuchsia-400/30
                   bg-gradient-to-br from-fuchsia-500/15 to-purple-700/10 py-3 text-sm font-semibold text-fuchsia-300
                   hover:from-fuchsia-500/20 hover:to-purple-700/15 transition-all">
        <Sparkles size={14} />
        Get AI health recommendations
      </button>
    )
  }

  if (loading && !rec) {
    return (
      <div className="rounded-2xl border border-fuchsia-400/30 bg-fuchsia-500/8 p-5 flex items-center gap-3">
        <RefreshCw size={16} className="animate-spin text-fuchsia-400" />
        <p className="text-sm text-fuchsia-300">Analyzing your health profile…</p>
      </div>
    )
  }

  const dt = new Date(rec.generated_at)
  const dateStr = dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })

  return (
    <div className="relative overflow-hidden rounded-2xl border border-fuchsia-400/30
                    bg-gradient-to-br from-fuchsia-500/12 via-purple-700/8 to-transparent p-5 space-y-4">
      <div className="pointer-events-none absolute -right-6 -top-6 h-24 w-24 rounded-full bg-fuchsia-500/15 blur-2xl" />

      {/* Header */}
      <div className="relative flex items-start gap-3">
        <div className="h-10 w-10 rounded-xl bg-fuchsia-500/20 border border-fuchsia-400/30 flex items-center justify-center text-lg">
          🧠
        </div>
        <div className="flex-1">
          <p className="text-[10px] uppercase tracking-wider text-muted-foreground">AI health plan · {dateStr}</p>
          <p className="text-sm font-bold text-foreground leading-snug mt-0.5">Your personalized starting point</p>
        </div>
        <div className="flex items-center gap-1 flex-shrink-0">
          <button onClick={onGenerate} disabled={loading}
            className="h-10 w-10 rounded-lg border border-white/10 bg-white/5 flex items-center justify-center text-muted-foreground hover:bg-white/10 transition-colors disabled:opacity-50"
            aria-label="Re-generate">
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          </button>
          <button onClick={onDismiss}
            className="h-10 w-10 rounded-lg border border-white/10 bg-white/5 flex items-center justify-center text-muted-foreground hover:text-red-400 hover:bg-red-500/10 transition-colors"
            aria-label="Close">
            <X size={16} />
          </button>
        </div>
      </div>

      {/* Summary */}
      <p className="relative text-sm text-foreground leading-relaxed">{rec.summary}</p>

      {/* Priorities */}
      {rec.priorities?.length > 0 && (
        <div className="relative">
          <p className="text-[10px] uppercase tracking-wider text-fuchsia-300 mb-1.5">Focus areas</p>
          <ul className="space-y-1">
            {rec.priorities.map((p: string, i: number) => (
              <li key={i} className="text-xs text-foreground flex gap-2">
                <span className="text-fuchsia-400">•</span>
                <span className="leading-snug">{p}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Suggested goals */}
      {rec.suggested_goals?.length > 0 && (
        <div className="relative">
          <p className="text-[10px] uppercase tracking-wider text-gold mb-2">Suggested goals</p>
          <div className="space-y-2">
            {rec.suggested_goals.map((g: any, i: number) => {
              const key = `g-${i}-${g.title}`
              const added = addedKeys.has(key)
              return (
                <div key={key} className="rounded-xl border border-white/10 bg-white/5 p-3 flex items-center gap-2">
                  <span className="text-lg">🎯</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-foreground truncate">{g.title}</p>
                    <p className="text-[10px] text-muted-foreground capitalize">{g.type} · {g.category}</p>
                  </div>
                  <button onClick={() => onAddGoal(g, key)} disabled={added || addingKey === key}
                    className={cn('rounded-lg px-3 py-1.5 text-xs font-semibold transition-all flex-shrink-0',
                      added ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        : 'bg-primary text-white hover:bg-teal-light disabled:opacity-50'
                    )}>
                    {added ? '✓ Added' : addingKey === key ? '…' : '+ Add'}
                  </button>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Suggested habits */}
      {rec.suggested_habits?.length > 0 && (
        <div className="relative">
          <p className="text-[10px] uppercase tracking-wider text-cyan-300 mb-2">Suggested habits</p>
          <div className="space-y-2">
            {rec.suggested_habits.map((h: any, i: number) => {
              const key = `h-${i}-${h.name}`
              const added = addedKeys.has(key)
              return (
                <div key={key} className="rounded-xl border border-white/10 bg-white/5 p-3 flex items-center gap-2">
                  <span className="text-lg">{h.emoji || '⭐'}</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-foreground truncate">{h.name}</p>
                    <p className="text-[10px] text-muted-foreground">
                      {h.type === 'simple' ? 'Yes/No · daily'
                        : h.type === 'counter' ? `${h.target_value ?? '?'} ${h.unit ?? ''} · daily`
                        : `${h.time_target_mins ?? '?'} min · daily`}
                    </p>
                  </div>
                  <button onClick={() => onAddHabit(h, key)} disabled={added || addingKey === key}
                    className={cn('rounded-lg px-3 py-1.5 text-xs font-semibold transition-all flex-shrink-0',
                      added ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        : 'bg-primary text-white hover:bg-teal-light disabled:opacity-50'
                    )}>
                    {added ? '✓ Added' : addingKey === key ? '…' : '+ Add'}
                  </button>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

function ExtraMetricRow({ metric, value, onChange, onRemove }: {
  metric: CustomMetric
  value: number | boolean | null | undefined
  onChange: (v: number | boolean | null) => void
  onRemove: () => void
}) {
  const done = isMetricDone(metric, value)

  if (metric.type === 'boolean') {
    const v = value === true
    return (
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(!v)}
          className={cn('flex-1 flex items-center gap-3 rounded-xl border p-3 transition-all active:scale-95',
            v ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
          )}>
          <span className="text-lg">{metric.emoji}</span>
          <div className="flex-1 text-left">
            <p className={cn('font-semibold text-sm', v ? 'text-emerald-300' : 'text-foreground')}>
              {metric.name}
            </p>
            <p className="text-[10px] text-muted-foreground mt-0.5">
              {v ? '✓ Done' : 'Tap when done'}
            </p>
          </div>
          <div className={cn('h-6 w-6 rounded-full border-2 flex items-center justify-center',
            v ? 'border-emerald-400 bg-emerald-400' : 'border-white/20'
          )}>
            {v && <span className="text-white text-xs">✓</span>}
          </div>
        </button>
        <button onClick={onRemove}
          className="h-9 w-9 rounded-lg text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 flex items-center justify-center">
          <Trash2 size={12} />
        </button>
      </div>
    )
  }

  // counter / number
  const n = typeof value === 'number' ? value : 0
  const showStepper = metric.type === 'counter'
  return (
    <div className={cn('rounded-xl border p-3 transition-all',
      done ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
    )}>
      <div className="flex items-center gap-3">
        <span className="text-lg">{metric.emoji}</span>
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm text-foreground truncate">{metric.name}</p>
          <p className="text-[10px] text-muted-foreground">
            {n} {metric.target ? ` / ${metric.target}` : ''} {metric.unit ?? ''} {done && '✓'}
          </p>
        </div>
        {showStepper && (
          <>
            <button onClick={() => onChange(Math.max(0, n - 1))}
              className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 text-foreground font-bold">−</button>
            <span className="font-bold tabular-nums text-gold w-8 text-center">{n}</span>
            <button onClick={() => onChange(n + 1)}
              className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 text-foreground font-bold">+</button>
          </>
        )}
        {!showStepper && (
          <input type="number" value={n || ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
            className="log-input w-20 text-center text-sm font-bold" />
        )}
        <button onClick={onRemove}
          className="h-8 w-8 rounded-lg text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 flex items-center justify-center">
          <Trash2 size={12} />
        </button>
      </div>
      {metric.target && metric.target > 0 && (metric.type === 'counter' || metric.type === 'number') && (
        <div className="mt-2 h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
          <div className={cn('h-full rounded-full transition-all', done ? 'bg-emerald-400' : 'bg-gold')}
            style={{ width: `${Math.min(100, (n / metric.target) * 100)}%` }} />
        </div>
      )}
    </div>
  )
}
