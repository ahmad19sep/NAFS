'use client'

import { useState, useEffect } from 'react'
import { todayString } from '@/lib/utils'

interface LogEntry {
  date: string
  prayers: any
  identity_score: number
}

interface QuranEntry {
  date: string
  pages_read: number
}

interface Props {
  logs: LogEntry[]
  quranLogs: QuranEntry[]
}

const PRAYER_NAMES = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha']

function getPrayerCount(prayers: any): { completed: number; onTime: number } {
  if (!prayers) return { completed: 0, onTime: 0 }
  let completed = 0, onTime = 0
  PRAYER_NAMES.forEach((n) => {
    if (prayers[n]?.completed) completed++
    if (prayers[n]?.onTime) onTime++
  })
  return { completed, onTime }
}

export default function DeenClient({ logs, quranLogs }: Props) {
  const [hijriDate, setHijriDate] = useState('')
  const [dhikrCount, setDhikrCount] = useState(0)

  useEffect(() => {
    async function loadHijri() {
      try {
        const now = new Date()
        const ctrl = new AbortController()
        const timer = setTimeout(() => ctrl.abort(), 5000)
        const res = await fetch(
          `https://api.aladhan.com/v1/gToH/${now.getDate()}-${now.getMonth() + 1}-${now.getFullYear()}`,
          { signal: ctrl.signal }
        )
        clearTimeout(timer)
        const json = await res.json()
        if (json.data) {
          const h = json.data.hijri
          setHijriDate(`${h.day} ${h.month.en} ${h.year} AH`)
        }
      } catch {
        // Non-fatal — Hijri date is decorative
      }
    }
    loadHijri()
  }, [])

  // Stats
  const totalQuranPages = quranLogs.reduce((s, l) => s + l.pages_read, 0)
  const quranDays = quranLogs.filter((l) => l.pages_read > 0).length
  const avgQuranPerDay = quranDays > 0 ? (totalQuranPages / quranDays).toFixed(1) : '0'

  const prayerStats = logs.map((l) => getPrayerCount(l.prayers))
  const totalOnTime = prayerStats.reduce((s, p) => s + p.onTime, 0)
  const totalPossible = prayerStats.reduce((s, p) => s + p.completed, 0)
  const onTimePct = totalPossible > 0 ? Math.round((totalOnTime / totalPossible) * 100) : 0

  // Build heatmap data (90 days)
  const heatmapDays: { date: string; value: number }[] = []
  const today = new Date()
  for (let i = 89; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const dateStr = d.toISOString().split('T')[0]
    const log = logs.find((l) => l.date === dateStr)
    const prayers = log ? getPrayerCount(log.prayers) : null
    const value = prayers ? Math.round((prayers.completed / 5) * 3) : 0
    heatmapDays.push({ date: dateStr, value })
  }

  // Quran heatmap
  const quranMap: Record<string, number> = {}
  quranLogs.forEach((l) => { quranMap[l.date] = l.pages_read })

  // Streak calculation for prayers
  let prayerStreak = 0
  const today2 = todayString()
  for (let i = logs.length - 1; i >= 0; i--) {
    const l = logs[i]
    const pc = getPrayerCount(l.prayers)
    if (pc.completed === 5) prayerStreak++
    else break
  }

  function getCellColor(value: number) {
    if (value === 0) return 'bg-white/5'
    if (value === 1) return 'bg-primary/30'
    if (value === 2) return 'bg-primary/60'
    return 'bg-emerald-400'
  }

  return (
    <div className="mx-auto max-w-md space-y-6 px-4">
      <div className="pt-2">
        <p className="text-xs text-muted-foreground">{hijriDate}</p>
        <h1 className="text-2xl font-bold text-foreground">Deen Tracker</h1>
        <p className="mt-1 text-sm text-muted-foreground">The foundation of everything.</p>
      </div>

      {/* Prayer stats */}
      <div className="grid grid-cols-3 gap-3">
        <div className="nafs-card p-4 text-center">
          <p className="text-2xl font-bold text-gold tabular-nums">{onTimePct}%</p>
          <p className="text-xs text-muted-foreground mt-1">On-time</p>
        </div>
        <div className="nafs-card p-4 text-center">
          <p className="text-2xl font-bold text-emerald-400 tabular-nums">{prayerStreak}</p>
          <p className="text-xs text-muted-foreground mt-1">Day streak</p>
        </div>
        <div className="nafs-card p-4 text-center">
          <p className="text-2xl font-bold text-foreground tabular-nums">{quranDays}</p>
          <p className="text-xs text-muted-foreground mt-1">Quran days</p>
        </div>
      </div>

      {/* Prayer heatmap */}
      <div className="nafs-card p-4">
        <p className="section-header mb-3">Prayer heatmap (90 days)</p>
        <div className="grid gap-1" style={{ gridTemplateColumns: 'repeat(13, 1fr)' }}>
          {heatmapDays.map((day) => (
            <div
              key={day.date}
              title={day.date}
              className={`h-5 rounded-sm transition-colors ${getCellColor(day.value)}`}
            />
          ))}
        </div>
        <div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
          <div className="h-3 w-3 rounded-sm bg-white/5" /> None
          <div className="h-3 w-3 rounded-sm bg-emerald-400" /> All 5
        </div>
      </div>

      {/* Dhikr counter */}
      <div className="nafs-card p-5">
        <p className="section-header mb-4">📿 Dhikr counter</p>
        <div className="flex flex-col items-center gap-4">
          <div className="text-6xl font-bold tabular-nums text-gold">{dhikrCount}</div>
          <div className="grid grid-cols-3 gap-3 w-full">
            {[33, 33, 34].map((target, i) => {
              const labels = ['SubhanAllah', 'Alhamdulillah', 'Allahu Akbar']
              const starts = [0, 33, 66]
              const isDone = dhikrCount >= starts[i] + target
              return (
                <div key={i} className={`rounded-xl border p-3 text-center
                  ${isDone ? 'border-emerald-500/50 bg-emerald-500/10' : 'border-white/10 bg-white/5'}`}>
                  <p className="text-xs text-muted-foreground">{labels[i]}</p>
                  <p className={`font-bold tabular-nums ${isDone ? 'text-emerald-400' : 'text-gold'}`}>
                    {Math.min(Math.max(0, dhikrCount - starts[i]), target)}/{target}
                  </p>
                </div>
              )
            })}
          </div>
          <div className="flex gap-3 w-full">
            <button
              onClick={() => setDhikrCount((c) => c + 1)}
              className="flex-1 rounded-2xl bg-primary py-5 font-bold text-2xl text-white
                         active:scale-95 transition-transform shadow-lg"
            >
              +1
            </button>
            <button
              onClick={() => setDhikrCount(0)}
              className="rounded-2xl border border-white/10 bg-white/5 px-5 text-sm text-muted-foreground
                         active:scale-95 transition-transform"
            >
              Reset
            </button>
          </div>
        </div>
      </div>

      {/* Quran progress */}
      <div className="nafs-card p-5">
        <p className="section-header mb-4">📖 Quran</p>
        <div className="space-y-3">
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Total pages read</span>
            <span className="font-bold tabular-nums text-gold">{totalQuranPages}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Days with Quran</span>
            <span className="font-bold tabular-nums text-foreground">{quranDays}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">Avg pages/day</span>
            <span className="font-bold tabular-nums text-foreground">{avgQuranPerDay}</span>
          </div>

          {/* Progress to full Quran (604 pages) */}
          <div className="mt-2">
            <div className="flex justify-between text-xs text-muted-foreground mb-1">
              <span>Full Quran</span>
              <span>{Math.min(100, Math.round((totalQuranPages / 604) * 100))}%</span>
            </div>
            <div className="h-2 w-full rounded-full bg-white/10">
              <div
                className="h-full rounded-full bg-gradient-to-r from-primary to-emerald-400 transition-all"
                style={{ width: `${Math.min(100, (totalQuranPages / 604) * 100)}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground mt-1">{604 - Math.min(604, totalQuranPages)} pages remaining</p>
          </div>
        </div>
      </div>
    </div>
  )
}
