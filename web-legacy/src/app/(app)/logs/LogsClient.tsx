'use client'

import { useState } from 'react'
import { formatDate, getMoodEmoji, scoreColor, formatPullDays } from '@/lib/utils'

interface DailyLog {
  id: string
  date: string
  prayers: any
  dream_work_hours: number
  study_hours: number
  screen_time_mins: number
  mood: number
  reflection_text: string | null
  photo_urls: string[]
  weighted_hours_today: number
  identity_score: number
  todays_pull_days: number
  sleep_start: string | null
  sleep_end: string | null
}

interface Props {
  logs: DailyLog[]
}

export default function LogsClient({ logs }: Props) {
  const [filter, setFilter] = useState<'all' | 'photos' | 'high' | 'low'>('all')
  const [selectedLog, setSelectedLog] = useState<DailyLog | null>(null)

  const filtered = logs.filter((l) => {
    if (filter === 'photos') return l.photo_urls?.length > 0
    if (filter === 'high') return l.identity_score >= 70
    if (filter === 'low') return l.identity_score < 40
    return true
  })

  function getPrayerSummary(prayers: any): string {
    if (!prayers) return '0/5'
    const done = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'].filter((n) => prayers[n]?.completed).length
    return `${done}/5`
  }

  if (selectedLog) {
    return (
      <div className="mx-auto max-w-md px-4 space-y-5">
        <div className="pt-2">
          <button onClick={() => setSelectedLog(null)} className="text-sm text-muted-foreground mb-3">← Back</button>
          <h2 className="text-xl font-bold text-foreground">{formatDate(selectedLog.date)}</h2>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="nafs-card p-4 text-center">
            <p className={`text-3xl font-bold tabular-nums ${scoreColor(selectedLog.identity_score)}`}>
              {selectedLog.identity_score}%
            </p>
            <p className="text-xs text-muted-foreground mt-1">Identity score</p>
          </div>
          <div className="nafs-card p-4 text-center">
            <p className={`text-2xl font-bold ${selectedLog.todays_pull_days >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
              {selectedLog.todays_pull_days >= 0 ? '+' : ''}{selectedLog.todays_pull_days.toFixed(1)}d
            </p>
            <p className="text-xs text-muted-foreground mt-1">Days pulled</p>
          </div>
        </div>

        <div className="nafs-card p-4 space-y-3">
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">Prayers</span>
            <span className="text-foreground font-medium">{getPrayerSummary(selectedLog.prayers)}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">Dream work</span>
            <span className="text-foreground font-medium">{selectedLog.dream_work_hours}h</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">Weighted hours</span>
            <span className="text-gold font-bold">{selectedLog.weighted_hours_today.toFixed(1)}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">Screen time</span>
            <span className={`font-medium ${selectedLog.screen_time_mins > 120 ? 'text-red-400' : 'text-emerald-400'}`}>
              {selectedLog.screen_time_mins}m
            </span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">Mood</span>
            <span>{getMoodEmoji(selectedLog.mood)} {selectedLog.mood}/10</span>
          </div>
          {selectedLog.sleep_start && selectedLog.sleep_end && (
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Sleep</span>
              <span className="text-foreground">{selectedLog.sleep_start} → {selectedLog.sleep_end}</span>
            </div>
          )}
        </div>

        {selectedLog.photo_urls?.length > 0 && (
          <div className="space-y-2">
            <p className="section-header">Photo proof</p>
            <div className="grid grid-cols-2 gap-2">
              {selectedLog.photo_urls.map((url, i) => (
                // eslint-disable-next-line @next/next/no-img-element
                <img key={i} src={url} alt={`Proof ${i + 1}`} className="rounded-xl w-full h-40 object-cover" />
              ))}
            </div>
          </div>
        )}

        {selectedLog.reflection_text && (
          <div className="nafs-card p-4">
            <p className="section-header mb-2">Reflection</p>
            <p className="text-sm text-foreground leading-relaxed">{selectedLog.reflection_text}</p>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md px-4 space-y-5">
      <div className="pt-2">
        <h1 className="text-2xl font-bold text-foreground">Past Logs</h1>
        <p className="mt-1 text-sm text-muted-foreground">{logs.length} days recorded</p>
      </div>

      {/* Filter */}
      <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-1">
        {[
          { key: 'all', label: 'All' },
          { key: 'photos', label: '📸 Photos' },
          { key: 'high', label: '🔥 High score' },
          { key: 'low', label: '😔 Low score' },
        ].map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setFilter(key as any)}
            className={`flex-shrink-0 rounded-full px-4 py-2 text-sm font-medium transition-all
              ${filter === key
                ? 'bg-primary text-white'
                : 'border border-white/10 bg-white/5 text-muted-foreground hover:bg-white/10'}`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Log list */}
      <div className="space-y-3 pb-8">
        {filtered.map((log) => (
          <button
            key={log.id}
            onClick={() => setSelectedLog(log)}
            className="w-full nafs-card p-4 text-left hover:border-white/20 transition-all active:scale-98"
          >
            <div className="flex items-center justify-between mb-3">
              <p className="font-semibold text-foreground text-sm">{formatDate(log.date)}</p>
              <div className="flex items-center gap-2">
                {log.photo_urls?.length > 0 && <span className="text-xs">📸</span>}
                <span className={`text-sm font-bold ${scoreColor(log.identity_score)}`}>
                  {log.identity_score}%
                </span>
              </div>
            </div>

            <div className="flex items-center gap-4 text-xs text-muted-foreground">
              <span>🕌 {getPrayerSummary(log.prayers)}</span>
              <span>⚡ {log.weighted_hours_today.toFixed(1)} wh</span>
              <span className={log.todays_pull_days >= 0 ? 'text-emerald-400' : 'text-red-400'}>
                {log.todays_pull_days >= 0 ? '✅' : '⚠️'} {formatPullDays(log.todays_pull_days)}
              </span>
              <span>{getMoodEmoji(log.mood)}</span>
            </div>
          </button>
        ))}

        {filtered.length === 0 && (
          <p className="text-center py-10 text-muted-foreground text-sm">No logs match this filter.</p>
        )}
      </div>
    </div>
  )
}
