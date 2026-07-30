import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput, Modal,
  KeyboardAvoidingView, Platform, ActivityIndicator, Alert, RefreshControl,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Plus, X, Trash2, Camera } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { todayString, getStreakEmoji, addDays } from '@/lib/utils'
import type { ChallengeRow, ChallengeCheckinRow } from '@/types/database'

interface ChallengeWithCheckins extends ChallengeRow {
  checkins: ChallengeCheckinRow[]
}

function ChallengeCard({
  challenge,
  onCheckin,
  onDelete,
}: {
  challenge: ChallengeWithCheckins
  onCheckin: () => void
  onDelete: () => void
}) {
  const today = todayString()
  const todayCheckin = challenge.checkins.find(c => c.date === today)
  const doneTodayAlready = todayCheckin?.completed ?? false

  const daysPassed = Math.max(0, Math.floor(
    (Date.now() - new Date(challenge.start_date).getTime()) / 86400000
  ))
  const pct = Math.min(100, Math.round((daysPassed / challenge.duration_days) * 100))
  const remaining = Math.max(0, challenge.duration_days - daysPassed)

  return (
    <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-3">
      <View className="flex-row items-start justify-between gap-x-3">
        <Text className="text-2xl">{challenge.emoji}</Text>
        <View className="flex-1">
          <Text className="text-sm font-semibold text-white">{challenge.title}</Text>
          <Text className="text-xs text-muted-fg mt-0.5">
            Day {daysPassed + 1} of {challenge.duration_days} · {remaining}d remaining
          </Text>
        </View>
        <View className="flex-row items-center gap-x-2">
          <Text className="text-base">{getStreakEmoji(challenge.current_streak)}</Text>
          <Text className="text-sm font-bold text-gold">{challenge.current_streak}</Text>
          <TouchableOpacity onPress={onDelete} className="ml-1 p-1">
            <Trash2 size={14} color="#6B8CA8" />
          </TouchableOpacity>
        </View>
      </View>

      {/* Progress */}
      <View className="flex-row items-center gap-x-2 mt-3">
        <View className="flex-1 h-2 rounded-full bg-white/10">
          <View className="h-full rounded-full bg-teal" style={{ width: `${pct}%` }} />
        </View>
        <Text className="text-xs text-muted-fg">{pct}%</Text>
      </View>

      {/* Check in today */}
      {challenge.status === 'active' && (
        <TouchableOpacity
          onPress={onCheckin}
          disabled={doneTodayAlready}
          className={`mt-3 rounded-xl py-2.5 items-center border ${
            doneTodayAlready
              ? 'border-emerald-500/30 bg-emerald-500/10'
              : 'border-teal bg-teal/20'
          }`}
          style={{ opacity: doneTodayAlready ? 0.7 : 1 }}
        >
          <Text className={`text-sm font-semibold ${doneTodayAlready ? 'text-emerald-400' : 'text-teal-light'}`}>
            {doneTodayAlready ? '✓ Done today' : 'Check in today'}
          </Text>
        </TouchableOpacity>
      )}
    </View>
  )
}

interface AddChallengeModalProps {
  visible: boolean
  onClose: () => void
  onSave: () => void
}

