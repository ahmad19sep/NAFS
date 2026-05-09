'use client'

import { useState, useEffect } from 'react'
import { cn } from '@/lib/utils'

interface PrayerTime { name: string; time: string; done: boolean }

interface Props {
  prayerLogs: Record<string, boolean>
}

const PRAYER_ICONS: Record<string, string> = {
  Fajr: '🌙', Dhuhr: '☀️', Asr: '🌤️', Maghrib: '🌅', Isha: '⭐'
}

export default function PrayerTimesBar({ prayerLogs }: Props) {
  const [prayers, setPrayers] = useState<PrayerTime[]>([
    { name: 'Fajr', time: '--:--', done: false },
    { name: 'Dhuhr', time: '--:--', done: false },
    { name: 'Asr', time: '--:--', done: false },
    { name: 'Maghrib', time: '--:--', done: false },
    { name: 'Isha', time: '--:--', done: false },
  ])
  const [nextPrayerName, setNextPrayerName] = useState('')
  const [countdown, setCountdown] = useState('')
  const [hijri, setHijri] = useState('')

  useEffect(() => {
    if (!navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(async (pos) => {
      try {
        const now = new Date()
        const res = await fetch(
          `https://api.aladhan.com/v1/timings/${now.getDate()}-${now.getMonth() + 1}-${now.getFullYear()}?latitude=${pos.coords.latitude}&longitude=${pos.coords.longitude}&method=2`
        )
        const json = await res.json()
        if (!json.data) return

        const t = json.data.timings
        const h = json.data.date.hijri
        setHijri(`${h.day} ${h.month.en} ${h.year} AH`)

        const prayerNames = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha']
        const times = [t.Fajr, t.Dhuhr, t.Asr, t.Maghrib, t.Isha]

        setPrayers(prayerNames.map((name, i) => ({
          name,
          time: times[i],
          done: prayerLogs[name] ?? false,
        })))

        // Start countdown to next prayer
        const updateCountdown = () => {
          const n = new Date()
          for (let i = 0; i < prayerNames.length; i++) {
            const [h, m] = times[i].split(':').map(Number)
            const target = new Date()
            target.setHours(h, m, 0, 0)
            if (target > n) {
              setNextPrayerName(prayerNames[i])
              const diff = target.getTime() - n.getTime()
              const hh = Math.floor(diff / 3600000)
              const mm = Math.floor((diff % 3600000) / 60000)
              const ss = Math.floor((diff % 60000) / 1000)
              setCountdown(`${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`)
              return
            }
          }
          setNextPrayerName('Fajr (tomorrow)')
        }
        updateCountdown()
        const id = setInterval(updateCountdown, 1000)
        return () => clearInterval(id) as unknown as void
      } catch {}
    }, () => {})
  }, [])

  return (
    <div className="space-y-3">
      {/* Hijri + countdown */}
      <div className="flex items-center justify-between">
        <p className="text-xs text-gold/80">{hijri}</p>
        {nextPrayerName && (
          <div className="flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1">
            <span className="text-xs text-muted-foreground">{nextPrayerName}</span>
            <span className="font-mono text-sm font-bold text-gold tabular-nums">{countdown}</span>
          </div>
        )}
      </div>

      {/* Prayer time row */}
      <div className="grid grid-cols-5 gap-1.5">
        {prayers.map((p) => (
          <div key={p.name}
            className={cn(
              'flex flex-col items-center rounded-xl border py-2.5 px-1 gap-1 transition-all',
              p.done
                ? 'border-emerald-500/50 bg-emerald-500/15'
                : 'border-white/10 bg-white/5'
            )}>
            <span className="text-base">{PRAYER_ICONS[p.name]}</span>
            <p className="text-[10px] font-medium text-foreground">{p.name}</p>
            <p className="text-[10px] text-muted-foreground tabular-nums">{p.time}</p>
            {p.done && <div className="h-1.5 w-1.5 rounded-full bg-emerald-400" />}
          </div>
        ))}
      </div>
    </div>
  )
}
