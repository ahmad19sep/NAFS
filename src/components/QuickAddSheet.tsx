'use client'

import Link from 'next/link'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useBodyScrollLock } from '@/hooks/useBodyScrollLock'

interface Props {
  open: boolean
  onClose: () => void
}

const ACTIONS: { href: string; emoji: string; label: string; tone: string; ring: string }[] = [
  { href: '/tasks',      emoji: '✅', label: 'New task',      tone: 'from-emerald-500/25 to-emerald-700/10', ring: 'ring-emerald-400/30' },
  { href: '/habits',     emoji: '🔄', label: 'New habit',     tone: 'from-cyan-500/25 to-blue-700/10',       ring: 'ring-cyan-400/30' },
  { href: '/challenges', emoji: '🎯', label: 'New challenge', tone: 'from-pink-500/25 to-rose-700/10',       ring: 'ring-pink-400/30' },
  { href: '/goals',      emoji: '🏆', label: 'New goal',      tone: 'from-yellow-500/20 to-orange-700/10',   ring: 'ring-yellow-400/30' },
  { href: '/deen',       emoji: '🕌', label: 'Log prayer',    tone: 'from-yellow-500/25 to-amber-700/10',    ring: 'ring-amber-400/30' },
  { href: '/health',     emoji: '❤️', label: 'Log health',    tone: 'from-red-500/25 to-pink-700/10',        ring: 'ring-red-400/30' },
]

export default function QuickAddSheet({ open, onClose }: Props) {
  useBodyScrollLock(open)
  if (!open) return null

  return (
    <div
      className="modal-overlay items-end backdrop-blur-sm animate-in fade-in"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-t-3xl border-t border-white/10
                   bg-gradient-to-b from-[#16314a] via-[#0f2235] to-[#0b1a2b]
                   shadow-[0_-12px_40px_rgba(0,0,0,0.4)] animate-slide-up
                   pb-[max(env(safe-area-inset-bottom),1rem)]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Grabber */}
        <div className="flex justify-center pt-2.5 pb-1">
          <div className="h-1 w-12 rounded-full bg-white/20" />
        </div>

        {/* Header */}
        <div className="flex items-center justify-between px-5 pt-1 pb-3">
          <p className="text-base font-bold text-foreground">Quick add</p>
          <button onClick={onClose}
            className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors">
            <X size={16} className="text-muted-foreground" />
          </button>
        </div>

        {/* Action grid */}
        <div className="px-5 grid grid-cols-3 gap-3">
          {ACTIONS.map((a) => (
            <Link key={a.href} href={a.href} onClick={onClose}
              className={cn(
                'rounded-2xl border border-white/10 bg-gradient-to-br p-4 text-center transition-all active:scale-95 hover:border-white/30',
                a.tone
              )}>
              <div className={cn('mx-auto h-10 w-10 rounded-xl bg-white/8 ring-1 flex items-center justify-center text-xl mb-2', a.ring)}>
                {a.emoji}
              </div>
              <p className="text-[11px] font-semibold text-foreground leading-tight">{a.label}</p>
            </Link>
          ))}
        </div>

        <div className="px-5 mt-3 pb-3 text-center">
          <p className="text-[10px] text-muted-foreground">
            Tap a card to open its create flow
          </p>
        </div>
      </div>
    </div>
  )
}
