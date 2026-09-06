import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  ActivityIndicator, RefreshControl, Alert, Switch,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Save, Plus, Trash2 } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { todayString, sleepSessionMinutes, totalSleepMinutes, formatDuration } from '@/lib/utils'
import type { HealthLogRow, SleepSession } from '@/types/database'

function Field({
  label, value, onChange, unit, keyboardType = 'numeric', placeholder = '—',
}: {
  label: string; value: string; onChange: (v: string) => void
  unit?: string; keyboardType?: any; placeholder?: string
}) {
  return (
    <View className="mb-4">
      <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">{label}</Text>
      <View className="flex-row items-center gap-x-2">
        <TextInput
          className="flex-1 rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
          value={value}
          onChangeText={onChange}
          keyboardType={keyboardType}
          placeholderTextColor="#6B8CA8"
          placeholder={placeholder}
        />
        {unit && <Text className="text-sm text-muted-fg w-14">{unit}</Text>}
      </View>
    </View>
  )
}

function newSessionId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

// TIME columns come back as "22:30:00"; the inputs work in "HH:MM".
function trimSeconds(time: string): string {
  return time.slice(0, 5)
}

function sessionsFromRow(row: HealthLogRow): SleepSession[] {
  const stored = row.sleep_sessions
  if (Array.isArray(stored) && stored.length) {
    return stored.map(s => ({ id: s.id, start: trimSeconds(s.start), end: trimSeconds(s.end) }))
  }
  if (row.sleep_time && row.wake_time) {
    return [{ id: newSessionId(), start: trimSeconds(row.sleep_time), end: trimSeconds(row.wake_time) }]
  }
  return []
}

function toTimeInput(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 4)
  return digits.length <= 2 ? digits : `${digits.slice(0, 2)}:${digits.slice(2)}`
}

function TimeField({
  label, value, onChange,
}: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <View className="flex-1">
      <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">{label}</Text>
      <TextInput
        className="rounded-xl border border-white/10 bg-white/5 px-3 py-3 text-white"
        value={value}
        onChangeText={v => onChange(toTimeInput(v))}
        keyboardType="numeric"
        placeholderTextColor="#6B8CA8"
        placeholder="--:--"
        maxLength={5}
      />
    </View>
  )
}

function SleepSessionCard({
  session, index, onChange, onRemove,
}: {
  session: SleepSession; index: number
  onChange: (patch: Partial<SleepSession>) => void
  onRemove: () => void
}) {
  const mins = sleepSessionMinutes(session.start, session.end)
  return (
    <View className="rounded-xl border border-white/10 bg-white/5 p-3 mb-3">
      <View className="flex-row items-center justify-between mb-2">
        <Text className="text-xs text-muted-fg uppercase tracking-wider">
          {index === 0 ? 'Sleep' : `Sleep ${index + 1}`}
        </Text>
        <TouchableOpacity onPress={onRemove} hitSlop={8}>
          <Trash2 size={16} color="#6B8CA8" />
        </TouchableOpacity>
      </View>
      <View className="flex-row gap-x-3">
        <TimeField label="Slept at" value={session.start} onChange={v => onChange({ start: v })} />
        <TimeField label="Woke at" value={session.end} onChange={v => onChange({ end: v })} />
      </View>
      <Text className="text-sm font-semibold text-gold mt-2">
        {mins == null ? 'Enter both times' : formatDuration(mins)}
      </Text>
    </View>
  )
}

function BmiCard({ weight, height }: { weight: number | null; height: number | null }) {
  if (!weight || !height) return null
  const bmi = weight / ((height / 100) ** 2)
  const { label, color } =
    bmi < 18.5 ? { label: 'Underweight', color: '#60a5fa' } :
    bmi < 25   ? { label: 'Normal', color: '#34d399' } :
    bmi < 30   ? { label: 'Overweight', color: '#fbbf24' } :
                 { label: 'Obese', color: '#f87171' }
  return (
    <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4 flex-row items-center justify-between">
      <View>
        <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1">BMI</Text>
        <Text className="text-3xl font-bold" style={{ color }}>{bmi.toFixed(1)}</Text>
      </View>
      <View className="items-end">
        <Text className="text-sm font-semibold" style={{ color }}>{label}</Text>
        <Text className="text-xs text-muted-fg mt-0.5">{weight} kg · {height} cm</Text>
      </View>
    </View>
  )
}

