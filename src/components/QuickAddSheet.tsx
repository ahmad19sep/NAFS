'use client'

import Link from 'next/link'
import {
  X, ListChecks, Repeat, Flame, Trophy, MoonStar, HeartPulse, type LucideIcon,
} from 'lucide-react'
import { useBodyScrollLock } from '@/hooks/useBodyScrollLock'
import { useDeenEnabled } from '@/hooks/useDeenEnabled'

interface Props {
  open: boolean
  onClose: () => void
}

const ACTIONS: { href: string; icon: LucideIcon; label: string }[] = [
  { href: '/tasks',      icon: ListChecks, label: 'New task' },
  { href: '/habits',     icon: Repeat,     label: 'New habit' },
  { href: '/challenges', icon: Flame,      label: 'New challenge' },
  { href: '/goals',      icon: Trophy,     label: 'New goal' },
  { href: '/deen',       icon: MoonStar,   label: 'Log prayer' },
  { href: '/health',     icon: HeartPulse, label: 'Log health' },
]

export default function QuickAddSheet({ open, onClose }: Props) {
  useBodyScrollLock(open)
  const deenEnabled = useDeenEnabled()
  if (!open) return null

  const actions = ACTIONS.filter((a) => deenEnabled || a.href !== '/deen')

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
        <div className="px-5 grid grid-cols-3 gap-2.5">
          {actions.map((a) => (
            <Link key={a.href} href={a.href} onClick={onClose}
              className="rounded-2xl border border-white/10 bg-white/[0.04] p-4 text-center
                         transition-all active:scale-95 hover:border-gold/30 hover:bg-white/[0.07]">
              <div className="mx-auto h-10 w-10 rounded-xl border border-gold/20 bg-gold/[0.07]
                              flex items-center justify-center mb-2">
                <a.icon size={17} strokeWidth={2} className="text-gold" />
              </div>
              <p className="text-[11px] font-medium text-foreground leading-tight">{a.label}</p>
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
