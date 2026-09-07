'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { cn, todayString } from '@/lib/utils'
import { Droplet, Footprints, Moon, Sun, Dumbbell, Scale, Ruler, Plus, Minus, X, Trash2, Sparkles, RefreshCw } from 'lucide-react'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'
import { computeHealthHistory } from '@/lib/history'
import { computeBMI } from '@/lib/bmi'
import { classifyWrite } from '@/lib/write-conflict'
import {
  type CustomMetric, type CustomMetricType, type ExtrasValues, type SleepSession,
  makeMetricId, isMetricDone,
  makeSleepSessionId, sleepSessionMinutes, totalSleepMinutes, formatDuration,
} from '@/lib/health'
import MealsCard from '@/components/MealsCard'
import { type Meal, ensureCoreMeals, mealsEaten } from '@/lib/food'

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
  const [sleepSessions, setSleepSessions] = useState<SleepSession[]>([])
  const [meals, setMeals] = useState<Meal[]>(() => ensureCoreMeals([]))

  // Setup modal
  const [showSetup, setShowSetup] = useState(false)
  const [setupHeight, setSetupHeight] = useState('')
  const [setupWeight, setSetupWeight] = useState('')
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

  // Today's row and the version it was at when this page loaded. Together they
  // let a save refuse to overwrite a change made somewhere else.
  const [logId, setLogId] = useState<string | null>(null)
  const [loadedAt, setLoadedAt] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

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
      }

      if (todayLog) {
        setLogId(todayLog.id)
        setLoadedAt(todayLog.updated_at)
        setWater(todayLog.water_glasses ?? 0)
        setSteps(String(todayLog.steps ?? ''))
        setExercise(todayLog.exercise_done ?? false)
        setExerciseMins(String(todayLog.exercise_minutes ?? ''))
        setWeight(String(todayLog.weight_kg ?? ''))
        setNotes(todayLog.notes ?? '')
        setExtrasValues((todayLog.extras ?? {}) as ExtrasValues)

        // Sessions win; fall back to the old single bedtime/wake pair on rows
        // saved before the migration.
        const stored = (todayLog.sleep_sessions ?? []) as SleepSession[]
        if (Array.isArray(stored) && stored.length) {
          setSleepSessions(stored.map((s) => ({
            id: s.id ?? makeSleepSessionId(),
            start: (s.start ?? '').slice(0, 5),
            end: (s.end ?? '').slice(0, 5),
          })))
        } else if (todayLog.sleep_time && todayLog.wake_time) {
          setSleepSessions([{
            id: makeSleepSessionId(),
            start: todayLog.sleep_time.slice(0, 5),
            end: todayLog.wake_time.slice(0, 5),
          }])
        }

        setMeals(ensureCoreMeals(todayLog.meals as Meal[] | null))
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
  const sleptMins = useMemo(() => totalSleepMinutes(sleepSessions), [sleepSessions])
  const history = useMemo(() => computeHealthHistory(history30, today), [history30, today])

  function addSleepSession() {
    setSleepSessions((prev) => [...prev, { id: makeSleepSessionId(), start: '', end: '' }])
  }
  function updateSleepSession(id: string, patch: Partial<SleepSession>) {
    setSleepSessions((prev) => prev.map((s) => (s.id === id ? { ...s, ...patch } : s)))
  }
  function removeSleepSession(id: string) {
    setSleepSessions((prev) => prev.filter((s) => s.id !== id))
  }

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

    let { data: updated, error } = await supabase
      .from('users').update({ height_cm: h, weight_kg: w }).eq('id', user.id)
      .select('height_cm, weight_kg').maybeSingle()

    // No row matched → the users profile row is missing (e.g. signup ran
    // before migrations). Create it now instead of failing.
    if (!updated && !error) {
      const meta = (user.user_metadata ?? {}) as Record<string, any>
      const fallbackName =
        (meta.name || meta.full_name || '').trim() || (user.email?.split('@')[0] ?? '')
      const ins = await supabase.from('users')
        .upsert({
          id: user.id,
          email: user.email ?? '',
          name: fallbackName,
          onboarding_complete: true,
          height_cm: h,
          weight_kg: w,
        })
        .select('height_cm, weight_kg').maybeSingle()
      updated = ins.data
      error   = ins.error
    }

    setSetupSaving(false)
    if (error) { setSetupError(error.message); return }
    if (!updated) { setSetupError('Could not save your profile. Please sign out and back in.'); return }

    setHeightCm(updated.height_cm)
    setProfileWeight(updated.weight_kg)
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
    if (!confirm(`Remove "${label}"? You can add it back anytime with + Add metric. Past data is kept.`)) return
    const next = Array.from(new Set([...hiddenDefaults, id]))
    setHiddenDefaults(next)
    if (userId) {
      await supabase.from('users').update({ health_defaults_hidden: next }).eq('id', userId)
    }
  }
  async function unhideDefault(id: string) {
    const next = hiddenDefaults.filter((x) => x !== id)
    setHiddenDefaults(next)
    if (userId) {
      await supabase.from('users').update({ health_defaults_hidden: next }).eq('id', userId)
    }
  }
  const isHidden = (id: string) => hiddenDefaults.includes(id)

  // Optional built-ins — offered in "+ Add metric" once removed.
  // Sleep and meals aren't here: they're permanent parts of the page.
  const BUILTIN_METRICS = [
    { id: 'water',    name: 'Water',    icon: Droplet,    tint: 'text-blue-400' },
    { id: 'steps',    name: 'Steps',    icon: Footprints, tint: 'text-emerald-400' },
    { id: 'exercise', name: 'Exercise', icon: Dumbbell,   tint: 'text-pink-400' },
    { id: 'weight',   name: 'Weight',   icon: Scale,      tint: 'text-gold' },
  ] as const
  const hiddenBuiltins = BUILTIN_METRICS.filter((b) => isHidden(b.id))
  const nothingTracked = hiddenBuiltins.length === BUILTIN_METRICS.length && extrasConfig.length === 0

  async function save() {
    if (!userId) return
    setSaving(true)
    const dailyWeightNum = weight ? Number(weight) : null

    // Only complete periods count; sleep_hours carries the summed total so the
    // dashboard, reports and AI prompts need no changes.
    const sessions = sleepSessions.filter((s) => sleepSessionMinutes(s.start, s.end) != null)
    const totalMins = totalSleepMinutes(sessions)

    const payload = {
      user_id: userId, date: today,
      water_glasses: water,
      steps: steps ? Number(steps) : null,
      exercise_done: exercise,
      exercise_minutes: exerciseMins ? Number(exerciseMins) : null,
      weight_kg: dailyWeightNum,
      notes: notes || null,
      extras: extrasValues,
      meals,
      sleep_sessions: sessions,
      sleep_hours: sessions.length ? Math.round(totalMins / 6) / 10 : null,
      sleep_time: sessions[0]?.start ?? null,
      wake_time: sessions[sessions.length - 1]?.end ?? null,
      updated_at: new Date().toISOString(),
    }

    // LOG-01. Every field here is an absolute value taken from this form, so a
    // plain upsert is last-write-wins: open the page on a phone and a laptop,
    // save on both, and whichever saved first is silently replaced — including
    // fields the second device never touched. The row's updated_at acts as a
    // version. If it moved since this page loaded, someone else wrote to today
    // and we stop rather than overwrite them.
    let saved: { id: string; updated_at: string } | null = null

    const isInsert = !logId
    const { data, error } = isInsert
      ? await supabase.from('health_logs').insert(payload)
          .select('id, updated_at').maybeSingle()
      : await supabase.from('health_logs').update(payload)
          .eq('id', logId).eq('updated_at', loadedAt)
          .select('id, updated_at').maybeSingle()

    const outcome = classifyWrite(error, !!data, isInsert)
    if (outcome.kind !== 'saved') {
      setSaving(false)
      if (outcome.kind === 'conflict') setConflict(true)
      else alert('Could not save. Please try again.')
      return
    }
    saved = data

    if (saved) {
      setLogId(saved.id)
      setLoadedAt(saved.updated_at)
    }

    if (dailyWeightNum) {
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
    return (
      <div className="mx-auto max-w-md px-4 pb-8 pt-5">
        <div className="text-center mb-6">
          <div className="mx-auto h-14 w-14 rounded-2xl bg-red-500/15 border border-red-400/30 flex items-center justify-center text-2xl mb-3">
            ❤️
          </div>
          <h1 className="text-xl font-bold text-foreground">Set up your health profile</h1>
          <p className="text-sm text-muted-foreground mt-1">
            We&apos;ll ask once — these power your BMI banner and health insights.
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
  // Sleep and meals always count; the rest only while the user keeps them.
  const optionalIds = ['water', 'steps', 'exercise'].filter((id) => !isHidden(id))
  const optionalDone: Record<string, boolean> = {
    water: water > 0, steps: !!steps, exercise,
  }
  const ateCount = mealsEaten(meals)
  const tracked = [
    sleptMins > 0,
    ateCount > 0,
    ...optionalIds.map((id) => optionalDone[id]),
    ...(extrasConfig.map((m) => isMetricDone(m, extrasValues[m.id]))),
  ].filter(Boolean).length
  const total = 2 + optionalIds.length + extrasConfig.length

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

      {/* Someone saved today's health log elsewhere while this page was open.
          Neither version is discarded automatically — reloading shows theirs,
          and the entries here stay on screen until then. */}
      {conflict && (
        <div className="rounded-2xl border border-orange-500/40 bg-orange-500/10 p-4">
          <p className="text-sm font-semibold text-orange-300">Today was updated somewhere else</p>
          <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
            Another device saved today&apos;s health log after this page loaded. Saving
            now would overwrite it, including anything it recorded that isn&apos;t on
            this screen. Reload to see the newer version, then re-enter anything missing.
          </p>
          <button onClick={() => window.location.reload()}
            className="mt-3 rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white
                       hover:bg-teal-light transition-all active:scale-95">
            Reload today
          </button>
        </div>
      )}

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
              {sleptMins > 0 && ` · slept ${formatDuration(sleptMins)}`}
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

      {/* ---------- The two permanent metrics: sleep and meals ---------- */}

      {/* Sleep — one card per period, so a night plus naps all count */}
      {(() => {
        const hrs = sleptMins / 60
        const inRange = hrs >= 7 && hrs <= 9
        return (
          <div className="nafs-card p-5">
            <div className="flex items-center gap-3 mb-4">
              <div className="h-9 w-9 rounded-xl border border-indigo-400/20 bg-indigo-500/10 flex items-center justify-center">
                <Moon size={16} className={inRange ? 'text-emerald-400' : 'text-indigo-400'} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[15px] font-semibold text-foreground leading-tight">Sleep</p>
                <p className="text-[11px] text-muted-foreground mt-0.5">
                  {sleepSessions.length > 1
                    ? `${sleepSessions.length} periods · goal 7–9 hrs`
                    : 'night sleep + naps · goal 7–9 hrs'}
                </p>
              </div>
              <p className="tabular-nums">
                <span className={cn('text-2xl font-semibold', inRange ? 'text-emerald-400' : 'text-foreground')}>
                  {sleptMins > 0 ? formatDuration(sleptMins) : '—'}
                </span>
              </p>
            </div>

            <div className="space-y-2.5">
              {sleepSessions.map((s, i) => {
                const mins = sleepSessionMinutes(s.start, s.end)
                return (
                  <div key={s.id} className="rounded-xl border border-white/10 bg-white/5 p-3">
                    <div className="flex items-center justify-between mb-2">
                      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                        {i === 0 ? 'Sleep' : `Nap ${i}`}
                      </p>
                      <div className="flex items-center gap-2">
                        <span className={cn('text-xs font-bold tabular-nums',
                          mins == null ? 'text-muted-foreground/60' : 'text-indigo-300'
                        )}>
                          {mins == null ? 'set both times' : formatDuration(mins)}
                        </span>
                        <button onClick={() => removeSleepSession(s.id)}
                          className="h-6 w-6 rounded-md flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                          aria-label={`Remove sleep period ${i + 1}`}>
                          <X size={11} />
                        </button>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 flex items-center gap-1">
                          <Moon size={10} /> Slept at
                        </label>
                        <input type="time" value={s.start}
                          onChange={(e) => updateSleepSession(s.id, { start: e.target.value })}
                          className="log-input text-center text-base font-semibold" />
                      </div>
                      <div>
                        <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 flex items-center gap-1">
                          <Sun size={10} /> Woke at
                        </label>
                        <input type="time" value={s.end}
                          onChange={(e) => updateSleepSession(s.id, { end: e.target.value })}
                          className="log-input text-center text-base font-semibold" />
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>

            <button onClick={addSleepSession}
              className="mt-2.5 w-full rounded-xl border border-dashed border-white/15 py-3 text-xs font-semibold
                         text-muted-foreground hover:text-indigo-300 hover:border-indigo-400/40 transition-all active:scale-95
                         flex items-center justify-center gap-1.5">
              <Plus size={13} />
              {sleepSessions.length === 0 ? 'Add sleep' : 'Add another sleep or nap'}
            </button>

            {sleptMins > 0 && (
              <p className={cn('mt-2.5 text-center text-[11px] font-medium',
                inRange ? 'text-emerald-400' : 'text-orange-400'
              )}>
                {formatDuration(sleptMins)} total
                {sleepSessions.length > 1 && ` across ${sleepSessions.length} periods`}
                {inRange ? ' — healthy range ✓' : ' · aim for 7–9 hrs'}
              </p>
            )}
          </div>
        )
      })()}

      {/* Meals — breakfast, lunch and dinner are permanent; extras optional */}
      <MealsCard meals={meals} onChange={setMeals} />

      {/* ---------- Optional metrics ---------- */}

      {/* Water */}
      {!isHidden('water') && (
        <div className="nafs-card p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="h-9 w-9 rounded-xl border border-blue-400/20 bg-blue-500/10 flex items-center justify-center">
              <Droplet size={16} className={water >= 8 ? 'text-emerald-400' : 'text-blue-400'} />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-[15px] font-semibold text-foreground leading-tight">Water</p>
              <p className="text-[11px] text-muted-foreground mt-0.5">8 glasses ≈ 2 litres</p>
            </div>
            <p className="tabular-nums">
              <span className={cn('text-2xl font-semibold', water >= 8 ? 'text-emerald-400' : 'text-foreground')}>{water}</span>
              <span className="text-sm text-muted-foreground"> / 8</span>
            </p>
            <button onClick={() => hideDefault('water', 'Water')}
              className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              aria-label="Hide water">
              <Trash2 size={11} />
            </button>
          </div>
          <div className="flex items-center gap-3">
            <button onClick={() => setWater(Math.max(0, water - 1))} disabled={water <= 0}
              aria-label="Remove glass"
              className="h-11 w-11 shrink-0 rounded-xl border border-white/10 bg-white/5 flex items-center justify-center
                         text-foreground transition-all active:scale-90 disabled:opacity-30">
              <Minus size={16} />
            </button>
            <div className="flex-1 flex gap-1.5">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className={cn('h-2.5 flex-1 rounded-full transition-all duration-300',
                  i < water
                    ? (water >= 8 ? 'bg-emerald-400' : 'bg-gradient-to-r from-sky-400 to-blue-400')
                    : 'bg-white/[0.08]'
                )} />
              ))}
            </div>
            <button onClick={() => setWater(Math.min(12, water + 1))}
              aria-label="Add glass"
              className="h-11 w-11 shrink-0 rounded-xl border border-blue-400/30 bg-blue-500/15 flex items-center justify-center
                         text-blue-300 transition-all active:scale-90">
              <Plus size={16} />
            </button>
          </div>
          {water >= 8 && (
            <p className="mt-2.5 text-center text-[11px] font-medium text-emerald-400">Goal reached — well hydrated ✓</p>
          )}
        </div>
      )}

      {/* Steps */}
      {!isHidden('steps') && (() => {
        const stepsNum = steps ? Number(steps) : 0
        const stepsTarget = 8000
        const stepsPct = Math.min(100, (stepsNum / stepsTarget) * 100)
        const stepsHit = stepsNum >= stepsTarget
        const addSteps = (n: number) => setSteps(String(Math.max(0, stepsNum + n)))
        return (
          <div className="nafs-card p-5">
            <div className="flex items-center gap-3 mb-4">
              <div className="h-9 w-9 rounded-xl border border-emerald-400/20 bg-emerald-500/10 flex items-center justify-center">
                <Footprints size={16} className="text-emerald-400" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[15px] font-semibold text-foreground leading-tight">Steps</p>
                <p className="text-[11px] text-muted-foreground mt-0.5">goal {stepsTarget.toLocaleString()}</p>
              </div>
              <p className="tabular-nums">
                <span className={cn('text-2xl font-semibold', stepsHit ? 'text-emerald-400' : 'text-foreground')}>
                  {stepsNum.toLocaleString()}
                </span>
              </p>
              <button onClick={() => hideDefault('steps', 'Steps')}
                className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                aria-label="Hide steps">
                <Trash2 size={11} />
              </button>
            </div>

            <div className="relative h-2.5 w-full rounded-full bg-white/[0.08] overflow-hidden">
              <div className={cn('h-full rounded-full transition-all duration-500',
                stepsHit ? 'bg-emerald-400' : 'bg-gradient-to-r from-emerald-600 to-emerald-400'
              )} style={{ width: `${stepsPct}%` }} />
            </div>
            <div className="mt-1.5 flex justify-between text-[10px] tabular-nums text-muted-foreground/70">
              <span>0</span><span>{stepsHit ? 'goal reached ✓' : `${Math.round(stepsPct)}%`}</span><span>{(stepsTarget / 1000)}k</span>
            </div>

            <div className="mt-3 flex items-center gap-2">
              <input type="number" inputMode="numeric" value={steps} onChange={(e) => setSteps(e.target.value)}
                placeholder="0" min="0"
                className="log-input flex-1 py-2.5 text-center text-lg font-semibold" />
              {[500, 1000, 2500].map((n) => (
                <button key={n} onClick={() => addSteps(n)}
                  className="shrink-0 rounded-xl border border-white/10 bg-white/5 px-3 py-2.5 text-xs font-semibold
                             text-muted-foreground hover:text-emerald-300 hover:border-emerald-400/30 transition-all active:scale-95 tabular-nums">
                  +{n >= 1000 ? `${n / 1000}k` : n}
                </button>
              ))}
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
        {nothingTracked && !showAddMetric && (
          <div className="py-6 text-center">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl border border-gold/20 bg-gold/[0.07]">
              <Plus size={18} className="text-gold" />
            </div>
            <p className="text-sm font-semibold text-foreground">Track what matters to you</p>
            <p className="mx-auto mt-1 max-w-[240px] text-xs text-muted-foreground leading-relaxed">
              Water, steps, exercise, weight — or anything custom like vitamins, coffee, mood.
            </p>
            <button onClick={() => setShowAddMetric(true)}
              className="btn-gold mt-4 px-6 py-2.5 text-sm">
              Add your first metric
            </button>
          </div>
        )}
        {!nothingTracked && extrasConfig.length === 0 && !showAddMetric && hiddenBuiltins.length > 0 && (
          <p className="text-xs text-muted-foreground text-center py-2">
            Add more — {hiddenBuiltins.map((b) => b.name.toLowerCase()).join(', ')} or anything custom.
          </p>
        )}

        {/* Add form */}
        {showAddMetric && (
          <div className="space-y-3 p-3 rounded-xl border border-white/10 bg-white/5">
            {hiddenBuiltins.length > 0 && (
              <div>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">Quick add</p>
                <div className="flex flex-wrap gap-1.5">
                  {hiddenBuiltins.map((b) => (
                    <button key={b.id} onClick={() => unhideDefault(b.id)}
                      className="flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1.5
                                 text-xs font-medium text-foreground hover:border-gold/40 hover:bg-gold/10 transition-all active:scale-95">
                      <b.icon size={12} className={b.tint} />
                      {b.name}
                    </button>
                  ))}
                </div>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground mt-3 mb-1.5">Or create custom</p>
              </div>
            )}
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
