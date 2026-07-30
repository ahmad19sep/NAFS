import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  ActivityIndicator, RefreshControl, Alert, Switch,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Save } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { todayString } from '@/lib/utils'
import type { HealthLogRow } from '@/types/database'

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
  const [sleepHours, setSleepHours] = useState('')
  const [sleepTime, setSleepTime] = useState('')
  const [wakeTime, setWakeTime] = useState('')
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
    setSleepHours(row.sleep_hours?.toString() ?? '')
    setSleepTime(row.sleep_time ?? '')
    setWakeTime(row.wake_time ?? '')
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
      const payload = {
        user_id: user.id,
        date: today,
        weight_kg: weight ? parseFloat(weight) : null,
        steps: steps ? parseInt(steps) : null,
        exercise_done: exerciseDone,
        exercise_minutes: exerciseMins ? parseInt(exerciseMins) : null,
        sleep_hours: sleepHours ? parseFloat(sleepHours) : null,
        sleep_time: sleepTime || null,
        wake_time: wakeTime || null,
        water_glasses: waterGlasses ? parseInt(waterGlasses) : 0,
        mood: mood || null,
        notes: notes || null,
        updated_at: new Date().toISOString(),
      }
      if (log) {
        const { data } = await supabase.from('health_logs').update(payload).eq('id', log.id).select().single()
        if (data) setLog(data as HealthLogRow)
      } else {
        const { data } = await supabase.from('health_logs').insert(payload).select().single()
        if (data) setLog(data as HealthLogRow)
      }
      Alert.alert('Saved', 'Health log saved.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

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
          <Text className="text-sm font-semibold text-white mb-3">😴 Sleep</Text>
          <Field label="Hours slept" value={sleepHours} onChange={setSleepHours} unit="hrs" />
          <Field label="Bedtime (HH:MM)" value={sleepTime} onChange={setSleepTime} unit="time" keyboardType="default" placeholder="22:30" />
          <Field label="Wake time (HH:MM)" value={wakeTime} onChange={setWakeTime} unit="time" keyboardType="default" placeholder="06:00" />
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
