import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity,
  ActivityIndicator, RefreshControl, Dimensions,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { useRouter } from 'expo-router'
import { FileText } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { todayString, formatDateShort } from '@/lib/utils'

type Period = '7d' | '30d' | '90d'

interface DayStat {
  date: string
  habits_done: number
  habits_total: number
  prayers_done: number
  tasks_done: number
  tasks_total: number
}

const { width: SCREEN_W } = Dimensions.get('window')
const BAR_AREA_W = SCREEN_W - 32 // padding 16px each side

function MiniBar({ value, max, color }: { value: number; max: number; color: string }) {
  const pct = max > 0 ? value / max : 0
  const BAR_H = 80
  return (
    <View style={{ height: BAR_H, justifyContent: 'flex-end' }}>
      <View
        style={{
          height: Math.max(2, pct * BAR_H),
          backgroundColor: color,
          borderRadius: 3,
          opacity: pct > 0 ? 1 : 0.2,
        }}
      />
    </View>
  )
}

function StatCard({ label, value, sub, color }: { label: string; value: string | number; sub?: string; color?: string }) {
  return (
    <View className="flex-1 rounded-2xl border border-white/10 bg-white/5 p-3 items-center">
      <Text className="text-2xl font-bold" style={{ color: color ?? '#fff' }}>{value}</Text>
      <Text className="text-xs text-muted-fg mt-0.5 text-center">{label}</Text>
      {sub && <Text className="text-[10px] text-muted-fg/60 mt-0.5">{sub}</Text>}
    </View>
  )
}

