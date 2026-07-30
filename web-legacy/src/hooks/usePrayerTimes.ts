'use client'

import { useState, useEffect } from 'react'
import type { PrayerTime } from '@/types'

export function usePrayerTimes() {
  const [timings, setTimings] = useState<PrayerTime | null>(null)
  const [hijriDate, setHijriDate] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function fetchTimings() {
      if (!navigator.geolocation) { setLoading(false); return }

      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          try {
            const now = new Date()
            const res = await fetch(
              `https://api.aladhan.com/v1/timings/${now.getDate()}-${now.getMonth() + 1}-${now.getFullYear()}?latitude=${pos.coords.latitude}&longitude=${pos.coords.longitude}&method=2`
            )
            const json = await res.json()
            if (json.data) {
              setTimings(json.data.timings)
              const h = json.data.date.hijri
              setHijriDate(`${h.day} ${h.month.en} ${h.year} AH`)
            }
          } catch {}
          setLoading(false)
        },
        () => setLoading(false)
      )
    }
    fetchTimings()
  }, [])

  function getNextPrayer(): { name: string; time: string } | null {
    if (!timings) return null
    const now = new Date()
    const prayers = [
      { name: 'Fajr', time: timings.Fajr },
      { name: 'Dhuhr', time: timings.Dhuhr },
      { name: 'Asr', time: timings.Asr },
      { name: 'Maghrib', time: timings.Maghrib },
      { name: 'Isha', time: timings.Isha },
    ]
    return prayers.find((p) => {
      const [h, m] = p.time.split(':').map(Number)
      const t = new Date()
      t.setHours(h, m, 0, 0)
      return t > now
    }) ?? prayers[0]
  }

  return { timings, hijriDate, loading, getNextPrayer }
}
