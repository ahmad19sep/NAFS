'use client'

import { useState, useEffect } from 'react'

interface Props {
  showClock?: boolean
  hijriDate?: string
}

export default function DateTimeHeader({ showClock = true, hijriDate }: Props) {
  const [time, setTime] = useState('')
  const [date, setDate] = useState('')

  useEffect(() => {
    function update() {
      const now = new Date()
      setTime(now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true }))
      setDate(now.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }))
    }
    update()
    const id = setInterval(update, 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <div className="flex items-center justify-between">
      <div>
        <p className="text-xs text-muted-foreground">{date}</p>
        {hijriDate && <p className="text-xs text-gold/70">{hijriDate}</p>}
      </div>
      {showClock && (
        <p className="text-2xl font-bold tabular-nums text-foreground tracking-tight">{time}</p>
      )}
    </div>
  )
}
