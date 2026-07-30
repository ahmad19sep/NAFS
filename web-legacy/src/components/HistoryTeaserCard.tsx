'use client'

import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { DayScore } from '@/lib/history'

type Accent = 'gold' | 'teal' | 'emerald' | 'pink' | 'red' | 'purple'

interface Props {
  days: DayScore[]
  title: string
  href: string
  /** Override default "avg X% · tap for breakdown" subtitle */
  subtitle?: string
  emoji?: string
  accent?: Accent
}

const ACCENT_BG: Record<Accent, string> = {
  gold:    'from-gold/10 via-white/3 to-transparent border-gold/25 hover:border-gold/40',
  teal:    'from-cyan-500/15 via-white/3 to-transparent border-cyan-400/25 hover:border-cyan-400/40',
  emerald: 'from-emerald-500/12 via-white/3 to-transparent border-emerald-400/25 hover:border-emerald-400/40',
  pink:    'from-pink-500/12 via-white/3 to-transparent border-pink-400/25 hover:border-pink-400/40',
  red:     'from-red-500/10 via-white/3 to-transparent border-red-400/25 hover:border-red-400/40',
  purple:  'from-fuchsia-500/12 via-white/3 to-transparent border-fuchsia-400/25 hover:border-fuchsia-400/40',
}
const ACCENT_BLUR: Record<Accent, string> = {
  gold:    'bg-gold/12',
  teal:    'bg-cyan-500/15',
  emerald: 'bg-emerald-500/12',
  pink:    'bg-pink-500/12',
  red:     'bg-red-500/12',
  purple:  'bg-fuchsia-500/12',
}
const ACCENT_ICON: Record<Accent, string> = {
  gold:    'bg-gold/15 border-gold/30',
  teal:    'bg-cyan-500/15 border-cyan-400/30',
  emerald: 'bg-emerald-500/15 border-emerald-400/30',
  pink:    'bg-pink-500/15 border-pink-400/30',
  red:     'bg-red-500/15 border-red-400/30',
  purple:  'bg-fuchsia-500/15 border-fuchsia-400/30',
}

export default function HistoryTeaserCard({
  days, title, href, subtitle, emoji = '📊', accent = 'gold',
}: Props) {
  const valid = days.filter((d) => d.max > 0)
  const avg = valid.length > 0
    ? Math.round(valid.reduce((s, d) => s + d.pct, 0) / valid.length)
    : 0
  const best = valid.length > 0 ? Math.max(...valid.map((d) => d.pct)) : 0
  const computedSub = subtitle ?? `avg ${avg}% · best ${best}% · tap for breakdown`

  return (
    <Link href={href} className="block">
      <div className={cn(
        'relative overflow-hidden rounded-2xl border bg-gradient-to-br p-4 transition-all active:scale-[0.99]',
        ACCENT_BG[accent]
      )}>
        <div className={cn('pointer-events-none absolute -right-4 -top-4 h-24 w-24 rounded-full blur-2xl', ACCENT_BLUR[accent])} />
        <div className="relative">
          <div className="flex items-center justify-between mb-2.5">
            <div className="flex items-center gap-2 min-w-0">
              <div className={cn('h-9 w-9 rounded-xl border flex items-center justify-center text-base flex-shrink-0', ACCENT_ICON[accent])}>
                {emoji}
              </div>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-foreground leading-tight truncate">{title}</p>
                <p className="text-[10px] text-muted-foreground tabular-nums truncate">{computedSub}</p>
              </div>
            </div>
            <ChevronRight size={16} className="text-muted-foreground flex-shrink-0" />
          </div>
          <Sparkline days={days} />
        </div>
      </div>
    </Link>
  )
}

function Sparkline({ days }: { days: DayScore[] }) {
  return (
    <div className="flex items-end gap-[2px] h-10">
      {days.map((d) => {
        const height = d.pct === 0 ? 2 : Math.max(4, (d.pct / 100) * 36)
        const color =
          d.pct === 0  ? 'bg-white/8'
          : d.pct < 40 ? 'bg-red-500/50'
          : d.pct < 65 ? 'bg-orange-500/70'
          : d.pct < 85 ? 'bg-gold/80'
          :              'bg-emerald-400'
        return (
          <div key={d.date}
            className={cn('flex-1 rounded-sm min-w-[3px] opacity-85', color)}
            style={{ height: `${height}px` }} />
        )
      })}
    </div>
  )
}
