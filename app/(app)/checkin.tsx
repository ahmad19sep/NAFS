import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  KeyboardAvoidingView, Platform, ActivityIndicator, Alert,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Plus, X, Check } from 'lucide-react-native'
import { useRouter } from 'expo-router'
import { supabase } from '@/lib/supabase'
import { ai } from '@/lib/api'
import { todayString } from '@/lib/utils'

interface CheckinTask { text: string; done: boolean }

export default function CheckinScreen() {
  const insets = useSafeAreaInsets()
  const router = useRouter()
  const today = todayString()
  const [tab, setTab] = useState<'morning' | 'evening'>(() =>
    new Date().getHours() >= 15 ? 'evening' : 'morning'
  )
  const [loading, setLoading] = useState(true)
  const [checkinId, setCheckinId] = useState<string | null>(null)

  // morning
  const [tasks, setTasks] = useState<CheckinTask[]>([])
  const [newTask, setNewTask] = useState('')
  const [savingMorning, setSavingMorning] = useState(false)

  // evening
  const [eveningText, setEveningText] = useState('')
  const [aiVerdict, setAiVerdict] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => { loadCheckin() }, [])

  async function loadCheckin() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data } = await supabase
      .from('daily_checkins').select('*').eq('user_id', user.id).eq('date', today).maybeSingle()
    if (data) {
      setCheckinId(data.id)
      setTasks((data.tasks as CheckinTask[]) ?? [])
      setEveningText(data.evening_text ?? '')
      setAiVerdict(data.ai_verdict ?? null)
    }
    setLoading(false)
  }

  async function persist(patch: Record<string, unknown>) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    if (checkinId) {
      await supabase.from('daily_checkins').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', checkinId)
    } else {
      const base = { user_id: user.id, date: today, tasks: [], evening_text: null, ai_verdict: null }
      const { data } = await supabase.from('daily_checkins').insert({ ...base, ...patch }).select().single()
      if (data) setCheckinId(data.id)
    }
  }

  function addTask() {
    if (!newTask.trim()) return
    const updated = [...tasks, { text: newTask.trim(), done: false }]
    setTasks(updated)
    setNewTask('')
    persist({ tasks: updated })
  }

  function toggleTask(i: number) {
    const updated = tasks.map((t, j) => j === i ? { ...t, done: !t.done } : t)
    setTasks(updated)
    persist({ tasks: updated })
  }

  function removeTask(i: number) {
    const updated = tasks.filter((_, j) => j !== i)
    setTasks(updated)
    persist({ tasks: updated })
  }

  async function saveMorning() {
    setSavingMorning(true)
    await persist({ tasks })
    setSavingMorning(false)
    Alert.alert('Saved', 'Morning plan saved.', [{ text: 'OK', onPress: () => router.back() }])
  }

  async function submitEvening() {
    if (!eveningText.trim()) return
    setSubmitting(true)
    try {
      let verdict: string | null = null
      try {
        const result = await ai.eveningVerdict({ eveningText, tasks, date: today })
        verdict = result.verdict
      } catch {
        verdict = "AI verdict unavailable — check your API connection."
      }
      setAiVerdict(verdict)
      await persist({ tasks, evening_text: eveningText, ai_verdict: verdict })
    } finally {
      setSubmitting(false)
    }
  }

  const doneTasks = tasks.filter(t => t.done).length

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-navy"
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={{ paddingTop: insets.top }}
    >
      {/* Header */}
      <View className="px-4 pt-4 pb-2">
        <Text className="text-xl font-bold text-white">Daily Check-in</Text>
        <Text className="text-xs text-muted-fg mt-0.5">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </Text>
      </View>

      {/* Tabs */}
      <View className="flex-row mx-4 rounded-xl border border-white/10 bg-white/5 p-1 mb-4">
        {(['morning', 'evening'] as const).map(t => (
          <TouchableOpacity
            key={t}
            onPress={() => setTab(t)}
            className={`flex-1 rounded-lg py-2.5 items-center ${tab === t ? 'bg-teal' : ''}`}
          >
            <Text className={`text-sm font-semibold ${tab === t ? 'text-white' : 'text-muted-fg'}`}>
              {t === 'morning' ? '☀️ Morning plan' : '🌙 Evening review'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* ─── Morning ─────────────────────────────────────────────────── */}
      {tab === 'morning' && (
        <ScrollView className="flex-1 px-4" contentContainerStyle={{ paddingBottom: 32 }} keyboardShouldPersistTaps="handled">
          <Text className="text-sm font-semibold text-white mb-1">What are your top tasks today?</Text>
          <Text className="text-xs text-muted-fg mb-4">Keep it to 3–5. You'll review them tonight.</Text>

          <View className="gap-y-2 mb-4">
            {tasks.map((task, i) => (
              <TouchableOpacity
                key={i}
                onPress={() => toggleTask(i)}
                className={`flex-row items-center gap-x-3 rounded-xl border px-4 py-3 ${
                  task.done ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
                }`}
              >
                <View className={`h-6 w-6 rounded-full border-2 items-center justify-center flex-shrink-0 ${
                  task.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30'
                }`}>
                  {task.done && <Check size={14} color="#fff" />}
                </View>
                <Text className={`flex-1 text-sm ${task.done ? 'text-muted-fg line-through' : 'text-white'}`}>
                  {task.text}
                </Text>
                <TouchableOpacity onPress={() => removeTask(i)} className="p-1">
                  <X size={16} color="#6B8CA8" />
                </TouchableOpacity>
              </TouchableOpacity>
            ))}
          </View>

          {tasks.length < 7 && (
            <View className="flex-row gap-x-2 mb-6">
              <TextInput
                className="flex-1 rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Add a task…"
                placeholderTextColor="#6B8CA8"
                value={newTask}
                onChangeText={setNewTask}
                onSubmitEditing={addTask}
                returnKeyType="done"
              />
              <TouchableOpacity
                onPress={addTask}
                disabled={!newTask.trim()}
                className="h-12 w-12 rounded-xl bg-teal items-center justify-center"
                style={{ opacity: newTask.trim() ? 1 : 0.4 }}
              >
                <Plus size={20} color="#fff" />
              </TouchableOpacity>
            </View>
          )}

          <TouchableOpacity
            onPress={saveMorning}
            disabled={savingMorning}
            className="rounded-xl bg-teal py-4 items-center"
            style={{ opacity: savingMorning ? 0.5 : 1 }}
          >
            {savingMorning
              ? <ActivityIndicator color="#fff" />
              : <Text className="text-white font-bold text-base">Save morning plan →</Text>
            }
          </TouchableOpacity>
        </ScrollView>
      )}

      {/* ─── Evening ─────────────────────────────────────────────────── */}
      {tab === 'evening' && (
        <ScrollView className="flex-1 px-4" contentContainerStyle={{ paddingBottom: 32 }} keyboardShouldPersistTaps="handled">
          {!aiVerdict ? (
            <>
              <Text className="text-sm font-semibold text-white mb-1">Tell me about your day</Text>
              <Text className="text-xs text-muted-fg mb-4">
                What did you do? What did you skip? How did you feel? AI will compare it to your goals.
              </Text>

              {/* Morning task review */}
              {tasks.length > 0 && (
                <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
                  <Text className="text-xs text-muted-fg uppercase tracking-wider mb-3">Morning tasks — did you complete them?</Text>
                  <View className="gap-y-2">
                    {tasks.map((task, i) => (
                      <TouchableOpacity
                        key={i}
                        onPress={() => toggleTask(i)}
                        className="flex-row items-center gap-x-3"
                      >
                        <View className={`h-5 w-5 rounded-full border-2 items-center justify-center flex-shrink-0 ${
                          task.done ? 'border-emerald-400 bg-emerald-400' : 'border-white/30'
                        }`}>
                          {task.done && <Text className="text-white text-xs">✓</Text>}
                        </View>
                        <Text className={`text-sm ${task.done ? 'text-muted-fg line-through' : 'text-white'}`}>
                          {task.text}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              )}

              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white mb-4"
                style={{ minHeight: 120, textAlignVertical: 'top' }}
                placeholder="Today I worked on… I missed… I felt… The hardest part was…"
                placeholderTextColor="#6B8CA8"
                value={eveningText}
                onChangeText={setEveningText}
                multiline
              />

              <TouchableOpacity
                onPress={submitEvening}
                disabled={!eveningText.trim() || submitting}
                className="rounded-xl bg-teal py-4 items-center"
                style={{ opacity: !eveningText.trim() || submitting ? 0.5 : 1 }}
              >
                {submitting
                  ? <ActivityIndicator color="#fff" />
                  : <Text className="text-white font-bold text-base">Get AI verdict →</Text>
                }
              </TouchableOpacity>
            </>
          ) : (
            /* Verdict received */
            <View className="gap-y-4">
              <View className="rounded-2xl border border-gold/30 bg-gold/5 p-5">
                <View className="flex-row items-center gap-x-2 mb-3">
                  <Text className="text-xl">⚖️</Text>
                  <Text className="font-bold text-gold">Today's Verdict</Text>
                </View>
                <Text className="text-sm text-white leading-6">{aiVerdict}</Text>
              </View>

              {tasks.length > 0 && (
                <View className="rounded-2xl border border-white/10 bg-white/5 p-4">
                  <Text className="text-xs text-muted-fg uppercase tracking-wider mb-3">Task completion</Text>
                  <View className="flex-row items-center gap-x-4">
                    <Text className="text-3xl font-bold text-gold">{doneTasks}/{tasks.length}</Text>
                    <View className="flex-1">
                      <View className="h-2 rounded-full bg-white/10">
                        <View
                          className="h-full rounded-full bg-gold"
                          style={{ width: `${tasks.length ? (doneTasks / tasks.length) * 100 : 0}%` }}
                        />
                      </View>
                      <Text className="text-xs text-muted-fg mt-1">tasks completed</Text>
                    </View>
                  </View>
                </View>
              )}

              <TouchableOpacity onPress={() => setAiVerdict(null)} className="rounded-xl border border-white/10 py-3 items-center">
                <Text className="text-sm text-muted-fg">Edit my notes</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => router.push('/(app)/dashboard')} className="rounded-xl bg-teal py-3 items-center">
                <Text className="text-white font-semibold">Back to home</Text>
              </TouchableOpacity>
            </View>
          )}
        </ScrollView>
      )}
    </KeyboardAvoidingView>
  )
}