export default function HistoryScreen() {
  const insets = useSafeAreaInsets()
  const router = useRouter()
  const [period, setPeriod] = useState<Period>('30d')
  const [stats, setStats] = useState<DayStat[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const days = period === '7d' ? 7 : period === '30d' ? 30 : 90
    const from = new Date()
    from.setDate(from.getDate() - (days - 1))
    const fromStr = from.toISOString().split('T')[0]

    const [
      { data: habLogs },
      { data: habs },
      { data: prayers },
      { data: tasks },
    ] = await Promise.all([
      supabase.from('habit_logs').select('date,completed').eq('user_id', user.id).gte('date', fromStr),
      supabase.from('habits').select('id').eq('user_id', user.id).eq('is_active', true),
      supabase.from('prayer_logs').select('date,fajr,dhuhr,asr,maghrib,isha').eq('user_id', user.id).gte('date', fromStr),
      supabase.from('tasks').select('date:period_date,status').eq('user_id', user.id).eq('type', 'daily').gte('period_date', fromStr),
    ])

    const habitsTotal = (habs ?? []).length
    const today = todayString()

    // Build day-by-day stats
    const dayStats: DayStat[] = []
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date()
      d.setDate(d.getDate() - i)
      const dateStr = d.toISOString().split('T')[0]

      const dayLogs = (habLogs ?? []).filter(l => l.date === dateStr)
      const habsDone = dayLogs.filter(l => l.completed).length

      const prayerRow = (prayers ?? []).find(p => p.date === dateStr)
      const prayersDone = prayerRow
        ? (['fajr','dhuhr','asr','maghrib','isha'] as const).filter(p => (prayerRow as any)[p]).length
        : 0

      const dayTasks = (tasks ?? []).filter(t => t.date === dateStr)
      const tasksDone = dayTasks.filter(t => t.status === 'completed').length

      dayStats.push({
        date: dateStr,
        habits_done: habsDone,
        habits_total: habitsTotal,
        prayers_done: prayersDone,
        tasks_done: tasksDone,
        tasks_total: dayTasks.length,
      })
    }

    setStats(dayStats)
  }, [period])

  useEffect(() => { load().finally(() => setLoading(false)) }, [load])
  const onRefresh = useCallback(async () => { setRefreshing(true); await load(); setRefreshing(false) }, [load])

  // Aggregate stats
  const totalDays = stats.length
  const avgPrayers = totalDays > 0
    ? (stats.reduce((s, d) => s + d.prayers_done, 0) / totalDays).toFixed(1)
    : '0'
  const habitDays = stats.filter(d => d.habits_total > 0 && d.habits_done / d.habits_total >= 0.8).length
  const taskCompletionPct = (() => {
    const t = stats.reduce((s, d) => s + d.tasks_total, 0)
    const d = stats.reduce((s, d) => s + d.tasks_done, 0)
    return t > 0 ? Math.round((d / t) * 100) : 0
  })()

  const maxHabits = Math.max(...stats.map(d => d.habits_total), 1)
  const barW = Math.floor(BAR_AREA_W / Math.min(stats.length, period === '7d' ? 7 : 30))

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <Text className="text-xl font-bold text-white">History</Text>
        <TouchableOpacity
          onPress={() => router.push('/(app)/reports')}
          className="flex-row items-center gap-x-1.5 rounded-xl border border-white/10 bg-white/5 px-3 py-1.5"
        >
          <FileText size={14} color="#C9A227" />
          <Text className="text-xs font-semibold text-gold">Reports</Text>
        </TouchableOpacity>
      </View>

      {/* Period tabs */}
      <View className="flex-row mx-4 rounded-xl border border-white/10 bg-white/5 p-1 mb-4">
        {(['7d', '30d', '90d'] as Period[]).map(p => (
          <TouchableOpacity
            key={p}
            onPress={() => setPeriod(p)}
            className={`flex-1 rounded-lg py-2 items-center ${period === p ? 'bg-teal' : ''}`}
          >
            <Text className={`text-sm font-semibold ${period === p ? 'text-white' : 'text-muted-fg'}`}>{p}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView
        className="flex-1 px-4"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
      >
        {/* Summary cards */}
        <View className="flex-row gap-x-3 mb-4">
          <StatCard label="Avg prayers/day" value={avgPrayers} color="#34d399" />
          <StatCard label="Habit streak days" value={habitDays} sub="≥80% done" color="#C9A227" />
          <StatCard label="Task completion" value={`${taskCompletionPct}%`} color="#60a5fa" />
        </View>

        {/* Habits bar chart */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <Text className="text-sm font-semibold text-white mb-3">Habits completed</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View className="flex-row items-end gap-x-0.5">
              {stats.slice(-Math.min(stats.length, period === '90d' ? 90 : 30)).map(d => (
                <View key={d.date} style={{ width: period === '90d' ? 6 : barW }}>
                  <MiniBar value={d.habits_done} max={maxHabits} color="#34d399" />
                </View>
              ))}
            </View>
          </ScrollView>
          <View className="flex-row justify-between mt-2">
            {stats.length > 0 && (
              <>
                <Text className="text-[10px] text-muted-fg">{formatDateShort(stats[0].date)}</Text>
                <Text className="text-[10px] text-muted-fg">{formatDateShort(stats[stats.length - 1].date)}</Text>
              </>
            )}
          </View>
        </View>

        {/* Prayers bar chart */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <Text className="text-sm font-semibold text-white mb-3">Prayers / day</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View className="flex-row items-end gap-x-0.5">
              {stats.slice(-Math.min(stats.length, period === '90d' ? 90 : 30)).map(d => (
                <View key={d.date} style={{ width: period === '90d' ? 6 : barW }}>
                  <MiniBar value={d.prayers_done} max={5} color="#C9A227" />
                </View>
              ))}
            </View>
          </ScrollView>
        </View>

        {/* Day detail (last 7 rows) */}
        {period === '7d' && (
          <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-8">
            <Text className="text-sm font-semibold text-white mb-3">Daily breakdown</Text>
            <View className="gap-y-3">
              {stats.slice(-7).reverse().map(d => (
                <View key={d.date} className="flex-row items-center gap-x-3">
                  <Text className="text-xs text-muted-fg w-16">{formatDateShort(d.date)}</Text>
                  <View className="flex-1 gap-y-1">
                    <View className="flex-row items-center gap-x-1.5">
                      <Text className="text-[10px] text-muted-fg w-14">Prayers</Text>
                      <View className="flex-1 h-1.5 rounded-full bg-white/10">
                        <View className="h-full rounded-full bg-emerald-400" style={{ width: `${(d.prayers_done / 5) * 100}%` }} />
                      </View>
                      <Text className="text-[10px] text-muted-fg">{d.prayers_done}/5</Text>
                    </View>
                    {d.habits_total > 0 && (
                      <View className="flex-row items-center gap-x-1.5">
                        <Text className="text-[10px] text-muted-fg w-14">Habits</Text>
                        <View className="flex-1 h-1.5 rounded-full bg-white/10">
                          <View className="h-full rounded-full bg-gold" style={{ width: `${(d.habits_done / d.habits_total) * 100}%` }} />
                        </View>
                        <Text className="text-[10px] text-muted-fg">{d.habits_done}/{d.habits_total}</Text>
                      </View>
                    )}
                  </View>
                </View>
              ))}
            </View>
          </View>
        )}

        <View className="h-6" />
      </ScrollView>
    </View>
  )
}
