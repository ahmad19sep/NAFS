'use client'

import { cn } from '@/lib/utils'
import type { DayScore } from '@/lib/history'

interface Props {
  days: DayScore[]
  selectedDate: string | null
  onSelect: (date: string | null) => void
  today: string
  title?: string
  /** Bar color override — defaults to score-based gradient */
  accent?: 'gold' | 'teal' | 'emerald' | 'pink' | 'red'
}

function barColor(pct: number, accent: Props['accent']): string {
  if (pct === 0) return 'bg-white/8'
  // Score-based gradient by default
  if (!accent) {
    if (pct < 30) return 'bg-red-500/60'
    if (pct < 50) return 'bg-orange-500/70'
    if (pct < 75) return 'bg-gold/80'
    return 'bg-emerald-400'
  }
  // Single-accent variants (intensity by score)
  const intensity = pct >= 80 ? '/100' : pct >= 60 ? '/80' : pct >= 40 ? '/60' : '/40'
  const map = { gold: 'bg-gold', teal: 'bg-teal-light', emerald: 'bg-emerald-400', pink: 'bg-pink-400', red: 'bg-red-400' }
  return map[accent] + intensity
}

export default function HistoryGraph({ days, selectedDate, onSelect, today, title, accent }: Props) {
  const avg = days.length > 0
    ? Math.round(days.filter((d) => d.max > 0).reduce((s, d) => s + d.pct, 0) /
        Math.max(1, days.filter((d) => d.max > 0).length))
    : 0
  const best = Math.max(0, ...days.map((d) => d.pct))

  return (
    <div className="nafs-card p-4">
      <div className="flex items-center justify-between mb-2">
        <p className="section-header">{title ?? 'Last 30 days'}</p>
        <div className="flex items-center gap-3 text-[10px]">
          <span className="text-muted-foreground">avg <span className="text-foreground font-semibold tabular-nums">{avg}%</span></span>
          <span className="text-muted-foreground">best <span className="text-emerald-400 font-semibold tabular-nums">{best}%</span></span>
        </div>
      </div>

      <div className="flex items-end gap-[3px] h-20">
        {days.map((d) => {
          const isSelected = d.date === selectedDate
          const isToday = d.date === today
          // Min visible height so empty days still register; 0% = 2px
          const height = d.pct === 0 ? 2 : Math.max(4, (d.pct / 100) * 76)
          return (
            <button
              key={d.date}
              aria-label={`${d.date}: ${d.pct}%`}
              onClick={() => onSelect(isSelected ? null : d.date)}
              className={cn(
                'flex-1 rounded-sm transition-all hover:opacity-100 cursor-pointer min-w-[3px]',
                barColor(d.pct, accent),
                isSelected
                  ? 'ring-2 ring-gold ring-offset-1 ring-offset-[#0f2235] opacity-100'
                  : isToday
                  ? 'opacity-100 outline outline-1 outline-white/30'
                  : 'opacity-75 hover:opacity-100'
              )}
              style={{ height: `${height}px` }}
            />
          )
        })}
      </div>
      <div className="flex justify-between text-[10px] text-muted-foreground mt-1.5">
        <span>30 days ago</span>
        <span>today →</span>
      </div>
    </div>
  )
}
