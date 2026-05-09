'use client'

import { useState } from 'react'
import Link from 'next/link'
import { createClient } from '@/lib/supabase/client'
import { ChevronRight } from 'lucide-react'
import { cn, scoreColor, todayString, daysUntil } from '@/lib/utils'
import { computeDailyScore, PRAYERS } from '@/lib/scoring'
import DateTimeHeader from '@/components/DateTimeHeader'
import PrayerTimesBar from '@/components/PrayerTimesBar'

const PRAYER_EMOJIS: Record<string, string> = {
  Fajr: '🌙', Dhuhr: '☀️', Asr: '🌤️', Maghrib: '🌅', Isha: '⭐',
}

interface Props {
  profile: any
  habits: any[]
  habitLogs: any[]
  prayerLog: any
  challenges: any[]
  checkin: any
  goals: any[]
  today: string
}

function getGreeting(name: string) {
  const h = new Date().getHours()
  const greet = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : h < 21 ? 'Good evening' : 'Good night'
  return `${greet}, ${name.split(' ')[0]} 👋`
}

export default function HomeClient({ profile, habits, habitLogs, prayerLog, challenges, checkin, goals, today }: Props) {
  const supabase = createClient()

  const [prayers, setPrayers] = useState<Record<string, boolean>>(
    PRAYERS.reduce((acc, p) => ({ ...acc, [p]: prayerLog?.[p.toLowerCase()] ?? false }), {})
  )
  const [savingPrayer, setSavingPrayer] = useState<string | null>(null)

  // Task state from morning checkin
  const tasks: { text: string; done: boolean }[] = checkin?.tasks ?? []

  // Compute score
  const score = computeDailyScore(prayers, habits, habitLogs)

  async function togglePrayer(name: string) {
    setSavingPrayer(name)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const updated = { ...prayers, [name]: !prayers[name] }
    setPrayers(updated)

    await supabase.from('prayer_logs').upsert({
      user_id: user.id,
      date: today,
      fajr: updated.Fajr,
      dhuhr: updated.Dhuhr,
      asr: updated.Asr,
      maghrib: updated.Maghrib,
      isha: updated.Isha,
    }, { onConflict: 'user_id,date' })

    setSavingPrayer(null)
  }

  const isEvening = new Date().getHours() >= 17

  return (
    <div className="mx-auto max-w-md space-y-5 px-4">

      {/* Header */}
      <div className="pt-4 space-y-2">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-bold text-foreground">
            {getGreeting(profile?.name ?? 'Ahmad')}
          </h1>
          <Link href="/profile">
            <div className="h-10 w-10 rounded-full bg-primary/30 border border-primary/50 flex items-center justify-center font-bold text-gold text-sm">
              {(profile?.name ?? 'A')[0].toUpperCase()}
            </div>
          </Link>
        </div>
        <DateTimeHeader showClock />
      </div>

      {/* Overall Score Card */}
      <div className="nafs-card p-5">
        <div className="flex items-center gap-5">
          {/* Score ring */}
          <div className="relative flex-shrink-0">
            <svg width="88" height="88" style={{ transform: 'rotate(-90deg)' }}>
              <circle cx="44" cy="44" r="36" fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="9" />
              <circle cx="44" cy="44" r="36" fill="none"
                stroke={score.totalScore >= 80 ? '#34d399' : score.totalScore >= 60 ? '#fbbf24' : score.totalScore >= 40 ? '#fb923c' : '#f87171'}
                strokeWidth="9" strokeLinecap="round"
                strokeDasharray={226}
                strokeDashoffset={226 * (1 - score.totalScore / 100)}
                style={{ transition: 'stroke-dashoffset 0.8s ease', filter: 'drop-shadow(0 0 6px currentColor)' }}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={`text-xl font-bold tabular-nums ${scoreColor(score.totalScore)}`}>
                {score.totalScore}%
              </span>
            </div>
          </div>

          {/* Score breakdown */}
          <div className="flex-1 space-y-2">
            <p className="text-sm font-semibold text-foreground">Today&apos;s score</p>
            {score.breakdown.slice(0, 4).map((b) => (
              <div key={b.name} className="flex items-center gap-2">
                <span className="text-sm w-5">{b.emoji}</span>
                <div className="flex-1 h-1.5 rounded-full bg-white/10">
                  <div
                    className={cn('h-full rounded-full transition-all', b.earned === b.max ? 'bg-emerald-400' : b.earned > 0 ? 'bg-gold' : 'bg-white/20')}
                    style={{ width: `${b.max > 0 ? (b.earned / b.max) * 100 : 0}%` }}
                  />
                </div>
                <span className="text-xs tabular-nums text-muted-foreground w-8 text-right">
                  {b.earned}/{b.max}
                </span>
              </div>
            ))}
            {score.breakdown.length > 4 && (
              <p className="text-xs text-muted-foreground">+{score.breakdown.length - 4} more</p>
            )}
          </div>
        </div>
      </div>

      {/* Daily Tasks — Prayers */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <p className="font-semibold text-foreground">Daily tasks</p>
          <Link href="/checkin" className="text-xs text-gold">+ Add task</Link>
        </div>

        {/* Prayers with real times */}
        <div className="nafs-card p-4 space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-foreground">🕌 Prayers</p>
            <span className={cn('text-sm font-bold tabular-nums px-2 py-0.5 rounded-full',
              score.prayerScore === 5 ? 'bg-emerald-500/20 text-emerald-400' : 'bg-gold/20 text-gold'
            )}>
              {score.prayerScore}/5
            </span>
          </div>
          <PrayerTimesBar prayerLogs={prayers} />
          {/* Tap buttons to mark done */}
          <div className="grid grid-cols-5 gap-1.5 pt-1 border-t border-white/5">
            {PRAYERS.map((prayer) => (
              <button key={prayer} onClick={() => togglePrayer(prayer)}
                disabled={savingPrayer === prayer}
                className={cn(
                  'rounded-xl py-2 text-xs font-semibold transition-all active:scale-90',
                  prayers[prayer]
                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                    : 'bg-white/5 text-muted-foreground border border-white/10 hover:border-white/20'
                )}>
                {prayers[prayer] ? '✓' : prayer.slice(0, 3)}
              </button>
            ))}
          </div>
        </div>

        {/* Custom daily tasks */}
        {tasks.length > 0 && (
          <div className="nafs-card p-4 space-y-2">
            <p className="text-sm font-semibold text-foreground">📋 Today&apos;s tasks</p>
            {tasks.map((t, i) => (
              <div key={i} className={cn('flex items-center gap-3 text-sm', t.done && 'opacity-50')}>
                <div className={cn('h-4 w-4 rounded-full border flex-shrink-0',
                  t.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30'
                )}>
                  {t.done && <span className="text-white text-[9px] flex items-center justify-center h-full w-full">✓</span>}
                </div>
                <span className={cn(t.done && 'line-through text-muted-foreground')}>{t.text}</span>
              </div>
            ))}
            <Link href="/checkin" className="text-xs text-gold">Update →</Link>
          </div>
        )}

        {tasks.length === 0 && (
          <Link href="/checkin">
            <div className="nafs-card p-4 border-dashed border-white/20 flex items-center gap-3">
              <span className="text-xl">{isEvening ? '🌙' : '☀️'}</span>
              <div className="flex-1">
                <p className="text-sm font-medium text-foreground">
                  {isEvening ? 'Evening check-in' : 'Plan your day'}
                </p>
                <p className="text-xs text-muted-foreground">
                  {isEvening ? 'Tell AI what you did today' : 'Add your top tasks for today'}
                </p>
              </div>
              <ChevronRight size={16} className="text-muted-foreground" />
            </div>
          </Link>
        )}
      </div>

      {/* Habits quick summary */}
      {habits.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="font-semibold text-foreground">Habits</p>
            <Link href="/habits" className="text-xs text-gold">See all →</Link>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {habits.slice(0, 6).map((h) => {
              const done = habitLogs.find((l: any) => l.habit_id === h.id)?.completed
              return (
                <Link key={h.id} href="/habits">
                  <div className={cn(
                    'rounded-2xl border p-3 text-center transition-all active:scale-95',
                    done ? 'border-emerald-500/40 bg-emerald-500/10' : 'border-white/10 bg-white/5'
                  )}>
                    <p className="text-2xl">{h.emoji}</p>
                    <p className="text-[11px] font-medium text-foreground mt-1 truncate">{h.name.split(' ')[0]}</p>
                    <p className={cn('text-[10px] mt-0.5', done ? 'text-emerald-400' : 'text-muted-foreground')}>
                      {done ? '✓ done' : h.time_target_mins > 0 ? `${h.time_target_mins}m` : `${h.score_weight}pt`}
                    </p>
                  </div>
                </Link>
              )
            })}
          </div>
        </div>
      )}

      {/* Challenges */}
      {challenges.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="font-semibold text-foreground">Challenges</p>
            <Link href="/challenges" className="text-xs text-gold">See all →</Link>
          </div>
          {challenges.slice(0, 2).map((c: any) => {
            const daysIn = Math.max(1, Math.ceil((Date.now() - new Date(c.start_date).getTime()) / 86400000))
            const daysLeft = c.deadline ? daysUntil(c.deadline) : c.duration_days - daysIn
            const pct = Math.min(100, Math.round((daysIn / c.duration_days) * 100))
            const todayDone = c.challenge_checkins?.some((ci: any) => ci.date === today && ci.completed)

            return (
              <Link key={c.id} href="/challenges">
                <div className={cn('nafs-card p-4 flex items-center gap-3', todayDone && 'border-emerald-500/20')}>
                  <span className="text-2xl">{c.emoji}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-1">
                      <p className="text-sm font-medium text-foreground truncate">{c.title}</p>
                      <span className="text-sm font-bold text-orange-400 flex-shrink-0 ml-2">🔥 {c.current_streak}</span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-white/10">
                      <div className="h-full rounded-full bg-gradient-to-r from-primary to-gold transition-all" style={{ width: `${pct}%` }} />
                    </div>
                    <p className="text-[10px] text-muted-foreground mt-1">
                      {daysLeft > 0 ? `${daysLeft} days left` : 'Last day!'} · {todayDone ? '✅ done today' : '⏳ pending'}
                    </p>
                  </div>
                </div>
              </Link>
            )
          })}
        </div>
      )}

      {/* Goals */}
      {goals.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="font-semibold text-foreground">Goals</p>
            <Link href="/goals" className="text-xs text-gold">See all →</Link>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {goals.slice(0, 4).map((g: any) => (
              <Link key={g.id} href="/goals">
                <div className="nafs-card p-4">
                  <p className="text-xl">{g.emoji}</p>
                  <p className="text-xs font-medium text-foreground mt-1.5 line-clamp-2">{g.title}</p>
                  <div className="mt-2 h-1.5 w-full rounded-full bg-white/10">
                    <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${g.progress_pct}%` }} />
                  </div>
                  <p className="text-[10px] text-muted-foreground mt-1">{g.progress_pct}%</p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* Screen time shortcut */}
      <Link href="/screentime">
        <div className="nafs-card p-4 flex items-center gap-3 border-white/10 mb-8">
          <span className="text-2xl">📱</span>
          <div className="flex-1">
            <p className="text-sm font-semibold text-foreground">Screen time</p>
            <p className="text-xs text-muted-foreground">Upload today&apos;s screenshot — AI tracks your usage</p>
          </div>
          <ChevronRight size={16} className="text-muted-foreground" />
        </div>
      </Link>

    </div>
  )
}
