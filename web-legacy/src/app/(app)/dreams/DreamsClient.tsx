'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import {
  Area, AreaChart, CartesianGrid, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis, ReferenceLine
} from 'recharts'
import { Pencil, Target, Loader2 } from 'lucide-react'
import { createClient } from '@/lib/supabase/client'
import { computeTrajectory, computeRequiredPerDay } from '@/lib/mapping-engine'
import { cn, formatDateShort, daysUntil, todayString } from '@/lib/utils'

interface Props {
  dream: any
  logs: { date: string; weighted_hours_today: number; todays_pull_days: number }[]
}

export default function DreamsClient({ dream, logs }: Props) {
  const router = useRouter()
  const supabase = createClient()
  const today = todayString()

  const [editing, setEditing] = useState(false)
  const [statement, setStatement] = useState<string>(dream?.statement ?? '')
  const [dreamDate, setDreamDate] = useState<string>(dream?.dream_date ?? '')
  const [totalHours, setTotalHours] = useState<number>(dream?.total_hours_required ?? 1000)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const todayLog = logs.find((l) => l.date === today)
  const [hoursToday, setHoursToday] = useState<number>(todayLog?.weighted_hours_today ?? 0)
  const [loggingHours, setLoggingHours] = useState(false)

  async function saveDream() {
    if (!statement.trim()) { setError('Describe your dream first'); return }
    if (!dreamDate || daysUntil(dreamDate) < 1) { setError('Pick a future deadline'); return }
    if (!totalHours || totalHours < 1) { setError('Estimate the hours it needs'); return }
    setSaving(true); setError(null)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) { setSaving(false); return }
    const { error: err } = await supabase.from('dreams').upsert({
      user_id: user.id,
      statement: statement.trim(),
      dream_date: dreamDate,
      total_hours_required: totalHours,
      updated_at: new Date().toISOString(),
    }, { onConflict: 'user_id' })
    setSaving(false)
    if (err) { setError(err.message); return }
    setEditing(false)
    router.refresh()
  }

  async function logHours(value: number) {
    const v = Math.max(0, Math.round(value * 2) / 2)
    setHoursToday(v)
    setLoggingHours(true)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user || !dream) { setLoggingHours(false); return }
    const required = computeRequiredPerDay(dream.total_hours_required * 1.8, dream.dream_date)
    const pull = required > 0 ? (v - required) / required : 0
    await supabase.from('daily_logs').upsert({
      user_id: user.id, date: today,
      weighted_hours_today: v,
      todays_pull_days: parseFloat(pull.toFixed(2)),
    }, { onConflict: 'user_id,date' })
    setLoggingHours(false)
    router.refresh()
  }

  // ---------- No dream yet / editing → definition form ----------
  if (!dream || editing) {
    return (
      <div className="mx-auto max-w-md space-y-6 px-4">
        <div className="pt-2">
          <h1 className="text-2xl font-bold text-foreground">
            {dream ? 'Edit your dream' : 'Dreams & Mapping'}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {dream ? 'Adjust the target — the math recalculates.' : 'Define the one thing you’re building toward. The math does the rest.'}
          </p>
        </div>

        {!dream && (
          <div className="nafs-card p-5 text-center">
            <p className="text-5xl">🌠</p>
            <p className="mt-3 text-sm text-muted-foreground leading-relaxed">
              Set a dream, estimate the focused hours it needs, and Ascend will tell you
              every single day whether you moved <span className="text-emerald-400 font-semibold">closer</span> or
              <span className="text-red-400 font-semibold"> further</span> from it.
            </p>
          </div>
        )}

        <div className="nafs-card p-5 space-y-4">
          <div>
            <label className="section-header mb-1.5 block">Your dream — one sentence</label>
            <textarea value={statement} onChange={(e) => setStatement(e.target.value)}
              rows={2} placeholder="e.g. Become an independent software engineer"
              className="log-input resize-none" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="section-header mb-1.5 block">Deadline</label>
              <input type="date" value={dreamDate} min={today}
                onChange={(e) => setDreamDate(e.target.value)} className="log-input" />
            </div>
            <div>
              <label className="section-header mb-1.5 block">Hours needed</label>
              <input type="number" value={totalHours || ''} min={1}
                onChange={(e) => setTotalHours(Number(e.target.value))}
                placeholder="1000" className="log-input" />
            </div>
          </div>
          <p className="text-[11px] text-muted-foreground leading-relaxed">
            Tip: mastery-level skills take 1,000–5,000 focused hours. Ascend adds an
            automatic ×1.8 reality buffer because everything takes longer than planned.
          </p>

          {error && <p className="text-xs text-red-400">{error}</p>}

          <div className="flex gap-2">
            {dream && (
              <button onClick={() => setEditing(false)}
                className="flex-1 rounded-xl border border-white/10 py-3 text-sm font-semibold text-muted-foreground">
                Cancel
              </button>
            )}
            <button onClick={saveDream} disabled={saving}
              className="flex-1 rounded-xl bg-primary py-3 text-sm font-semibold text-white
                         disabled:opacity-50 flex items-center justify-center gap-2 active:scale-[0.98]">
              {saving && <Loader2 size={14} className="animate-spin" />}
              {dream ? 'Save changes' : 'Lock in my dream'}
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ---------- Dream exists → trajectory dashboard ----------
  const totalRequired = dream.total_hours_required * 1.8
  const last30 = logs.slice(-30).map((l) => l.weighted_hours_today)
  const totalDone = logs.reduce((s, l) => s + l.weighted_hours_today, 0)
  const trajectory = computeTrajectory(last30, totalRequired, totalDone, dream.dream_date)
  const requiredPerDay = computeRequiredPerDay(totalRequired, dream.dream_date)
  const daysLeft = daysUntil(dream.dream_date)
  const progressPct = Math.min(100, Math.round((totalDone / totalRequired) * 100))

  const chartData = logs.slice(-30).map((l) => ({
    date: formatDateShort(l.date),
    actual: parseFloat(l.weighted_hours_today.toFixed(1)),
    required: parseFloat(requiredPerDay.toFixed(1)),
  }))

  const pullChart = logs.map((l, i) => ({
    date: formatDateShort(l.date),
    pull: parseFloat(
      logs.slice(0, i + 1).reduce((s, x) => s + x.todays_pull_days, 0).toFixed(1)
    ),
  }))

  return (
    <div className="mx-auto max-w-md space-y-5 px-4">
      <div className="pt-2 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Dreams & Mapping</h1>
          <p className="mt-1 text-sm text-muted-foreground">The math doesn&apos;t lie.</p>
        </div>
        <button onClick={() => setEditing(true)} aria-label="Edit dream"
          className="h-9 w-9 rounded-xl border border-white/10 bg-white/5 flex items-center justify-center
                     hover:bg-white/10 transition-all active:scale-95">
          <Pencil size={14} className="text-muted-foreground" />
        </button>
      </div>

      {/* Dream board */}
      <div className="relative overflow-hidden rounded-3xl border border-gold/30">
        {dream.image_url ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={dream.image_url} alt="Dream" className="h-48 w-full object-cover opacity-40" />
        ) : (
          <div className="h-48 bg-gradient-to-br from-primary/50 via-navy-light to-navy" />
        )}
        <div className="pointer-events-none absolute -top-8 -right-8 h-32 w-32 rounded-full bg-gold/15 blur-3xl" />
        <div className="absolute inset-0 flex flex-col justify-end p-5">
          <p className="text-xs font-bold uppercase tracking-widest text-gold">Your dream</p>
          <p className="mt-1 text-lg font-bold text-white leading-snug">{dream.statement}</p>
          <p className="mt-1 text-sm text-white/70">{dream.dream_date} · {daysLeft} days left</p>
        </div>
      </div>

      {/* Log today's hours */}
      <div className="nafs-card p-4">
        <div className="flex items-center justify-between mb-2">
          <p className="section-header flex items-center gap-1.5">
            <Target size={11} className="text-gold" /> Today&apos;s focused hours
          </p>
          <span className="text-[10px] text-muted-foreground">need {requiredPerDay.toFixed(1)}h/day</span>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={() => logHours(hoursToday - 0.5)} disabled={loggingHours || hoursToday <= 0}
            className="h-11 w-11 rounded-xl border border-white/10 bg-white/5 text-xl font-bold text-foreground
                       active:scale-90 transition-all disabled:opacity-30">−</button>
          <div className="flex-1 text-center">
            <p className={cn('text-3xl font-bold tabular-nums',
              hoursToday >= requiredPerDay ? 'text-emerald-400' : hoursToday > 0 ? 'text-gold' : 'text-muted-foreground'
            )}>
              {hoursToday}<span className="text-base font-semibold text-muted-foreground ml-1">h</span>
            </p>
            <div className="mt-1.5 h-1.5 w-full rounded-full bg-white/10">
              <div className={cn('h-full rounded-full transition-all',
                hoursToday >= requiredPerDay ? 'bg-emerald-400' : 'bg-gold')}
                style={{ width: `${Math.min(100, (hoursToday / Math.max(requiredPerDay, 0.1)) * 100)}%` }} />
            </div>
          </div>
          <button onClick={() => logHours(hoursToday + 0.5)} disabled={loggingHours}
            className="h-11 w-11 rounded-xl border border-gold/40 bg-gold/15 text-xl font-bold text-gold
                       active:scale-90 transition-all">+</button>
        </div>
        <p className="mt-2 text-center text-[10px] text-muted-foreground">
          {hoursToday >= requiredPerDay
            ? `🟢 +${((hoursToday - requiredPerDay) / Math.max(requiredPerDay, 0.1)).toFixed(1)} days pulled closer today`
            : `🔴 ${(requiredPerDay - hoursToday).toFixed(1)}h short of breaking even today`}
        </p>
      </div>

      {/* Progress stats */}
      <div className="grid grid-cols-2 gap-3">
        <div className="nafs-card p-4">
          <p className="text-xs text-muted-foreground">Progress</p>
          <p className="text-3xl font-bold tabular-nums text-gold">{progressPct}%</p>
          <p className="text-xs text-muted-foreground mt-1">of {Math.round(totalRequired).toLocaleString()} hrs</p>
        </div>
        <div className="nafs-card p-4">
          <p className="text-xs text-muted-foreground">Avg (30d)</p>
          <p className={cn('text-3xl font-bold tabular-nums',
            trajectory.avgPerDay >= requiredPerDay ? 'text-emerald-400' : 'text-red-400')}>
            {trajectory.avgPerDay.toFixed(1)}
          </p>
          <p className="text-xs text-muted-foreground mt-1">hrs/day · need {requiredPerDay.toFixed(1)}</p>
        </div>
      </div>
      <div className="nafs-card p-4 flex items-center justify-between">
        <div>
          <p className="text-xs text-muted-foreground">Projected arrival</p>
          <p className={cn('text-lg font-bold', trajectory.isOnTrack ? 'text-emerald-400' : 'text-orange-400')}>
            {trajectory.arrivalDate}
          </p>
        </div>
        <span className={cn('rounded-full border px-3 py-1.5 text-xs font-bold',
          trajectory.isOnTrack
            ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400'
            : 'border-orange-500/30 bg-orange-500/10 text-orange-400')}>
          {trajectory.isOnTrack ? 'On track ✅' : `${trajectory.delayDays}d late ⚠️`}
        </span>
      </div>

      {/* Actual vs required chart */}
      {chartData.length > 2 && (
        <div className="nafs-card p-4">
          <p className="section-header mb-3">Actual vs required (30 days)</p>
          <ResponsiveContainer width="100%" height={140}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
              <XAxis dataKey="date" hide />
              <YAxis hide />
              <Tooltip
                contentStyle={{ background: '#0B1A2B', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }}
                itemStyle={{ fontSize: 12 }}
              />
              <ReferenceLine y={requiredPerDay} stroke="rgba(255,255,255,0.2)" strokeDasharray="4 4" />
              <Line type="monotone" dataKey="required" stroke="rgba(201,162,39,0.4)" strokeWidth={1} dot={false} name="Required" />
              <Line type="monotone" dataKey="actual" stroke="#C9A227" strokeWidth={2} dot={false} name="Actual" />
            </LineChart>
          </ResponsiveContainer>
          <div className="flex gap-4 mt-2 text-xs text-muted-foreground">
            <span className="flex items-center gap-1"><span className="h-1 w-4 bg-gold/40 inline-block rounded" />Required</span>
            <span className="flex items-center gap-1"><span className="h-1 w-4 bg-gold inline-block rounded" />Actual</span>
          </div>
        </div>
      )}

      {/* Cumulative pull chart */}
      {pullChart.length > 2 && (
        <div className="nafs-card p-4">
          <p className="section-header mb-3">Cumulative days pulled</p>
          <ResponsiveContainer width="100%" height={100}>
            <AreaChart data={pullChart}>
              <defs>
                <linearGradient id="pullGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#34d399" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#34d399" stopOpacity={0} />
                </linearGradient>
              </defs>
              <XAxis dataKey="date" hide />
              <ReferenceLine y={0} stroke="rgba(255,255,255,0.2)" />
              <Tooltip
                contentStyle={{ background: '#0B1A2B', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }}
                itemStyle={{ color: '#34d399', fontSize: 12 }}
              />
              <Area type="monotone" dataKey="pull" stroke="#34d399" strokeWidth={2} fill="url(#pullGrad)" name="Days pulled" />
            </AreaChart>
          </ResponsiveContainer>
          <p className="text-xs text-muted-foreground mt-1">
            Positive = ahead of your dream date. Negative = falling behind.
          </p>
        </div>
      )}
    </div>
  )
}
