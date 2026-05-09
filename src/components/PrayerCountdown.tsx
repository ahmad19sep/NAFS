'use client'

import { useState, useEffect } from 'react'

interface Props {
  nextPrayerName: string
  nextPrayerTime: string
}

export default function PrayerCountdown({ nextPrayerName, nextPrayerTime }: Props) {
  const [timeLeft, setTimeLeft] = useState('')

  useEffect(() => {
    function compute() {
      const [h, m] = nextPrayerTime.split(':').map(Number)
      const now = new Date()
      const target = new Date()
      target.setHours(h, m, 0, 0)
      if (target <= now) target.setDate(target.getDate() + 1)

      const diff = target.getTime() - now.getTime()
      const hours = Math.floor(diff / 3600000)
      const mins = Math.floor((diff % 3600000) / 60000)
      const secs = Math.floor((diff % 60000) / 1000)
      setTimeLeft(`${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`)
    }
    compute()
    const id = setInterval(compute, 1000)
    return () => clearInterval(id)
  }, [nextPrayerTime])

  return (
    <div className="flex items-center gap-3 rounded-2xl border border-primary/30 bg-primary/10 px-4 py-3">
      <span className="text-2xl">🕌</span>
      <div>
        <p className="text-xs text-muted-foreground">{nextPrayerName} in</p>
        <p className="font-mono text-lg font-bold text-gold tabular-nums">{timeLeft}</p>
      </div>
    </div>
  )
}
