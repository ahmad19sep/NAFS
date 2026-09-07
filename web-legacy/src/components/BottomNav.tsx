'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, BarChart3, Sparkles, User, Plus } from 'lucide-react'
import { cn } from '@/lib/utils'
import QuickAddSheet from './QuickAddSheet'
import { QUICK_ADD_EVENT, type QuickAddIntent } from '@/lib/quick-add'

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
  // Set when a card or the coach opens the sheet with a starting point;
  // cleared when the + button opens it plain.
  const [quickAddInitial, setQuickAddInitial] = useState<QuickAddIntent | null>(null)

  useEffect(() => {
    const onOpen = (e: Event) => {
      setQuickAddInitial((e as CustomEvent<QuickAddIntent>).detail ?? null)
      setQuickAddOpen(true)
    }
    window.addEventListener(QUICK_ADD_EVENT, onOpen)
    return () => window.removeEventListener(QUICK_ADD_EVENT, onOpen)
  }, [])

  return (
    <>
      {/* Floating dock */}
      <nav className="pointer-events-none fixed inset-x-0 bottom-0 z-50 px-4"
        style={{ paddingBottom: 'calc(env(safe-area-inset-bottom, 0px) + 10px)' }}>
        <div className="pointer-events-auto mx-auto flex max-w-[400px] items-center justify-between
                        rounded-[26px] border border-white/10 px-2 py-1.5
                        shadow-[0_18px_40px_rgba(0,0,0,0.5),inset_0_1px_0_rgba(255,255,255,0.06)]
                        backdrop-blur-xl"
          style={{ background: 'linear-gradient(180deg, rgba(20,42,64,0.92), rgba(9,21,35,0.96))' }}>
          {TABS.map((tab) => {
            const isActive = tab.href ? (pathname === tab.href || pathname.startsWith(tab.href + '/')) : false
            const isCenter = tab.href === null
            const Icon = tab.icon

            if (isCenter) {
              return (
                <button
                  key="add"
                  aria-label="Quick add"
                  onClick={() => { setQuickAddInitial(null); setQuickAddOpen(true) }}
                  className="relative -mt-7 flex flex-col items-center px-1"
                >
                  <div className="flex h-14 w-14 items-center justify-center rounded-[20px]
                                  bg-gradient-to-b from-[#E8C547] to-[#B8922A]
                                  ring-[5px] ring-[#0a1622]
                                  shadow-[0_10px_24px_rgba(201,162,39,0.35),inset_0_1.5px_0_rgba(255,255,255,0.4)]
                                  transition-all active:scale-90 active:shadow-[0_4px_12px_rgba(201,162,39,0.3)]">
                    <Plus size={24} className="text-[#0b1a2b] stroke-[2.5]" />
                  </div>
                </button>
              )
            }

            return (
              <Link
                key={tab.href!}
                href={tab.href!}
                className={cn(
                  'relative flex flex-1 flex-col items-center gap-1 rounded-2xl py-2 transition-all duration-200',
                  isActive ? 'text-gold' : 'text-muted-foreground/70 hover:text-foreground'
                )}
              >
                {/* Active glow pill */}
                <span className={cn(
                  'pointer-events-none absolute inset-x-1 inset-y-0.5 rounded-2xl transition-opacity duration-300',
                  isActive ? 'opacity-100 bg-gold/[0.09]' : 'opacity-0'
                )} />
                <Icon size={20} strokeWidth={isActive ? 2.2 : 1.8} className="relative" />
                <span className={cn('relative text-[9.5px] leading-none tracking-wide',
                  isActive ? 'font-semibold' : 'font-medium'
                )}>{tab.label}</span>
              </Link>
            )
          })}
        </div>
      </nav>

      <QuickAddSheet open={quickAddOpen} initial={quickAddInitial} onClose={() => setQuickAddOpen(false)} />
    </>
  )
}
