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
                    <div className="relative flex h-[52px] w-[52px] items-center justify-center rounded-2xl
                                    bg-gradient-to-b from-[#E0BC45] to-[#B8922A] -mt-5
                                    shadow-[0_6px_18px_rgba(201,162,39,0.35),inset_0_1px_0_rgba(255,255,255,0.35)]
                                    transition-transform active:scale-90">
                      <Plus size={24} className="text-[#0b1a2b] stroke-[2.5]" />
                    </div>
                  </div>
                  <span className="mt-1.5 text-[10px] font-medium text-gold/90">{tab.label}</span>
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
                  isActive && 'after:absolute after:-top-2 after:h-[3px] after:w-5 after:rounded-full after:bg-gold'
                )}>
                  <Icon size={20} strokeWidth={isActive ? 2.2 : 1.8} />
                </div>
                <span className={cn('text-[10px]', isActive ? 'font-semibold' : 'font-medium')}>{tab.label}</span>
              </Link>
            )
          })}
        </div>
      </nav>

      <QuickAddSheet open={quickAddOpen} onClose={() => setQuickAddOpen(false)} />
    </>
  )
}
