'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, Zap, Star, Target } from 'lucide-react'
import { cn } from '@/lib/utils'

const TABS = [
  { href: '/dashboard', icon: Home, label: 'Home' },
  { href: '/habits', icon: Zap, label: 'Habits' },
  { href: '/checkin', icon: null, label: 'Check-in' },
  { href: '/challenges', icon: Star, label: 'Challenges' },
  { href: '/goals', icon: Target, label: 'Goals' },
]

// Screen time accessible via home card & /screentime route

export default function BottomNav() {
  const pathname = usePathname()

  return (
    <nav className="bottom-nav">
      <div className="flex items-center justify-around px-2 py-2">
        {TABS.map((tab) => {
          const isActive = pathname === tab.href || pathname.startsWith(tab.href + '/')
          const isCenter = tab.label === 'Check-in'

          if (isCenter) {
            return (
              <Link key={tab.href} href={tab.href} className="flex flex-col items-center">
                <div className="flex h-14 w-14 items-center justify-center rounded-full
                                bg-gradient-to-br from-primary to-teal-light
                                border-2 border-gold/40 -mt-5 shadow-lg
                                transition-transform active:scale-90">
                  <span className="text-2xl">✏️</span>
                </div>
                <span className="mt-1 text-[10px] text-muted-foreground">{tab.label}</span>
              </Link>
            )
          }

          const Icon = tab.icon!
          return (
            <Link
              key={tab.href}
              href={tab.href}
              className={cn(
                'flex flex-col items-center gap-1 px-3 py-1 rounded-xl transition-all duration-200',
                isActive ? 'text-gold' : 'text-muted-foreground hover:text-foreground'
              )}
            >
              <Icon size={22} className={cn(isActive && 'drop-shadow-[0_0_6px_rgba(201,162,39,0.6)]')} />
              <span className="text-[10px]">{tab.label}</span>
            </Link>
          )
        })}
      </div>
    </nav>
  )
}
