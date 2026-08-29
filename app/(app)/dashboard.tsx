import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, RefreshControl, ActivityIndicator,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { useRouter } from 'expo-router'
import { supabase } from '@/lib/supabase'
import { todayString, getStreakEmoji, daysUntil } from '@/lib/utils'
import type { UserRow, HabitRow, HabitLogRow, PrayerLogRow, TaskRow, GoalRow, ChallengeRow } from '@/types/database'

const PRAYERS = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'] as const
type PrayerKey = typeof PRAYERS[number]

// val: 0=missed, 1=alone, 2=jamat
function PrayerDot({ val }: { val: number }) {
  const color = val === 2 ? '#C9A227' : val === 1 ? '#34d399' : undefined
  return (
    <View
      className={`h-3 w-3 rounded-full ${!color ? 'bg-white/20' : ''}`}
      style={color ? { backgroundColor: color, shadowColor: color, shadowRadius: 6, shadowOpacity: 0.7 } : undefined}
    />
  )
}

function IdentityRing({ score }: { score: number }) {
  const color = score >= 80 ? '#34d399' : score >= 60 ? '#fbbf24' : score >= 40 ? '#fb923c' : '#f87171'
  return (
    <View className="items-center justify-center">
      <View
        className="h-20 w-20 rounded-full items-center justify-center"
        style={{
          borderWidth: 4,
          borderColor: color,
          shadowColor: color,
          shadowRadius: 12,
          shadowOpacity: 0.5,
          elevation: 6,
        }}
      >
        <Text className="text-2xl font-bold text-white">{score}</Text>
        <Text className="text-[9px] text-muted-fg">score</Text>
      </View>
    </View>
  )
}

