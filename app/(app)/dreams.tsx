import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  ActivityIndicator, Alert, RefreshControl,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Save } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { daysUntil } from '@/lib/utils'
import type { DreamRow } from '@/types/database'

export default function DreamsScreen() {
  const insets = useSafeAreaInsets()
  const [dream, setDream] = useState<DreamRow | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [editing, setEditing] = useState(false)

  const [statement, setStatement] = useState('')
  const [why, setWhy] = useState('')
  const [dreamDate, setDreamDate] = useState('')
  const [hoursRequired, setHoursRequired] = useState('')

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data } = await supabase
      .from('dreams').select('*').eq('user_id', user.id).order('created_at', { ascending: false }).limit(1).maybeSingle()
    const d = data as DreamRow | null
    setDream(d)
    if (d) {
      setStatement(d.statement)
      setWhy(d.why ?? '')
      setDreamDate(d.dream_date)
      setHoursRequired(d.total_hours_required?.toString() ?? '')
    }
  }, [])

  useEffect(() => { load().finally(() => setLoading(false)) }, [load])
  const onRefresh = useCallback(async () => { setRefreshing(true); await load(); setRefreshing(false) }, [load])

  async function save() {
    if (!statement.trim()) return
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    setSaving(true)
    try {
      const payload = {
        user_id: user.id,
        statement: statement.trim(),
        why: why.trim() || null,
        dream_date: dreamDate || new Date(Date.now() + 365 * 86400000).toISOString().split('T')[0],
        total_hours_required: parseFloat(hoursRequired) || 1000,
        updated_at: new Date().toISOString(),
      }
      if (dream) {
        await supabase.from('dreams').update(payload).eq('id', dream.id)
        setDream(prev => prev ? { ...prev, ...payload } : null)
      } else {
        const { data } = await supabase.from('dreams').insert(payload).select().single()
        if (data) setDream(data as DreamRow)
      }
      setEditing(false)
      Alert.alert('Saved', 'Your dream statement has been saved.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <Text className="text-xl font-bold text-white">Dream</Text>
        {dream && !editing && (
          <TouchableOpacity onPress={() => setEditing(true)} className="rounded-xl border border-white/20 px-3 py-1.5">
            <Text className="text-xs text-white font-medium">Edit</Text>
          </TouchableOpacity>
        )}
        {editing && (
          <TouchableOpacity
            onPress={save}
            disabled={saving}
            className="flex-row items-center gap-x-1.5 rounded-xl bg-teal px-4 py-2"
            style={{ opacity: saving ? 0.5 : 1 }}
          >
            {saving ? <ActivityIndicator size="small" color="#fff" /> : <Save size={14} color="#fff" />}
            <Text className="text-white text-sm font-semibold">{saving ? '…' : 'Save'}</Text>
          </TouchableOpacity>
        )}
      </View>

      <ScrollView
        className="flex-1 px-4"
        contentContainerStyle={{ paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
      >
        {/* No dream yet */}
        {!dream && !editing && (
          <View className="items-center py-16">
            <Text className="text-5xl mb-4">🌟</Text>
            <Text className="text-lg font-bold text-white text-center">What's your big dream?</Text>
            <Text className="text-sm text-muted-fg text-center mt-2 max-w-xs leading-5">
              Define it clearly. NAFS tracks your daily hours and progress toward it.
            </Text>
            <TouchableOpacity
              onPress={() => setEditing(true)}
              className="mt-6 rounded-xl bg-teal px-8 py-3"
            >
              <Text className="text-white font-semibold">Set my dream →</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* View dream (not editing) */}
        {dream && !editing && (
          <View className="gap-y-4">
            {/* Statement card */}
            <View className="rounded-2xl border border-gold/30 bg-gold/5 p-5">
              <Text className="text-xs text-gold uppercase tracking-wider mb-2">My Dream</Text>
              <Text className="text-lg font-bold text-white leading-7">{dream.statement}</Text>
              {dream.why && (
                <Text className="text-sm text-muted-fg mt-3 leading-5 italic">"{dream.why}"</Text>
              )}
            </View>

            {/* Stats */}
            <View className="flex-row gap-x-3">
              <View className="flex-1 rounded-2xl border border-white/10 bg-white/5 p-4 items-center">
                <Text className="text-2xl font-bold text-gold">{dream.total_hours_required}</Text>
                <Text className="text-xs text-muted-fg mt-0.5">hrs required</Text>
              </View>
              <View className="flex-1 rounded-2xl border border-white/10 bg-white/5 p-4 items-center">
                <Text className={`text-2xl font-bold ${daysUntil(dream.dream_date) > 0 ? 'text-white' : 'text-red-400'}`}>
                  {Math.abs(daysUntil(dream.dream_date))}
                </Text>
                <Text className="text-xs text-muted-fg mt-0.5">
                  {daysUntil(dream.dream_date) > 0 ? 'days left' : 'days overdue'}
                </Text>
              </View>
            </View>

            {/* Deadline */}
            <View className="rounded-2xl border border-white/10 bg-white/5 px-4 py-3 flex-row items-center justify-between">
              <Text className="text-sm text-muted-fg">Target date</Text>
              <Text className="text-sm font-semibold text-white">
                {new Date(dream.dream_date).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}
              </Text>
            </View>

            {/* Motivation */}
            <View className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Daily reminder</Text>
              <Text className="text-sm text-white leading-5 text-center italic">
                "Every hour you put in is a brick in the building of who you're becoming."
              </Text>
            </View>
          </View>
        )}

        {/* Edit form */}
        {(editing || (!dream && editing)) && (
          <View className="gap-y-4">
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Dream statement *</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                style={{ minHeight: 100, textAlignVertical: 'top' }}
                placeholder="In 3 years I will be… / I want to become…"
                placeholderTextColor="#6B8CA8"
                value={statement}
                onChangeText={setStatement}
                multiline
                autoFocus
              />
            </View>
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Why does this matter? (optional)</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                style={{ minHeight: 80, textAlignVertical: 'top' }}
                placeholder="Because…"
                placeholderTextColor="#6B8CA8"
                value={why}
                onChangeText={setWhy}
                multiline
              />
            </View>
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Target date (YYYY-MM-DD)</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder={new Date(Date.now() + 365 * 86400000).toISOString().split('T')[0]}
                placeholderTextColor="#6B8CA8"
                value={dreamDate}
                onChangeText={setDreamDate}
              />
            </View>
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Total deep-work hours required</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="e.g. 1000"
                placeholderTextColor="#6B8CA8"
                value={hoursRequired}
                onChangeText={setHoursRequired}
                keyboardType="numeric"
              />
            </View>
            {dream && (
              <TouchableOpacity onPress={() => setEditing(false)} className="rounded-xl border border-white/10 py-3 items-center">
                <Text className="text-muted-fg text-sm">Cancel</Text>
              </TouchableOpacity>
            )}
          </View>
        )}
      </ScrollView>
    </View>
  )
}
