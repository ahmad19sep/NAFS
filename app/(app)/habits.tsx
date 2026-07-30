import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput, FlatList,
  Modal, KeyboardAvoidingView, Platform, RefreshControl, ActivityIndicator, Alert,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Plus, X, Trash2, Flame } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { todayString, getStreakEmoji } from '@/lib/utils'
import type { HabitRow, HabitLogRow } from '@/types/database'

type HabitType = 'boolean' | 'count' | 'duration'

const CATEGORIES = [
  { id: 'deen',    emoji: '🕌', label: 'Deen'    },
  { id: 'health',  emoji: '💪', label: 'Health'  },
  { id: 'mind',    emoji: '🧠', label: 'Mind'    },
  { id: 'growth',  emoji: '🌱', label: 'Growth'  },
  { id: 'custom',  emoji: '⭐', label: 'Custom'  },
]

const EMOJI_PRESETS = ['⭐','🔥','💪','🧠','📚','🕌','🤲','🏃','💧','😴','📖','🎯','🌅','🚿','📝']

interface AddHabitModalProps {
  visible: boolean
  onClose: () => void
  onSave: () => void
}

function AddHabitModal({ visible, onClose, onSave }: AddHabitModalProps) {
  const [name, setName] = useState('')
  const [emoji, setEmoji] = useState('⭐')
  const [type, setType] = useState<HabitType>('boolean')
  const [target, setTarget] = useState('1')
  const [unit, setUnit] = useState('')
  const [category, setCategory] = useState('custom')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (visible) { setName(''); setEmoji('⭐'); setType('boolean'); setTarget('1'); setUnit(''); setCategory('custom') }
  }, [visible])

  async function handleSave() {
    if (!name.trim()) return
    setSaving(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return
      await supabase.from('habits').insert({
        user_id: user.id,
        name: name.trim(),
        emoji,
        type,
        target_value: parseFloat(target) || 1,
        unit: unit.trim(),
        category,
        sort_order: Date.now(),
      })
      onSave()
      onClose()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal visible={visible} transparent animationType="slide">
      <KeyboardAvoidingView className="flex-1" behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <View className="flex-1 justify-end" style={{ backgroundColor: 'rgba(11,26,43,0.85)' }}>
          <TouchableOpacity className="flex-1" onPress={onClose} />
          <ScrollView className="bg-card rounded-t-3xl border-t border-white/10">
            <View className="px-5 pt-4 pb-8">
              <View className="flex-row items-center justify-between mb-5">
                <Text className="text-lg font-bold text-white">New Habit</Text>
                <TouchableOpacity onPress={onClose}><X size={20} color="#6B8CA8" /></TouchableOpacity>
              </View>

              {/* Emoji picker */}
              <View className="mb-4">
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Icon</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-x-2">
                    {EMOJI_PRESETS.map(e => (
                      <TouchableOpacity
                        key={e}
                        onPress={() => setEmoji(e)}
                        className={`h-10 w-10 rounded-xl items-center justify-center border ${
                          emoji === e ? 'border-teal bg-teal/20' : 'border-white/10 bg-white/5'
                        }`}
                      >
                        <Text className="text-xl">{e}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </ScrollView>
              </View>

              {/* Name */}
              <View className="mb-4">
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Name</Text>
                <TextInput
                  className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                  placeholder="e.g. Read Quran, Exercise"
                  placeholderTextColor="#6B8CA8"
                  value={name}
                  onChangeText={setName}
                  autoFocus
                />
              </View>

              {/* Category */}
              <View className="mb-4">
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Category</Text>
                <View className="flex-row flex-wrap gap-2">
                  {CATEGORIES.map(c => (
                    <TouchableOpacity
                      key={c.id}
                      onPress={() => setCategory(c.id)}
                      className={`flex-row items-center gap-x-1.5 rounded-xl border px-3 py-1.5 ${
                        category === c.id ? 'border-teal bg-teal/20' : 'border-white/10 bg-white/5'
                      }`}
                    >
                      <Text>{c.emoji}</Text>
                      <Text className={`text-xs font-medium ${category === c.id ? 'text-teal-light' : 'text-muted-fg'}`}>
                        {c.label}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>

              {/* Type */}
              <View className="mb-4">
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Type</Text>
                <View className="flex-row gap-x-2">
                  {([
                    { id: 'boolean' as HabitType, label: 'Yes/No' },
                    { id: 'count' as HabitType, label: 'Count' },
                    { id: 'duration' as HabitType, label: 'Duration' },
                  ]).map(({ id, label }) => (
                    <TouchableOpacity
                      key={id}
                      onPress={() => setType(id)}
                      className={`flex-1 rounded-xl border py-2.5 items-center ${
                        type === id ? 'border-teal bg-teal/20' : 'border-white/10 bg-white/5'
                      }`}
                    >
                      <Text className={`text-xs font-semibold ${type === id ? 'text-teal-light' : 'text-muted-fg'}`}>
                        {label}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>

              {/* Target */}
              {type !== 'boolean' && (
                <View className="flex-row gap-x-3 mb-4">
                  <View className="flex-1">
                    <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Target</Text>
                    <TextInput
                      className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                      value={target}
                      onChangeText={setTarget}
                      keyboardType="numeric"
                    />
                  </View>
                  <View className="flex-1">
                    <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Unit</Text>
                    <TextInput
                      className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                      placeholder={type === 'duration' ? 'min' : 'times'}
                      placeholderTextColor="#6B8CA8"
                      value={unit}
                      onChangeText={setUnit}
                    />
                  </View>
                </View>
              )}

              <TouchableOpacity
                onPress={handleSave}
                disabled={saving || !name.trim()}
                className="rounded-xl bg-teal py-3.5 items-center"
                style={{ opacity: saving || !name.trim() ? 0.5 : 1 }}
              >
                {saving
                  ? <ActivityIndicator color="#fff" />
                  : <Text className="text-white font-semibold">Save Habit</Text>
                }
              </TouchableOpacity>
            </View>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  )
}

function HabitCard({
  habit,
  log,
  onToggle,
  onDelete,
}: {
  habit: HabitRow
  log: HabitLogRow | undefined
  onToggle: () => void
  onDelete: () => void
}) {
  const done = log?.completed ?? false

  return (
    <View className={`rounded-2xl border p-4 mb-3 ${
      done ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
    }`}>
      <View className="flex-row items-center gap-x-3">
        {/* Check button */}
        <TouchableOpacity
          onPress={onToggle}
          className={`h-10 w-10 rounded-full border-2 items-center justify-center ${
            done ? 'bg-emerald-500 border-emerald-500' : 'border-white/30'
          }`}
        >
          {done
            ? <Text className="text-white font-bold">✓</Text>
            : <Text className="text-2xl">{habit.emoji}</Text>}
        </TouchableOpacity>

        {/* Info */}
        <View className="flex-1">
          <Text className={`text-sm font-semibold ${done ? 'text-emerald-400 line-through' : 'text-white'}`}>
            {habit.name}
          </Text>
          <View className="flex-row items-center gap-x-2 mt-0.5">
            <Text className="text-xs text-muted-fg capitalize">{habit.category}</Text>
            {habit.type !== 'boolean' && (
              <>
                <Text className="text-muted-fg text-xs">·</Text>
                <Text className="text-xs text-muted-fg">{habit.target_value} {habit.unit}</Text>
              </>
            )}
          </View>
        </View>

        {/* Streak */}
        <View className="items-center">
          <Text className="text-base">{getStreakEmoji(habit.current_streak)}</Text>
          <Text className="text-xs font-bold text-gold">{habit.current_streak}d</Text>
        </View>

        {/* Delete */}
        <TouchableOpacity onPress={onDelete} className="ml-2 p-1">
          <Trash2 size={16} color="#6B8CA8" />
        </TouchableOpacity>
      </View>
    </View>
  )
}

export default function HabitsScreen() {
  const insets = useSafeAreaInsets()
  const today = todayString()
  const [habits, setHabits] = useState<HabitRow[]>([])
  const [logs, setLogs] = useState<HabitLogRow[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [showAdd, setShowAdd] = useState(false)

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const [{ data: habs }, { data: lgs }] = await Promise.all([
      supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true).order('sort_order'),
      supabase.from('habit_logs').select('*').eq('user_id', user.id).eq('date', today),
    ])
    setHabits(habs ?? [])
    setLogs(lgs ?? [])
  }, [today])

  useEffect(() => {
    load().finally(() => setLoading(false))
  }, [load])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await load()
    setRefreshing(false)
  }, [load])

  async function toggleHabit(habit: HabitRow) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const existing = logs.find(l => l.habit_id === habit.id)
    if (existing) {
      const newCompleted = !existing.completed
      await supabase.from('habit_logs')
        .update({ completed: newCompleted, value: newCompleted ? habit.target_value : 0 })
        .eq('id', existing.id)
      setLogs(prev => prev.map(l => l.id === existing.id ? { ...l, completed: newCompleted } : l))
      // Update streak
      const streak = newCompleted ? habit.current_streak + 1 : Math.max(0, habit.current_streak - 1)
      await supabase.from('habits').update({
        current_streak: streak,
        longest_streak: Math.max(habit.longest_streak, streak),
      }).eq('id', habit.id)
      setHabits(prev => prev.map(h => h.id === habit.id
        ? { ...h, current_streak: streak, longest_streak: Math.max(h.longest_streak, streak) }
        : h))
    } else {
      const { data } = await supabase.from('habit_logs').insert({
        user_id: user.id,
        habit_id: habit.id,
        date: today,
        value: habit.target_value,
        completed: true,
      }).select().single()
      if (data) {
        setLogs(prev => [...prev, data])
        const streak = habit.current_streak + 1
        await supabase.from('habits').update({
          current_streak: streak,
          longest_streak: Math.max(habit.longest_streak, streak),
        }).eq('id', habit.id)
        setHabits(prev => prev.map(h => h.id === habit.id
          ? { ...h, current_streak: streak, longest_streak: Math.max(h.longest_streak, streak) }
          : h))
      }
    }
  }

  async function deleteHabit(id: string) {
    Alert.alert('Delete habit?', 'This will also delete all logs.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive',
        onPress: async () => {
          await supabase.from('habits').delete().eq('id', id)
          setHabits(prev => prev.filter(h => h.id !== id))
          setLogs(prev => prev.filter(l => l.habit_id !== id))
        },
      },
    ])
  }

  const completed = logs.filter(l => l.completed).length
  const total = habits.length

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <View>
          <Text className="text-xl font-bold text-white">Habits</Text>
          <Text className="text-xs text-muted-fg mt-0.5">
            {completed}/{total} done today
          </Text>
        </View>
        <TouchableOpacity
          onPress={() => setShowAdd(true)}
          className="h-9 w-9 rounded-full bg-teal items-center justify-center"
        >
          <Plus size={20} color="#fff" />
        </TouchableOpacity>
      </View>

      {/* Progress bar */}
      {total > 0 && (
        <View className="mx-4 mb-4 h-2 rounded-full bg-white/10">
          <View
            className="h-full rounded-full bg-emerald-400"
            style={{ width: `${Math.round((completed / total) * 100)}%` }}
          />
        </View>
      )}

      <ScrollView
        className="flex-1 px-4"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
      >
        {habits.length === 0 ? (
          <View className="items-center py-16">
            <Text className="text-4xl mb-3">🌱</Text>
            <Text className="text-base font-semibold text-white">No habits yet</Text>
            <Text className="text-sm text-muted-fg mt-1">Tap + to build your first habit.</Text>
          </View>
        ) : (
          habits.map(habit => (
            <HabitCard
              key={habit.id}
              habit={habit}
              log={logs.find(l => l.habit_id === habit.id)}
              onToggle={() => toggleHabit(habit)}
              onDelete={() => deleteHabit(habit.id)}
            />
          ))
        )}
        <View className="h-6" />
      </ScrollView>

      <AddHabitModal
        visible={showAdd}
        onClose={() => setShowAdd(false)}
        onSave={load}
      />
    </View>
  )
}