export default function DashboardScreen() {
  const insets = useSafeAreaInsets()
  const router = useRouter()
  const today = todayString()

  const [profile, setProfile] = useState<UserRow | null>(null)
  const [habits, setHabits] = useState<HabitRow[]>([])
  const [todayLogs, setTodayLogs] = useState<HabitLogRow[]>([])
  const [prayerLog, setPrayerLog] = useState<PrayerLogRow | null>(null)
  const [todayTasks, setTodayTasks] = useState<TaskRow[]>([])
  const [goals, setGoals] = useState<GoalRow[]>([])
  const [challenges, setChallenges] = useState<ChallengeRow[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const [
      { data: prof },
      { data: habs },
      { data: logs },
      { data: prayers },
      { data: tasks },
      { data: gls },
      { data: chals },
    ] = await Promise.all([
      supabase.from('users').select('*').eq('id', user.id).single(),
      supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true).order('sort_order'),
      supabase.from('habit_logs').select('*').eq('user_id', user.id).eq('date', today),
      supabase.from('prayer_logs').select('*').eq('user_id', user.id).eq('date', today).maybeSingle(),
      supabase.from('tasks').select('*').eq('user_id', user.id).eq('type', 'daily').eq('period_date', today),
      supabase.from('goals').select('*').eq('user_id', user.id).order('created_at', { ascending: false }),
      supabase.from('challenges').select('*').eq('user_id', user.id).eq('status', 'active'),
    ])

    setProfile(prof)
    setHabits(habs ?? [])
    setTodayLogs(logs ?? [])
    setPrayerLog(prayers ?? null)
    setTodayTasks(tasks ?? [])
    setGoals(gls ?? [])
    setChallenges(chals ?? [])
  }, [today])

  useEffect(() => {
    load().finally(() => setLoading(false))
  }, [load])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await load()
    setRefreshing(false)
  }, [load])

  // Derived stats
  const completedHabits = todayLogs.filter(l => l.completed).length
  const totalHabits = habits.length
  const habitPct = totalHabits > 0 ? Math.round((completedHabits / totalHabits) * 100) : 0

  const completedTasks = todayTasks.filter(t => t.status === 'completed').length
  const totalTasks = todayTasks.length

  // prayers are integers 0/1/2; count ≥1 as "prayed"
  const prayerCount = prayerLog
    ? PRAYERS.filter(p => ((prayerLog as any)[p] || 0) >= 1).length
    : 0
  const prayerScore = prayerLog
    ? PRAYERS.reduce((s, p) => s + ((prayerLog as any)[p] || 0), 0)
    : 0

  // Identity score: prayers 40% (out of 10) + habits 40% + tasks 20%
  const identityScore = Math.round(
    (prayerScore / 10) * 40 + habitPct * 0.4 + (totalTasks > 0 ? (completedTasks / totalTasks) * 20 : 10)
  )

  const greeting = (() => {
    const h = new Date().getHours()
    if (h < 12) return 'Good morning'
    if (h < 17) return 'Good afternoon'
    return 'Good evening'
  })()

  async function toggleHabit(habit: HabitRow) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const existing = todayLogs.find(l => l.habit_id === habit.id)
    if (existing) {
      const newCompleted = !existing.completed
      await supabase.from('habit_logs')
        .update({ completed: newCompleted, value: newCompleted ? habit.target_value : 0 })
        .eq('id', existing.id)
      setTodayLogs(prev => prev.map(l => l.id === existing.id ? { ...l, completed: newCompleted } : l))
    } else {
      const { data } = await supabase.from('habit_logs').insert({
        user_id: user.id,
        habit_id: habit.id,
        date: today,
        value: habit.target_value,
        completed: true,
      }).select().single()
      if (data) setTodayLogs(prev => [...prev, data])
    }
  }

  async function togglePrayer(prayer: PrayerKey) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const current = prayerLog ? ((prayerLog as any)[prayer] || 0) : 0
    const next = (current + 1) % 3  // 0→1→2→0
    const update = { [prayer]: next }
    if (prayerLog) {
      await supabase.from('prayer_logs').update(update).eq('id', prayerLog.id)
      setPrayerLog(prev => prev ? { ...prev, ...update } as PrayerLogRow : null)
    } else {
      const base = PRAYERS.reduce<Record<string, number>>((acc, p) => ({ ...acc, [p]: 0 }), {})
      const { data } = await supabase.from('prayer_logs').insert({
        user_id: user.id, date: today, extra_prayers: [], ...base, ...update,
      }).select().single()
      if (data) setPrayerLog(data as PrayerLogRow)
    }
  }

  if (loading) {
    return (
      <View className="flex-1 bg-navy items-center justify-center">
        <ActivityIndicator color="#C9A227" size="large" />
      </View>
    )
  }

  return (
    <ScrollView
      className="flex-1 bg-navy"
      contentContainerStyle={{ paddingTop: insets.top + 16, paddingBottom: 24 }}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
    >
      <View className="px-4">
        {/* Header */}
        <View className="flex-row items-center justify-between mb-6">
          <View>
            <Text className="text-sm text-muted-fg">{greeting}</Text>
            <Text className="text-xl font-bold text-white">{profile?.name ?? 'Welcome'} 👋</Text>
            <Text className="text-xs text-muted-fg mt-0.5">
              {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
            </Text>
          </View>
          <IdentityRing score={identityScore} />
        </View>

        {/* Prayers */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <View className="flex-row items-center justify-between mb-3">
            <Text className="text-sm font-semibold text-white">Prayers today</Text>
            <Text className="text-sm font-bold text-gold">{prayerScore}/10</Text>
          </View>
          <View className="flex-row justify-between">
            {PRAYERS.map(p => (
              <TouchableOpacity key={p} onPress={() => togglePrayer(p)} className="items-center gap-y-1.5">
                <PrayerDot val={prayerLog ? ((prayerLog as any)[p] || 0) : 0} />
                <Text className="text-[10px] text-muted-fg capitalize">{p}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Today's Habits */}
        {habits.length > 0 && (
          <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
            <View className="flex-row items-center justify-between mb-3">
              <Text className="text-sm font-semibold text-white">Habits</Text>
              <Text className="text-sm font-bold text-gold">{completedHabits}/{totalHabits}</Text>
            </View>
            <View className="gap-y-2">
              {habits.slice(0, 5).map(habit => {
                const log = todayLogs.find(l => l.habit_id === habit.id)
                const done = log?.completed ?? false
                return (
                  <TouchableOpacity
                    key={habit.id}
                    onPress={() => toggleHabit(habit)}
                    className={`flex-row items-center gap-x-3 rounded-xl px-3 py-2.5 border ${
                      done ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
                    }`}
                  >
                    <Text className="text-base">{habit.emoji}</Text>
                    <Text className={`flex-1 text-sm font-medium ${done ? 'text-emerald-400 line-through' : 'text-white'}`}>
                      {habit.name}
                    </Text>
                    <View className={`h-5 w-5 rounded-full border items-center justify-center ${
                      done ? 'bg-emerald-500 border-emerald-500' : 'border-white/30'
                    }`}>
                      {done && <Text className="text-white text-xs font-bold">✓</Text>}
                    </View>
                  </TouchableOpacity>
                )
              })}
              {habits.length > 5 && (
                <TouchableOpacity onPress={() => router.push('/(app)/habits')}>
                  <Text className="text-xs text-muted-fg text-center py-1">
                    +{habits.length - 5} more → View all habits
                  </Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        )}

        {/* Today's Tasks */}
        {totalTasks > 0 && (
          <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
            <View className="flex-row items-center justify-between mb-3">
              <Text className="text-sm font-semibold text-white">Tasks today</Text>
              <Text className="text-sm font-bold text-gold">{completedTasks}/{totalTasks}</Text>
            </View>
            <View className="gap-y-2">
              {todayTasks.slice(0, 4).map(task => (
                <View key={task.id} className="flex-row items-center gap-x-2">
                  <View className={`h-2 w-2 rounded-full ${
                    task.status === 'completed' ? 'bg-emerald-400' :
                    task.priority === 'high' ? 'bg-red-400' :
                    task.priority === 'medium' ? 'bg-yellow-400' : 'bg-blue-400'
                  }`} />
                  <Text className={`flex-1 text-sm ${
                    task.status === 'completed' ? 'text-muted-fg line-through' : 'text-white'
                  }`}>
                    {task.title}
                  </Text>
                </View>
              ))}
              {totalTasks > 4 && (
                <TouchableOpacity onPress={() => router.push('/(app)/tasks')}>
                  <Text className="text-xs text-muted-fg text-center py-1">
                    +{totalTasks - 4} more → View all tasks
                  </Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        )}

        {/* Active Challenges */}
        {challenges.length > 0 && (
          <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
            <Text className="text-sm font-semibold text-white mb-3">Active Challenges</Text>
            <View className="gap-y-2">
              {challenges.map(c => {
                const daysPassed = Math.floor(
                  (Date.now() - new Date(c.start_date).getTime()) / 86400000
                )
                const pct = Math.min(100, Math.round((daysPassed / c.duration_days) * 100))
                return (
                  <TouchableOpacity
                    key={c.id}
                    onPress={() => router.push('/(app)/challenges')}
                    className="flex-row items-center gap-x-3"
                  >
                    <Text className="text-xl">{c.emoji}</Text>
                    <View className="flex-1">
                      <Text className="text-sm font-medium text-white">{c.title}</Text>
                      <View className="flex-row items-center gap-x-2 mt-1">
                        <View className="flex-1 h-1.5 rounded-full bg-white/10">
                          <View className="h-full rounded-full bg-teal" style={{ width: `${pct}%` }} />
                        </View>
                        <Text className="text-xs text-muted-fg">
                          {getStreakEmoji(c.current_streak)} {c.current_streak}d
                        </Text>
                      </View>
                    </View>
                  </TouchableOpacity>
                )
              })}
            </View>
          </View>
        )}

        {/* Goals */}
        {goals.length > 0 && (
          <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
            <Text className="text-sm font-semibold text-white mb-3">Goals</Text>
            <View className="gap-y-2">
              {goals.slice(0, 3).map(g => (
                <TouchableOpacity
                  key={g.id}
                  onPress={() => router.push('/(app)/goals')}
                  className="flex-row items-center gap-x-3"
                >
                  <Text className="text-xl">{g.emoji}</Text>
                  <View className="flex-1">
                    <Text className="text-sm text-white" numberOfLines={1}>{g.title}</Text>
                    <View className="flex-row items-center gap-x-2 mt-1">
                      <View className="flex-1 h-1.5 rounded-full bg-white/10">
                        <View
                          className="h-full rounded-full bg-gold"
                          style={{ width: `${g.progress_pct}%` }}
                        />
                      </View>
                      <Text className="text-xs text-muted-fg">{g.progress_pct}%</Text>
                    </View>
                    {g.deadline && (
                      <Text className="text-[10px] text-muted-fg mt-0.5">
                        {daysUntil(g.deadline) > 0 ? `${daysUntil(g.deadline)}d left` : 'Overdue'}
                      </Text>
                    )}
                  </View>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}

        {/* Quick nav shortcuts */}
        <View className="flex-row gap-x-2 mb-4">
          {[
            { label: 'Check-in', emoji: '☀️', route: '/(app)/checkin' },
            { label: 'Coach', emoji: '✨', route: '/(app)/coach' },
            { label: 'History', emoji: '📈', route: '/(app)/history' },
            { label: 'Reports', emoji: '📄', route: '/(app)/reports' },
            { label: 'Dreams', emoji: '🌟', route: '/(app)/dreams' },
          ].map(s => (
            <TouchableOpacity
              key={s.route}
              onPress={() => router.push(s.route as any)}
              className="flex-1 rounded-xl border border-white/10 bg-white/5 py-3 items-center gap-y-1"
            >
              <Text className="text-xl">{s.emoji}</Text>
              <Text className="text-xs text-muted-fg">{s.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Empty state */}
        {habits.length === 0 && todayTasks.length === 0 && (
          <View className="rounded-2xl border border-dashed border-white/20 p-8 items-center">
            <Text className="text-4xl mb-3">🌙</Text>
            <Text className="text-base font-semibold text-white text-center">Start your journey</Text>
            <Text className="text-sm text-muted-fg text-center mt-2">
              Add habits and tasks to track your daily growth.
            </Text>
            <TouchableOpacity
              onPress={() => router.push('/(app)/habits')}
              className="mt-4 rounded-xl bg-teal px-6 py-2.5"
            >
              <Text className="text-white font-semibold">Add Habits →</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>
    </ScrollView>
  )
}
