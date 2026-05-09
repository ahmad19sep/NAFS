'use client'

import {
  Area, AreaChart, CartesianGrid, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis, ReferenceLine
} from 'recharts'
import { computeTrajectory, computeRequiredPerDay } from '@/lib/mapping-engine'
import { formatDateShort, daysUntil } from '@/lib/utils'

interface Props {
  dream: any
  logs: { date: string; weighted_hours_today: number; todays_pull_days: number }[]
}

export default function DreamsClient({ dream, logs }: Props) {
  if (!dream) {
    return (
      <div className="mx-auto max-w-md px-4 pt-10 text-center">
        <p className="text-5xl">🌠</p>
        <p className="mt-4 font-semibold text-foreground">No dream defined yet</p>
        <p className="mt-2 text-sm text-muted-foreground">Complete onboarding to set your dream.</p>
      </div>
    )
  }

  const totalRequired = dream.total_hours_required * 1.8
  const last30 = logs.slice(-30).map((l) => l.weighted_hours_today)
  const totalDone = logs.reduce((s, l) => s + l.weighted_hours_today, 0)
  const trajectory = computeTrajectory(last30, totalRequired, totalDone, dream.dream_date)
  const requiredPerDay = computeRequiredPerDay(totalRequired, dream.dream_date)
  const daysLeft = daysUntil(dream.dream_date)
  const progressPct = Math.min(100, Math.round((totalDone / totalRequired) * 100))

  // Build trajectory chart data
  const chartData = logs.slice(-30).map((l) => ({
    date: formatDateShort(l.date),
    actual: parseFloat(l.weighted_hours_today.toFixed(1)),
    required: parseFloat(requiredPerDay.toFixed(1)),
  }))

  // Cumulative pull chart
  const pullChart = logs.map((l, i) => ({
    date: formatDateShort(l.date),
    pull: parseFloat(
      logs.slice(0, i + 1).reduce((s, x) => s + x.todays_pull_days, 0).toFixed(1)
    ),
  }))

  return (
    <div className="mx-auto max-w-md space-y-6 px-4">
      <div className="pt-2">
        <h1 className="text-2xl font-bold text-foreground">Dreams & Mapping</h1>
        <p className="mt-1 text-sm text-muted-foreground">The math doesn&apos;t lie.</p>
      </div>

      {/* Dream board */}
      <div className="relative overflow-hidden rounded-2xl border border-gold/30">
        {dream.image_url ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={dream.image_url} alt="Dream" className="h-48 w-full object-cover opacity-40" />
        ) : (
          <div className="h-48 bg-gradient-to-br from-primary/40 to-navy" />
        )}
        <div className="absolute inset-0 flex flex-col justify-end p-5">
          <p className="text-xs font-bold uppercase tracking-widest text-gold">Your dream</p>
          <p className="mt-1 text-lg font-bold text-white leading-snug">{dream.statement}</p>
          <p className="mt-1 text-sm text-white/70">Deadline: {dream.dream_date} ({daysLeft} days left)</p>
        </div>
      </div>

      {/* Progress stats */}
      <div className="grid grid-cols-2 gap-3">
        <div className="nafs-card p-4">
          <p className="text-xs text-muted-foreground">Progress</p>
          <p className="text-3xl font-bold tabular-nums text-gold">{progressPct}%</p>
          <p className="text-xs text-muted-foreground mt-1">of {dream.total_hours_required.toLocaleString()} hrs</p>
        </div>
        <div className="nafs-card p-4">
          <p className="text-xs text-muted-foreground">Required/day</p>
          <p className="text-3xl font-bold tabular-nums text-foreground">{requiredPerDay.toFixed(1)}</p>
          <p className="text-xs text-muted-foreground mt-1">weighted hours</p>
        </div>
        <div className="nafs-card p-4">
          <p className="text-xs text-muted-foreground">Avg (30d)</p>
          <p className={`text-3xl font-bold tabular-nums ${trajectory.avgPerDay >= requiredPerDay ? 'text-emerald-400' : 'text-red-400'}`}>
            {trajectory.avgPerDay.toFixed(1)}
          </p>
          <p className="text-xs text-muted-foreground mt-1">weighted hrs/day</p>
        </div>
        <div className="nafs-card p-4">
          <p className="text-xs text-muted-foreground">Arrival</p>
          <p className={`text-lg font-bold ${trajectory.isOnTrack ? 'text-emerald-400' : 'text-orange-400'}`}>
            {trajectory.arrivalDate}
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            {trajectory.isOnTrack ? 'On track ✅' : `${trajectory.delayDays}d late ⚠️`}
          </p>
        </div>
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

      {/* Activity weights table */}
      {dream.activity_weights?.length > 0 && (
        <div className="nafs-card p-4">
          <p className="section-header mb-3">Activity weights</p>
          <div className="space-y-2">
            {dream.activity_weights.map((w: any) => (
              <div key={w.id} className="flex items-center justify-between">
                <span className="text-sm text-foreground">{w.activity_name}</span>
                <span className={`text-sm font-bold tabular-nums
                  ${w.weight_multiplier >= 2.5 ? 'text-emerald-400'
                    : w.weight_multiplier >= 1.5 ? 'text-gold'
                    : w.weight_multiplier >= 0.5 ? 'text-orange-400'
                    : 'text-red-400'}`}>
                  {w.weight_multiplier}×
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
