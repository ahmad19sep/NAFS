import { useState, useCallback } from 'react'
import { View, Text, TouchableOpacity, Pressable } from 'react-native'
import { Tabs, usePathname, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Home, BarChart3, Sparkles, User, Plus, X } from 'lucide-react-native'

// ─── Quick-add FAB sheet ───────────────────────────────────────────────────────
const QUICK_ACTIONS = [
  { label: 'Morning plan',  emoji: '☀️', route: '/(app)/checkin'    },
  { label: 'Add Task',      emoji: '✅', route: '/(app)/tasks'      },
  { label: 'Log Habit',     emoji: '🔥', route: '/(app)/habits'     },
  { label: 'Log Health',    emoji: '💪', route: '/(app)/health'     },
  { label: 'Log Prayer',    emoji: '🕌', route: '/(app)/deen'       },
  { label: 'Log Quran',     emoji: '📖', route: '/(app)/deen'       },
  { label: 'Add Challenge', emoji: '🎯', route: '/(app)/challenges' },
  { label: 'My Dream',      emoji: '🌟', route: '/(app)/dreams'     },
  { label: 'Ask NAFS',      emoji: '✨', route: '/(app)/coach'      },
]

function QuickAddOverlay({ onClose }: { onClose: () => void }) {
  const router = useRouter()
  const insets = useSafeAreaInsets()

  function go(route: string) {
    onClose()
    router.push(route as any)
  }

  return (
    <View
      className="absolute inset-0 z-50"
      style={{ backgroundColor: 'rgba(11,26,43,0.92)' }}
    >
      <Pressable className="flex-1" onPress={onClose} />
      <View
        className="bg-card rounded-t-3xl border-t border-white/10 px-5 pt-4"
        style={{ paddingBottom: insets.bottom + 16 }}
      >
        <View className="flex-row items-center justify-between mb-4">
          <Text className="text-lg font-bold text-white">Quick Add</Text>
          <TouchableOpacity onPress={onClose} className="p-1">
            <X size={20} color="#6B8CA8" />
          </TouchableOpacity>
        </View>
        <View className="flex-row flex-wrap gap-3">
          {QUICK_ACTIONS.map(a => (
            <TouchableOpacity
              key={a.route}
              onPress={() => go(a.route)}
              className="flex-row items-center gap-x-2 rounded-xl border border-white/10
                         bg-white/5 px-4 py-3 w-[47%]"
            >
              <Text className="text-xl">{a.emoji}</Text>
              <Text className="text-sm text-white font-medium">{a.label}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
    </View>
  )
}

// ─── Custom tab bar ────────────────────────────────────────────────────────────
const NAV_TABS = [
  { href: '/(app)/dashboard', icon: Home,      label: 'Home'    },
  { href: '/(app)/history',   icon: BarChart3, label: 'History' },
  { href: null,               icon: Plus,      label: 'Add'     },
  { href: '/(app)/coach',     icon: Sparkles,  label: 'Coach'   },
  { href: '/(app)/profile',   icon: User,      label: 'Profile' },
] as const

function BottomTabBar({ quickAddOpen, onToggleQuickAdd }: {
  quickAddOpen: boolean
  onToggleQuickAdd: () => void
}) {
  const router = useRouter()
  const pathname = usePathname()
  const insets = useSafeAreaInsets()

  return (
    <View
      className="bg-card border-t border-white/10"
      style={{ paddingBottom: Math.max(insets.bottom, 8) }}
    >
      <View className="flex-row items-end justify-around px-2 pt-2">
        {NAV_TABS.map((tab) => {
          const isCenter = tab.href === null
          const isActive = tab.href
            ? (pathname === tab.href || pathname.startsWith(tab.href + '/'))
            : quickAddOpen
          const Icon = tab.icon

          if (isCenter) {
            return (
              <TouchableOpacity
                key="add"
                onPress={onToggleQuickAdd}
                className="items-center"
              >
                <View
                  className="h-14 w-14 rounded-full items-center justify-center -mt-5"
                  style={{
                    backgroundColor: '#C9A227',
                    shadowColor: '#C9A227',
                    shadowRadius: 16,
                    shadowOpacity: 0.5,
                    elevation: 8,
                    transform: [{ scale: quickAddOpen ? 0.9 : 1 }],
                  }}
                >
                  {quickAddOpen
                    ? <X size={24} color="#0B1A2B" strokeWidth={3} />
                    : <Plus size={26} color="#0B1A2B" strokeWidth={3} />}
                </View>
                <Text className="mt-1 text-[10px] font-semibold text-gold">
                  {tab.label}
                </Text>
              </TouchableOpacity>
            )
          }

          return (
            <TouchableOpacity
              key={tab.href}
              onPress={() => router.push(tab.href as any)}
              className="items-center px-2.5 py-1.5 rounded-xl min-w-[60px]"
            >
              <Icon
                size={20}
                color={isActive ? '#C9A227' : '#6B8CA8'}
                strokeWidth={isActive ? 2.5 : 2}
              />
              <Text
                className={`text-[10px] mt-0.5 ${isActive ? 'text-gold font-semibold' : 'text-muted-fg'}`}
              >
                {tab.label}
              </Text>
            </TouchableOpacity>
          )
        })}
      </View>
    </View>
  )
}

// ─── Layout ────────────────────────────────────────────────────────────────────
export default function AppLayout() {
  const [quickAddOpen, setQuickAddOpen] = useState(false)
  const toggleQuickAdd = useCallback(() => setQuickAddOpen(v => !v), [])

  return (
    <View className="flex-1 bg-navy">
      <Tabs
        screenOptions={{ headerShown: false }}
        tabBar={() => (
          <BottomTabBar
            quickAddOpen={quickAddOpen}
            onToggleQuickAdd={toggleQuickAdd}
          />
        )}
      >
        <Tabs.Screen name="dashboard" />
        <Tabs.Screen name="history" />
        <Tabs.Screen name="reports" />
        <Tabs.Screen name="coach" />
        <Tabs.Screen name="profile" />
        <Tabs.Screen name="tasks" />
        <Tabs.Screen name="habits" />
        <Tabs.Screen name="health" />
        <Tabs.Screen name="goals" />
        <Tabs.Screen name="deen" />
        <Tabs.Screen name="challenges" />
        <Tabs.Screen name="checkin" />
        <Tabs.Screen name="dreams" />
      </Tabs>

      {quickAddOpen && (
        <QuickAddOverlay onClose={() => setQuickAddOpen(false)} />
      )}
    </View>
  )
}
