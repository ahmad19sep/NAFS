import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  Modal, KeyboardAvoidingView, Platform, ActivityIndicator, Alert,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Plus, X, Trash2, CheckCircle, Circle } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { daysUntil } from '@/lib/utils'
import type { GoalRow, GoalMilestoneRow } from '@/types/database'

interface GoalWithMilestones extends GoalRow {
  goal_milestones: GoalMilestoneRow[]
}

function GoalCard({
  goal,
  onDelete,
  onUpdateProgress,
  onToggleMilestone,
}: {
  goal: GoalWithMilestones
  onDelete: () => void
  onUpdateProgress: (pct: number) => void
  onToggleMilestone: (milestone: GoalMilestoneRow) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const days = goal.deadline ? daysUntil(goal.deadline) : null

  return (
    <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-3">
      <TouchableOpacity onPress={() => setExpanded(!expanded)}>
        <View className="flex-row items-start gap-x-3">
          <Text className="text-2xl mt-0.5">{goal.emoji}</Text>
          <View className="flex-1">
            <Text className="text-sm font-semibold text-white">{goal.title}</Text>
            {goal.deadline && (
              <Text className={`text-xs mt-0.5 ${days && days < 0 ? 'text-red-400' : 'text-muted-fg'}`}>
                {days === null ? '' : days > 0 ? `${days} days left` : `${Math.abs(days!)} days overdue`}
              </Text>
            )}
            {/* Progress bar */}
            <View className="flex-row items-center gap-x-2 mt-2">
              <View className="flex-1 h-2 rounded-full bg-white/10">
                <View className="h-full rounded-full bg-gold" style={{ width: `${goal.progress_pct}%` }} />
              </View>
              <Text className="text-xs text-gold font-semibold">{goal.progress_pct}%</Text>
            </View>
          </View>
          <TouchableOpacity onPress={onDelete} className="p-1">
            <Trash2 size={16} color="#6B8CA8" />
          </TouchableOpacity>
        </View>
      </TouchableOpacity>

      {expanded && (
        <View className="mt-4 pt-4 border-t border-white/10">
          {/* Progress slider as tappable segments */}
          <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Update Progress</Text>
          <View className="flex-row gap-x-1.5 mb-4">
            {[0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100].map(pct => (
              <TouchableOpacity
                key={pct}
                onPress={() => onUpdateProgress(pct)}
                className={`flex-1 h-7 rounded items-center justify-center ${
                  pct <= goal.progress_pct ? 'bg-gold' : 'bg-white/10'
                }`}
              >
                {pct % 50 === 0 && (
                  <Text className="text-[9px] text-white/70">{pct}</Text>
                )}
              </TouchableOpacity>
            ))}
          </View>

          {/* Milestones */}
          {goal.goal_milestones.length > 0 && (
            <>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Milestones</Text>
              <View className="gap-y-2">
                {goal.goal_milestones.map(m => (
                  <TouchableOpacity
                    key={m.id}
                    onPress={() => onToggleMilestone(m)}
                    className="flex-row items-center gap-x-2.5"
                  >
                    {m.done
                      ? <CheckCircle size={18} color="#34d399" />
                      : <Circle size={18} color="#6B8CA8" />
                    }
                    <Text className={`text-sm flex-1 ${m.done ? 'text-muted-fg line-through' : 'text-white'}`}>
                      {m.title}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </>
          )}

          {/* AI Plan */}
          {goal.ai_plan && (
            <>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mt-4 mb-2">AI Plan</Text>
              <Text className="text-sm text-muted-fg leading-5">{goal.ai_plan}</Text>
            </>
          )}
        </View>
      )}
    </View>
  )
}

interface AddGoalModalProps {
  visible: boolean
  onClose: () => void
  onSave: () => void
}

function AddGoalModal({ visible, onClose, onSave }: AddGoalModalProps) {
  const [title, setTitle] = useState('')
  const [emoji, setEmoji] = useState('⭐')
  const [description, setDescription] = useState('')
  const [deadline, setDeadline] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (visible) { setTitle(''); setEmoji('⭐'); setDescription(''); setDeadline('') }
  }, [visible])

  async function handleSave() {
    if (!title.trim()) return
    setSaving(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return
      await supabase.from('goals').insert({
        user_id: user.id,
        title: title.trim(),
        emoji,
        description: description || null,
        deadline: deadline || null,
        progress_pct: 0,
      })
      onSave()
      onClose()
    } finally {
      setSaving(false)
    }
  }

  const EMOJI_OPTS = ['⭐','🎯','🏆','💎','🚀','🌟','💡','📚','💪','🌱','🎓','🏠','❤️','✨']

  return (
    <Modal visible={visible} transparent animationType="slide">
      <KeyboardAvoidingView className="flex-1" behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <View className="flex-1 justify-end" style={{ backgroundColor: 'rgba(11,26,43,0.85)' }}>
          <TouchableOpacity className="flex-1" onPress={onClose} />
          <View className="bg-card rounded-t-3xl border-t border-white/10 px-5 pt-4 pb-8">
            <View className="flex-row items-center justify-between mb-5">
              <Text className="text-lg font-bold text-white">New Goal</Text>
              <TouchableOpacity onPress={onClose}><X size={20} color="#6B8CA8" /></TouchableOpacity>
            </View>

            <ScrollView horizontal showsHorizontalScrollIndicator={false} className="mb-4">
              <View className="flex-row gap-x-2">
                {EMOJI_OPTS.map(e => (
                  <TouchableOpacity
                    key={e}
                    onPress={() => setEmoji(e)}
                    className={`h-10 w-10 rounded-xl items-center justify-center border ${
                      emoji === e ? 'border-gold bg-gold/20' : 'border-white/10 bg-white/5'
                    }`}
                  >
                    <Text className="text-xl">{e}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </ScrollView>

            <View className="gap-y-4">
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Goal title"
                placeholderTextColor="#6B8CA8"
                value={title}
                onChangeText={setTitle}
                autoFocus
              />
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Description (optional)"
                placeholderTextColor="#6B8CA8"
                value={description}
                onChangeText={setDescription}
                multiline
                numberOfLines={2}
              />
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Deadline YYYY-MM-DD (optional)"
                placeholderTextColor="#6B8CA8"
                value={deadline}
                onChangeText={setDeadline}
              />
              <TouchableOpacity
                onPress={handleSave}
                disabled={saving || !title.trim()}
                className="rounded-xl bg-teal py-3.5 items-center"
                style={{ opacity: saving || !title.trim() ? 0.5 : 1 }}
              >
                {saving ? <ActivityIndicator color="#fff" /> : <Text className="text-white font-semibold">Save Goal</Text>}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  )
}

export default function GoalsScreen() {
  const insets = useSafeAreaInsets()
  const [goals, setGoals] = useState<GoalWithMilestones[]>([])
  const [loading, setLoading] = useState(true)
  const [showAdd, setShowAdd] = useState(false)

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data } = await supabase
      .from('goals')
      .select('*, goal_milestones(*)')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false })
    setGoals((data as GoalWithMilestones[]) ?? [])
  }, [])

  useEffect(() => { load().finally(() => setLoading(false)) }, [load])

  async function updateProgress(goal: GoalWithMilestones, pct: number) {
    await supabase.from('goals').update({ progress_pct: pct }).eq('id', goal.id)
    setGoals(prev => prev.map(g => g.id === goal.id ? { ...g, progress_pct: pct } : g))
  }

  async function toggleMilestone(goal: GoalWithMilestones, milestone: GoalMilestoneRow) {
    const newDone = !milestone.done
    await supabase.from('goal_milestones').update({ done: newDone }).eq('id', milestone.id)
    const doneCount = goal.goal_milestones.filter(m => (m.id === milestone.id ? newDone : m.done)).length
    const pct = Math.round((doneCount / goal.goal_milestones.length) * 100)
    await supabase.from('goals').update({ progress_pct: pct }).eq('id', goal.id)
    setGoals(prev => prev.map(g =>
      g.id === goal.id
        ? {
            ...g,
            progress_pct: pct,
            goal_milestones: g.goal_milestones.map(m => m.id === milestone.id ? { ...m, done: newDone } : m),
          }
        : g
    ))
  }

  async function deleteGoal(id: string) {
    Alert.alert('Delete goal?', '', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive',
        onPress: async () => {
          await supabase.from('goals').delete().eq('id', id)
          setGoals(prev => prev.filter(g => g.id !== id))
        },
      },
    ])
  }

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <Text className="text-xl font-bold text-white">Goals</Text>
        <TouchableOpacity onPress={() => setShowAdd(true)} className="h-9 w-9 rounded-full bg-teal items-center justify-center">
          <Plus size={20} color="#fff" />
        </TouchableOpacity>
      </View>

      <ScrollView className="flex-1 px-4" contentContainerStyle={{ paddingVertical: 12 }}>
        {goals.length === 0 ? (
          <View className="items-center py-16">
            <Text className="text-4xl mb-3">🎯</Text>
            <Text className="text-base font-semibold text-white">No goals yet</Text>
            <Text className="text-sm text-muted-fg mt-1">Set a goal to work towards.</Text>
          </View>
        ) : (
          goals.map(g => (
            <GoalCard
              key={g.id}
              goal={g}
              onDelete={() => deleteGoal(g.id)}
              onUpdateProgress={pct => updateProgress(g, pct)}
              onToggleMilestone={m => toggleMilestone(g, m)}
            />
          ))
        )}
        <View className="h-6" />
      </ScrollView>

      <AddGoalModal visible={showAdd} onClose={() => setShowAdd(false)} onSave={load} />
    </View>
  )
}
