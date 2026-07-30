'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts'
import IdentityScoreRing from '@/components/IdentityScoreRing'
import PrayerCountdown from '@/components/PrayerCountdown'
import { formatPullDays, formatDateShort, scoreColor, timeAgo } from '@/lib/utils'
import { computeTrajectory } from '@/lib/mapping-engine'

interface Props {
  profile: any
  todayLog: any
  recentLogs: any[]
  latestTribunal: any
}

export default function DashboardClient({ profile, todayLog, recentLogs, latestTribunal }: Props) {
  const router = useRouter()
  const dream = profile?.dreams
  const [nextPrayer, setNextPrayer] = useState({ name: 'Fajr', time: '05:00' })
  const [hijriDate, setHijriDate] = useState('')

  const identityScore = todayLog?.identity_score ?? 0
  const todaysPull = todayLog?.todays_pull_days ?? 0
  const hasLoggedToday = !!todayLog

  // Trajectory from last 30 days
  const last30Weighted = recentLogs.map((l: any) => l.weighted_hours_today ?? 0)
  const totalRequired = dream ? dream.total_hours_required * 1.8 : 5400
  const alreadyDone = last30Weighted.reduce((s: number, h: number) => s + h, 0)

  const trajectory = dream
    ? computeTrajectory(last30Weighted, totalRequired, alreadyDone, dream.dream_date)
    : null

  // Chart data
  const chartData = recentLogs.slice(-14).map((l: any) => ({
    date: formatDateShort(l.date),
    score: l.identity_score ?? 0,
    hours: l.weighted_hours_today ?? 0,
  }))

  useEffect(() => {
    // Fetch prayer times
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(async (pos) => {
        try {
          const today = new Date()
          const ctrl = new AbortController()
          const timer = setTimeout(() => ctrl.abort(), 5000)
          const res = await fetch(
            `https://api.aladhan.com/v1/timings/${today.getDate()}-${today.getMonth() + 1}-${today.getFullYear()}?latitude=${pos.coords.latitude}&longitude=${pos.coords.longitude}&method=2`,
            { signal: ctrl.signal }
          )
          clearTimeout(timer)
          const json = await res.json()
          if (json.data) {
            const timings = json.data.timings
            const hijri = json.data.date.hijri
            setHijriDate(`${hijri.day} ${hijri.month.en} ${hijri.year}`)

            // Find next prayer
            const now = new Date()
            const prayers = [
              { name: 'Fajr', time: timings.Fajr },
              { name: 'Dhuhr', time: timings.Dhuhr },
              { name: 'Asr', time: timings.Asr },
              { name: 'Maghrib', time: timings.Maghrib },
              { name: 'Isha', time: timings.Isha },
            ]
            const next = prayers.find((p) => {
              const [h, m] = p.time.split(':').map(Number)
              const t = new Date()
              t.setHours(h, m, 0, 0)
              return t > now
            }) ?? prayers[0]
            setNextPrayer(next)
          }
        } catch {}
      })
    }
  }, [])

  return (
    <div className="mx-auto max-w-md space-y-5 px-4">
      {/* Header */}
      <div className="flex items-center justify-between pt-2">
        <div>
          <p className="text-xs text-muted-foreground">{hijriDate || 'Bismillah'}</p>
          <h1 className="text-xl font-bold text-foreground">
            {profile?.name ? `السلام عليكم, ${profile.name}` : 'NAFS'}
          </h1>
        </div>
        <Link href="/profile" className="h-9 w-9 rounded-full bg-primary/20 border border-primary/30 flex items-center justify-center">
          <span className="text-sm font-bold text-gold">
            {profile?.name?.[0]?.toUpperCase() ?? 'A'}
          </span>
        </Link>
      </div>

      {/* Prayer countdown */}
      <PrayerCountdown nextPrayerName={nextPrayer.name} nextPrayerTime={nextPrayer.time} />

      {/* Identity Score + Today's Pull */}
      <div className="nafs-card p-6">
        {hasLoggedToday ? (
          <div className="flex flex-col items-center gap-4">
            <IdentityScoreRing score={identityScore} />
            <div className="text-center">
              <p className="text-sm text-muted-foreground">
                Today you are{' '}
                <span className={`font-bold ${scoreColor(identityScore)}`}>
                  {identityScore}%
                </span>{' '}
                the person you said you want to be.
              </p>
            </div>
            <div className={`streak-badge ${todaysPull >= 0 ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
              {todaysPull >= 0 ? '✅' : '⚠️'}{' '}
              {formatPullDays(todaysPull)}
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-4 py-4">
            <div className="h-20 w-20 rounded-full border-2 border-dashed border-white/20 flex items-center justify-center">
              <span className="text-3xl">🌙</span>
            </div>
            <div className="text-center">
              <p className="font-semibold text-foreground">You haven&apos;t logged today</p>
              <p className="mt-1 text-sm text-muted-foreground">90 seconds. Be honest.</p>
            </div>
            <Link
              href="/log"
              className="rounded-xl bg-primary px-8 py-3 font-semibold text-white
                         transition-all hover:bg-teal-light active:scale-95"
            >
              Log your day →
            </Link>
          </div>
        )}
      </div>

      {/* Dream board */}
      {dream && (
        <Link href="/dreams" className="block">
          <div className="relative overflow-hidden rounded-2xl border border-gold/20">
            {dream.image_url ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={dream.image_url} alt="Dream" className="h-32 w-full object-cover opacity-50" />
            ) : (
              <div className="h-32 w-full bg-gradient-to-br from-primary/30 to-navy" />
            )}
            <div className="absolute inset-0 flex flex-col justify-end p-4">
              <p className="text-xs text-gold font-semibold uppercase tracking-widest">Your dream</p>
              <p className="mt-1 font-semibold text-white leading-snug line-clamp-2">
                {dream.statement}
              </p>
              {trajectory && (
                <p className={`mt-1 text-xs ${trajectory.isOnTrack ? 'text-emerald-400' : 'text-orange-400'}`}>
                  {trajectory.isOnTrack
                    ? `On track — arriving ${trajectory.arrivalDate}`
                    : `${trajectory.delayDays} days behind — arriving ${trajectory.arrivalDate}`}
                </p>
              )}
            </div>
          </div>
        </Link>
      )}

      {/* 14-day Identity Score chart */}
      {chartData.length > 1 && (
        <div className="nafs-card p-4">
          <p className="section-header mb-3">14-day identity score</p>
          <ResponsiveContainer width="100%" height={80}>
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="scoreGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#C9A227" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#C9A227" stopOpacity={0} />
                </linearGradient>
              </defs>
              <XAxis dataKey="date" hide />
              <Tooltip
                contentStyle={{ background: '#0B1A2B', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8 }}
                labelStyle={{ color: '#9ca3af', fontSize: 11 }}
                itemStyle={{ color: '#C9A227', fontSize: 12 }}
              />
              <Area
                type="monotone"
                dataKey="score"
                stroke="#C9A227"
                strokeWidth={2}
                fill="url(#scoreGrad)"
                dot={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Latest tribunal snippet */}
      {latestTribunal && (
        <Link href="/coach" className="block">
          <div className="nafs-card p-4 border-gold/20">
            <div className="flex items-center gap-2 mb-2">
              <span className="text-sm font-semibold text-gold">⚖️ Weekly Tribunal</span>
              <span className="text-xs text-muted-foreground">{timeAgo(latestTribunal.generated_at)}</span>
            </div>
            <p className="text-sm text-muted-foreground line-clamp-3 leading-relaxed">
              {latestTribunal.content_md.slice(0, 200)}…
            </p>
          </div>
        </Link>
      )}

      {/* Quick actions */}
      <div className="grid grid-cols-2 gap-3 pb-4">
        <Link href="/deen" className="nafs-card p-4 flex flex-col gap-2 active:scale-95 transition-transform">
          <span className="text-2xl">🕌</span>
          <p className="font-semibold text-sm text-foreground">Deen Tracker</p>
          <p className="text-xs text-muted-foreground">Prayers & Quran</p>
        </Link>
        <Link href="/dreams" className="nafs-card p-4 flex flex-col gap-2 active:scale-95 transition-transform">
          <span className="text-2xl">📊</span>
          <p className="font-semibold text-sm text-foreground">Trajectory</p>
          <p className="text-xs text-muted-foreground">Dream mapping</p>
        </Link>
      </div>
    </div>
  )
}
