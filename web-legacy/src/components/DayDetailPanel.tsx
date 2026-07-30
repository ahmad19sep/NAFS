'use client'

import { TrendingUp, TrendingDown, Minus, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { formatPrettyDate, type DayScore } from '@/lib/history'

interface Props {
  selected: DayScore
  prevPct?: number
  today: string
  onClose: () => void
  children?: React.ReactNode
}

export default function DayDetailPanel({ selected, prevPct, today, onClose, children }: Props) {
  const delta = prevPct !== undefined ? selected.pct - prevPct : null
  const isToday = selected.date === today

  return (
    <div className="nafs-card p-4 mt-3 animate-slide-up">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[11px] text-muted-foreground">
            {isToday ? 'Today' : formatPrettyDate(selected.date)}
          </p>
          <div className="flex items-baseline gap-2">
            <span className={cn('text-3xl font-bold tabular-nums',
              selected.pct >= 75 ? 'text-emerald-400'
              : selected.pct >= 50 ? 'text-gold'
              : selected.pct > 0 ? 'text-orange-400'
              : 'text-muted-foreground'
            )}>
              {selected.pct}%
            </span>
            {selected.max > 0 && (
              <span className="text-xs text-muted-foreground tabular-nums">
                {selected.earned} / {selected.max}
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {delta !== null && (
            <div className={cn(
              'flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold',
              delta > 0
                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400'
                : delta < 0
                ? 'border-red-500/30 bg-red-500/10 text-red-400'
                : 'border-white/10 bg-white/5 text-muted-foreground'
            )}>
              {delta > 0 ? <TrendingUp size={12} /> : delta < 0 ? <TrendingDown size={12} /> : <Minus size={12} />}
              {delta > 0 ? '+' : ''}{delta}%
              <span className="text-[10px] opacity-70 ml-0.5">vs prev day</span>
            </div>
          )}
          <button onClick={onClose}
            className="h-7 w-7 rounded-md hover:bg-white/10 flex items-center justify-center text-muted-foreground">
            <X size={13} />
          </button>
        </div>
      </div>

      {/* Verdict line */}
      {delta !== null && (
        <p className="text-[11px] text-muted-foreground/80 mt-2">
          {delta > 5 ? '📈 Better than the previous day — keep the momentum.'
            : delta > 0 ? '🟢 Slight improvement vs the day before.'
            : delta === 0 ? '⚖️ Same as the day before.'
            : delta > -5 ? '🟡 Slightly worse than the day before.'
            : '📉 Worse than the day before — small reset tomorrow.'}
        </p>
      )}

      {/* Per-feature breakdown */}
      {children && <div className="mt-3 pt-3 border-t border-white/5">{children}</div>}
    </div>
  )
}