function AddChallengeModal({ visible, onClose, onSave }: AddChallengeModalProps) {
  const [title, setTitle] = useState('')
  const [emoji, setEmoji] = useState('🎯')
  const [duration, setDuration] = useState<21 | 30 | 90>(21)
  const [sadqa, setSadqa] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (visible) { setTitle(''); setEmoji('🎯'); setDuration(21); setSadqa('') }
  }, [visible])

  async function handleSave() {
    if (!title.trim()) return
    setSaving(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return
      await supabase.from('challenges').insert({
        user_id: user.id,
        title: title.trim(),
        emoji,
        duration_days: duration,
        start_date: todayString(),
        sadqa_amount: sadqa ? parseFloat(sadqa) : null,
        status: 'active',
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
          <View className="bg-card rounded-t-3xl border-t border-white/10 px-5 pt-4 pb-8">
            <View className="flex-row items-center justify-between mb-5">
              <Text className="text-lg font-bold text-white">New Challenge</Text>
              <TouchableOpacity onPress={onClose}><X size={20} color="#6B8CA8" /></TouchableOpacity>
            </View>

            <View className="gap-y-4">
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Challenge name"
                placeholderTextColor="#6B8CA8"
                value={title}
                onChangeText={setTitle}
                autoFocus
              />

              <View>
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Duration</Text>
                <View className="flex-row gap-x-2">
                  {([21, 30, 90] as const).map(d => (
                    <TouchableOpacity
                      key={d}
                      onPress={() => setDuration(d)}
                      className={`flex-1 rounded-xl border py-2.5 items-center ${
                        duration === d ? 'border-teal bg-teal/20' : 'border-white/10 bg-white/5'
                      }`}
                    >
                      <Text className={`text-sm font-semibold ${duration === d ? 'text-teal-light' : 'text-muted-fg'}`}>
                        {d} days
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>

              <View>
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">
                  Sadqa if broken (PKR, optional)
                </Text>
                <TextInput
                  className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                  placeholder="e.g. 500"
                  placeholderTextColor="#6B8CA8"
                  value={sadqa}
                  onChangeText={setSadqa}
                  keyboardType="numeric"
                />
              </View>

              <TouchableOpacity
                onPress={handleSave}
                disabled={saving || !title.trim()}
                className="rounded-xl bg-teal py-3.5 items-center"
                style={{ opacity: saving || !title.trim() ? 0.5 : 1 }}
              >
                {saving ? <ActivityIndicator color="#fff" /> : <Text className="text-white font-semibold">Start Challenge</Text>}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  )
}

export default function ChallengesScreen() {
  const insets = useSafeAreaInsets()
  const [challenges, setChallenges] = useState<ChallengeWithCheckins[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [showAdd, setShowAdd] = useState(false)
  const [tab, setTab] = useState<'active' | 'completed'>('active')

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data: chals } = await supabase
      .from('challenges')
      .select('*')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false })

    if (!chals?.length) { setChallenges([]); return }

    const ids = chals.map(c => c.id)
    const { data: checkins } = await supabase
      .from('challenge_checkins')
      .select('*')
      .in('challenge_id', ids)

    setChallenges(
      chals.map(c => ({
        ...c,
        checkins: (checkins ?? []).filter(ck => ck.challenge_id === c.id),
      })) as ChallengeWithCheckins[]
    )
  }, [])

  useEffect(() => { load().finally(() => setLoading(false)) }, [load])
  const onRefresh = useCallback(async () => { setRefreshing(true); await load(); setRefreshing(false) }, [load])

  async function checkIn(challenge: ChallengeWithCheckins) {
    const today = todayString()
    const { data } = await supabase.from('challenge_checkins').upsert({
      challenge_id: challenge.id,
      date: today,
      completed: true,
    }, { onConflict: 'challenge_id,date' }).select().single()

    const newStreak = challenge.current_streak + 1
    await supabase.from('challenges').update({
      current_streak: newStreak,
      longest_streak: Math.max(challenge.longest_streak, newStreak),
    }).eq('id', challenge.id)

    setChallenges(prev => prev.map(c =>
      c.id === challenge.id
        ? {
            ...c,
            current_streak: newStreak,
            longest_streak: Math.max(c.longest_streak, newStreak),
            checkins: data
              ? [...c.checkins.filter(ck => ck.date !== today), data]
              : c.checkins,
          }
        : c
    ))
  }

  async function deleteChallenge(id: string) {
    Alert.alert('Delete challenge?', '', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive',
        onPress: async () => {
          await supabase.from('challenges').delete().eq('id', id)
          setChallenges(prev => prev.filter(c => c.id !== id))
        },
      },
    ])
  }

  const filtered = challenges.filter(c => c.status === (tab === 'active' ? 'active' : 'completed'))

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <Text className="text-xl font-bold text-white">Challenges</Text>
        <TouchableOpacity onPress={() => setShowAdd(true)} className="h-9 w-9 rounded-full bg-teal items-center justify-center">
          <Plus size={20} color="#fff" />
        </TouchableOpacity>
      </View>

      <View className="flex-row mx-4 rounded-xl border border-white/10 bg-white/5 p-1 mb-4">
        {(['active', 'completed'] as const).map(t => (
          <TouchableOpacity
            key={t}
            onPress={() => setTab(t)}
            className={`flex-1 rounded-lg py-2 items-center ${tab === t ? 'bg-teal' : ''}`}
          >
            <Text className={`text-sm font-semibold capitalize ${tab === t ? 'text-white' : 'text-muted-fg'}`}>{t}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView
        className="flex-1 px-4"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
      >
        {filtered.length === 0 ? (
          <View className="items-center py-16">
            <Text className="text-4xl mb-3">🎯</Text>
            <Text className="text-base font-semibold text-white">No {tab} challenges</Text>
            {tab === 'active' && (
              <Text className="text-sm text-muted-fg mt-1">Start a 21, 30, or 90-day challenge.</Text>
            )}
          </View>
        ) : (
          filtered.map(c => (
            <ChallengeCard
              key={c.id}
              challenge={c}
              onCheckin={() => checkIn(c)}
              onDelete={() => deleteChallenge(c.id)}
            />
          ))
        )}
        <View className="h-6" />
      </ScrollView>

      <AddChallengeModal visible={showAdd} onClose={() => setShowAdd(false)} onSave={load} />
    </View>
  )
}
