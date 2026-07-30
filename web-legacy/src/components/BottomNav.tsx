'use client'

import { useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, BarChart3, Sparkles, User, Plus } from 'lucide-react'
import { cn } from '@/lib/utils'
import QuickAddSheet from './QuickAddSheet'

const TABS = [
  { href: '/dashboard', icon: Home,       label: 'Home' },
  { href: '/history',   icon: BarChart3,  label: 'History' },
  { href: null,         icon: Plus,       label: 'Add' },          // center FAB
  { href: '/coach',     icon: Sparkles,   label: 'Coach' },
  { href: '/profile',   icon: User,       label: 'Profile' },
] as const

export default function BottomNav() {
  const pathname = usePathname()
  const [quickAddOpen, setQuickAddOpen] = useState(false)

  return (
    <>
      <nav className="bottom-nav">
        <div className="flex items-end justify-around px-2 py-2">
          {TABS.map((tab) => {
            const isActive = tab.href ? (pathname === tab.href || pathname.startsWith(tab.href + '/')) : false
            const isCenter = tab.href === null
            const Icon = tab.icon

            if (isCenter) {
              return (
                <button
                  key="add"
                  aria-label="Quick add"
                  onClick={() => setQuickAddOpen(true)}
                  className="flex flex-col items-center"
                >
                  <div className="relative">
                    {/* Glow ring */}
                    <div className="absolute inset-0 -m-1 rounded-full bg-gold/20 blur-md animate-pulse-gold" />
                    <div className="relative flex h-14 w-14 items-center justify-center rounded-full
                                    bg-gradient-to-br from-gold via-gold to-amber-600
                                    border-2 border-gold/60 -mt-5
                                    shadow-[0_8px_24px_rgba(201,162,39,0.4)]
                                    transition-transform active:scale-90">
                      <Plus size={26} className="text-[#0b1a2b] stroke-[3]" />
                    </div>
                  </div>
                  <span className="mt-1 text-[10px] font-semibold text-gold">{tab.label}</span>
                </button>
              )
            }

            return (
              <Link
                key={tab.href!}
                href={tab.href!}
                className={cn(
                  'flex flex-col items-center gap-0.5 px-2.5 py-1.5 rounded-xl transition-all duration-200 min-w-[60px]',
                  isActive ? 'text-gold' : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <div className={cn('relative flex items-center justify-center',
                  isActive && 'after:absolute after:-top-2.5 after:h-1 after:w-6 after:rounded-full after:bg-gold after:shadow-[0_0_8px_rgba(201,162,39,0.6)]'
                )}>
                  <Icon size={20} className={cn(isActive && 'drop-shadow-[0_0_6px_rgba(201,162,39,0.6)]')} />
                </div>
                <span className={cn('text-[10px]', isActive && 'font-semibold')}>{tab.label}</span>
              </Link>
            )
          })}
        </div>
      </nav>

      <QuickAddSheet open={quickAddOpen} onClose={() => setQuickAddOpen(false)} />
    </>
  )
}