export default function HealthScreen() {
  const insets = useSafeAreaInsets()
  const today = todayString()
  const [log, setLog] = useState<HealthLogRow | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  // body
  const [weight, setWeight] = useState('')
  const [steps, setSteps] = useState('')
  // exercise
  const [exerciseDone, setExerciseDone] = useState(false)
  const [exerciseMins, setExerciseMins] = useState('')
  // sleep
  const [sleepSessions, setSleepSessions] = useState<SleepSession[]>([])
  // wellness
  const [waterGlasses, setWaterGlasses] = useState('')
  const [mood, setMood] = useState(0)
  const [notes, setNotes] = useState('')

  function populate(row: HealthLogRow | null) {
    if (!row) return
    setWeight(row.weight_kg?.toString() ?? '')
    setSteps(row.steps?.toString() ?? '')
    setExerciseDone(row.exercise_done ?? false)
    setExerciseMins(row.exercise_minutes?.toString() ?? '')
    setSleepSessions(sessionsFromRow(row))
    setWaterGlasses(row.water_glasses?.toString() ?? '')
    setMood(row.mood ?? 0)
    setNotes(row.notes ?? '')
  }

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data } = await supabase.from('health_logs').select('*').eq('user_id', user.id).eq('date', today).maybeSingle()
    setLog(data as HealthLogRow ?? null)
    populate(data as HealthLogRow ?? null)
  }, [today])

  useEffect(() => { load().finally(() => setLoading(false)) }, [load])
  const onRefresh = useCallback(async () => { setRefreshing(true); await load(); setRefreshing(false) }, [load])

  async function save() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    setSaving(true)
    try {
      const sessions = sleepSessions.filter(s => sleepSessionMinutes(s.start, s.end) != null)
      const sleptMins = totalSleepMinutes(sessions)
      const payload = {
        user_id: user.id,
        date: today,
        weight_kg: weight ? parseFloat(weight) : null,
        steps: steps ? parseInt(steps) : null,
        exercise_done: exerciseDone,
        exercise_minutes: exerciseMins ? parseInt(exerciseMins) : null,
        sleep_sessions: sessions,
        sleep_hours: sessions.length ? Math.round(sleptMins / 6) / 10 : null,
        sleep_time: sessions[0]?.start ?? null,
        wake_time: sessions[sessions.length - 1]?.end ?? null,
        water_glasses: waterGlasses ? parseInt(waterGlasses) : 0,
        mood: mood || null,
        notes: notes || null,
        updated_at: new Date().toISOString(),
      }
      const { data, error } = log
        ? await supabase.from('health_logs').update(payload).eq('id', log.id).select().single()
        : await supabase.from('health_logs').insert(payload).select().single()
      if (error) {
        Alert.alert('Could not save', error.message)
        return
      }
      if (data) setLog(data as HealthLogRow)
      Alert.alert('Saved', 'Health log saved.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  const sleptMins = totalSleepMinutes(sleepSessions)
  const profileWeight = weight ? parseFloat(weight) : null
  // pull height from user profile — show BMI only if we have both
  // (height is on users table; for now show if weight entered with typical height)

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <Text className="text-xl font-bold text-white">Health</Text>
        <TouchableOpacity
          onPress={save}
          disabled={saving}
          className="flex-row items-center gap-x-1.5 rounded-xl bg-teal px-4 py-2"
          style={{ opacity: saving ? 0.5 : 1 }}
        >
          {saving ? <ActivityIndicator size="small" color="#fff" /> : <Save size={16} color="#fff" />}
          <Text className="text-white text-sm font-semibold">{saving ? 'Saving…' : 'Save'}</Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        className="flex-1 px-4"
        contentContainerStyle={{ paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
      >

        {/* Body */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <Text className="text-sm font-semibold text-white mb-3">💪 Body</Text>
          <Field label="Weight" value={weight} onChange={setWeight} unit="kg" />
          <Field label="Steps" value={steps} onChange={setSteps} unit="steps" />
        </View>

        {/* Exercise */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <View className="flex-row items-center justify-between mb-3">
            <Text className="text-sm font-semibold text-white">🏃 Exercise</Text>
            <View className="flex-row items-center gap-x-2">
              <Text className="text-xs text-muted-fg">{exerciseDone ? 'Done' : 'Not yet'}</Text>
              <Switch
                value={exerciseDone}
                onValueChange={setExerciseDone}
                trackColor={{ false: '#1E3448', true: '#0F4C5C' }}
                thumbColor={exerciseDone ? '#C9A227' : '#6B8CA8'}
              />
            </View>
          </View>
          {exerciseDone && (
            <Field label="Duration" value={exerciseMins} onChange={setExerciseMins} unit="min" />
          )}
        </View>

        {/* Sleep */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <View className="flex-row items-center justify-between mb-3">
            <Text className="text-sm font-semibold text-white">😴 Sleep</Text>
            {sleptMins > 0 && (
              <Text className="text-sm font-bold text-gold">{formatDuration(sleptMins)} total</Text>
            )}
          </View>

          {sleepSessions.map((session, i) => (
            <SleepSessionCard
              key={session.id}
              session={session}
              index={i}
              onChange={patch => setSleepSessions(prev =>
                prev.map(s => (s.id === session.id ? { ...s, ...patch } : s))
              )}
              onRemove={() => setSleepSessions(prev => prev.filter(s => s.id !== session.id))}
            />
          ))}

          <TouchableOpacity
            onPress={() => setSleepSessions(prev => [...prev, { id: newSessionId(), start: '', end: '' }])}
            className="flex-row items-center justify-center gap-x-1.5 rounded-xl border border-dashed border-white/20 py-3"
          >
            <Plus size={16} color="#6B8CA8" />
            <Text className="text-sm text-muted-fg">
              {sleepSessions.length ? 'Add nap or another sleep' : 'Add sleep'}
            </Text>
          </TouchableOpacity>

          {sleepSessions.length > 1 && (
            <Text className="text-xs text-muted-fg mt-2 text-center">
              {sleepSessions.length} periods logged today
            </Text>
          )}
        </View>

        {/* Water */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <Text className="text-sm font-semibold text-white mb-3">💧 Hydration</Text>
          <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Water glasses</Text>
          <View className="flex-row flex-wrap gap-2 mb-2">
            {[1,2,3,4,5,6,7,8].map(n => (
              <TouchableOpacity
                key={n}
                onPress={() => setWaterGlasses(n.toString())}
                className={`h-10 w-10 rounded-xl border items-center justify-center ${
                  parseInt(waterGlasses) >= n ? 'border-blue-400 bg-blue-400/20' : 'border-white/10 bg-white/5'
                }`}
              >
                <Text className="text-base">💧</Text>
              </TouchableOpacity>
            ))}
          </View>
          <Text className="text-sm text-white">{waterGlasses || '0'} glass{parseInt(waterGlasses) !== 1 ? 'es' : ''}</Text>
        </View>

        {/* Mood */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <Text className="text-sm font-semibold text-white mb-3">🧠 Mood (1–10)</Text>
          <View className="flex-row flex-wrap gap-2">
            {[1,2,3,4,5,6,7,8,9,10].map(n => {
              const active = mood === n
              const col = n <= 3 ? '#f87171' : n <= 6 ? '#fbbf24' : '#34d399'
              return (
                <TouchableOpacity
                  key={n}
                  onPress={() => setMood(n)}
                  className="h-10 w-10 rounded-xl border items-center justify-center"
                  style={{
                    borderColor: active ? col : 'rgba(255,255,255,0.1)',
                    backgroundColor: active ? col + '22' : 'rgba(255,255,255,0.03)',
                  }}
                >
                  <Text className="text-sm font-bold" style={{ color: active ? col : '#6B8CA8' }}>{n}</Text>
                </TouchableOpacity>
              )
            })}
          </View>
        </View>

        {/* Notes */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <Text className="text-sm font-semibold text-white mb-3">📝 Notes</Text>
          <TextInput
            className="text-white text-sm leading-5"
            style={{ minHeight: 80, textAlignVertical: 'top' }}
            placeholder="How do you feel today? Any symptoms, energy levels…"
            placeholderTextColor="#6B8CA8"
            value={notes}
            onChangeText={setNotes}
            multiline
          />
        </View>
      </ScrollView>
    </View>
  )
}
