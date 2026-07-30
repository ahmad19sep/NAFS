import { useState, useEffect, useCallback, useRef } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput, FlatList,
  Modal, KeyboardAvoidingView, Platform, RefreshControl, ActivityIndicator, Alert,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Plus, X, Trash2, ChevronDown } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { todayString, isoWeekMonday, monthStart } from '@/lib/utils'
import type { TaskRow } from '@/types/database'

type TabType = 'daily' | 'weekly' | 'monthly'
type Priority = 'high' | 'medium' | 'low'

const PRIORITY_COLORS = { high: '#f87171', medium: '#fbbf24', low: '#60a5fa' }
const PRIORITY_LABELS = { high: 'High', medium: 'Medium', low: 'Low' }

function periodDate(type: TabType): string {
  const today = todayString()
  if (type === 'daily') return today
  if (type === 'weekly') return isoWeekMonday(today)
  return monthStart(today)
}

function isPast(task: TaskRow): boolean {
  return task.status === 'active' && task.period_date < todayString() && task.type === 'daily'
}

interface AddTaskModalProps {
  visible: boolean
  type: TabType
  onClose: () => void
  onSave: (task: Omit<TaskRow, 'id' | 'created_at' | 'updated_at' | 'completed_at'>) => Promise<void>
}

function AddTaskModal({ visible, type, onClose, onSave }: AddTaskModalProps) {
  const [title, setTitle] = useState('')
  const [note, setNote] = useState('')
  const [priority, setPriority] = useState<Priority>('medium')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (visible) { setTitle(''); setNote(''); setPriority('medium') }
  }, [visible])

  async function handleSave() {
    if (!title.trim()) return
    setSaving(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return
      await onSave({
        user_id: user.id,
        title: title.trim(),
        note: note.trim() || null,
        type,
        priority,
        status: 'active',
        period_date: periodDate(type),
      })
      onClose()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal visible={visible} transparent animationType="slide">
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View className="flex-1 justify-end" style={{ backgroundColor: 'rgba(11,26,43,0.8)' }}>
          <TouchableOpacity className="flex-1" onPress={onClose} />
          <View className="bg-card rounded-t-3xl border-t border-white/10 px-5 pt-4 pb-8">
            <View className="flex-row items-center justify-between mb-5">
              <Text className="text-lg font-bold text-white">New {type} task</Text>
              <TouchableOpacity onPress={onClose}><X size={20} color="#6B8CA8" /></TouchableOpacity>
            </View>

            <View className="gap-y-4">
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Task title"
                placeholderTextColor="#6B8CA8"
                value={title}
                onChangeText={setTitle}
                autoFocus
              />
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="Note (optional)"
                placeholderTextColor="#6B8CA8"
                value={note}
                onChangeText={setNote}
              />

              {/* Priority */}
              <View>
                <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">Priority</Text>
                <View className="flex-row gap-x-2">
                  {(['high', 'medium', 'low'] as Priority[]).map(p => (
                    <TouchableOpacity
                      key={p}
                      onPress={() => setPriority(p)}
                      className={`flex-1 rounded-xl border py-2.5 items-center ${
                        priority === p ? 'border-transparent' : 'border-white/10 bg-white/5'
                      }`}
                      style={priority === p ? { backgroundColor: PRIORITY_COLORS[p] + '33', borderColor: PRIORITY_COLORS[p] } : undefined}
                    >
                      <Text className="text-sm font-medium text-white">{PRIORITY_LABELS[p]}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>

              <TouchableOpacity
                onPress={handleSave}
                disabled={saving || !title.trim()}
                className="rounded-xl bg-teal py-3.5 items-center"
                style={{ opacity: saving || !title.trim() ? 0.5 : 1 }}
              >
                {saving
                  ? <ActivityIndicator color="#fff" />
                  : <Text className="text-white font-semibold">Save Task</Text>
                }
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  )
}

function TaskItem({
  task,
  onToggle,
  onDelete,
}: {
  task: TaskRow
  onToggle: () => void
  onDelete: () => void
}) {
  const done = task.status === 'completed'
  const past = isPast(task)

  return (
    <View className={`flex-row items-center gap-x-3 rounded-xl px-3 py-3 border mb-2 ${
      done ? 'border-emerald-500/20 bg-emerald-500/5' :
      past ? 'border-red-500/20 bg-red-500/5' :
      'border-white/10 bg-white/5'
    }`}>
      {/* Priority dot */}
      <View className="h-2 w-2 rounded-full" style={{ backgroundColor: PRIORITY_COLORS[task.priority] }} />

      {/* Checkbox */}
      <TouchableOpacity onPress={onToggle}>
        <View className={`h-6 w-6 rounded-full border-2 items-center justify-center ${
          done ? 'bg-emerald-500 border-emerald-500' : 'border-white/30'
        }`}>
          {done && <Text className="text-white text-xs">✓</Text>}
        </View>
      </TouchableOpacity>

      {/* Title */}
      <View className="flex-1">
        <Text className={`text-sm font-medium ${
          done ? 'text-muted-fg line-through' : past ? 'text-red-400' : 'text-white'
        }`}>
          {task.title}
        </Text>
        {task.note ? (
          <Text className="text-xs text-muted-fg mt-0.5" numberOfLines={1}>{task.note}</Text>
        ) : null}
        {past && !done && <Text className="text-xs text-red-400/70 mt-0.5">Missed</Text>}
      </View>

      {/* Delete */}
      <TouchableOpacity onPress={onDelete} className="p-1">
        <Trash2 size={16} color="#6B8CA8" />
      </TouchableOpacity>
    </View>
  )
}

export default function TasksScreen() {
  const insets = useSafeAreaInsets()
  const [activeTab, setActiveTab] = useState<TabType>('daily')
  const [tasks, setTasks] = useState<TaskRow[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [showAdd, setShowAdd] = useState(false)

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const sixMonthsAgo = new Date()
    sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6)
    const start = sixMonthsAgo.toISOString().split('T')[0]

    const { data } = await supabase
      .from('tasks')
      .select('*')
      .eq('user_id', user.id)
      .gte('period_date', start)
      .order('priority', { ascending: true })
      .order('created_at', { ascending: false })

    setTasks(data ?? [])
  }, [])

  useEffect(() => {
    load().finally(() => setLoading(false))
  }, [load])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await load()
    setRefreshing(false)
  }, [load])

  async function addTask(task: Omit<TaskRow, 'id' | 'created_at' | 'updated_at' | 'completed_at'>) {
    const { data } = await supabase.from('tasks').insert(task).select().single()
    if (data) setTasks(prev => [data, ...prev])
  }

  async function toggleTask(id: string) {
    const task = tasks.find(t => t.id === id)
    if (!task) return
    const newStatus = task.status === 'completed' ? 'active' : 'completed'
    await supabase.from('tasks').update({
      status: newStatus,
      completed_at: newStatus === 'completed' ? new Date().toISOString() : null,
    }).eq('id', id)
    setTasks(prev => prev.map(t => t.id === id ? { ...t, status: newStatus } : t))
  }

  async function deleteTask(id: string) {
    Alert.alert('Delete task?', '', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive',
        onPress: async () => {
          await supabase.from('tasks').delete().eq('id', id)
          setTasks(prev => prev.filter(t => t.id !== id))
        },
      },
    ])
  }

  // Filter tasks for current tab + period
  const today = todayString()
  const filtered = tasks.filter(t => {
    if (t.type !== activeTab) return false
    if (activeTab === 'daily') {
      return t.period_date === today || (t.status === 'active' && t.period_date >= new Date(Date.now() - 7 * 86400000).toISOString().split('T')[0])
    }
    if (activeTab === 'weekly') return t.period_date >= isoWeekMonday(today)
    return t.period_date >= monthStart(today)
  })

  const pending = filtered.filter(t => t.status === 'active')
  const completed = filtered.filter(t => t.status === 'completed')

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      {/* Header */}
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <Text className="text-xl font-bold text-white">Tasks</Text>
        <TouchableOpacity
          onPress={() => setShowAdd(true)}
          className="h-9 w-9 rounded-full bg-teal items-center justify-center"
        >
          <Plus size={20} color="#fff" />
        </TouchableOpacity>
      </View>

      {/* Tabs */}
      <View className="flex-row mx-4 rounded-xl border border-white/10 bg-white/5 p-1 mb-4">
        {(['daily', 'weekly', 'monthly'] as TabType[]).map(t => (
          <TouchableOpacity
            key={t}
            onPress={() => setActiveTab(t)}
            className={`flex-1 rounded-lg py-2 items-center ${activeTab === t ? 'bg-teal' : ''}`}
          >
            <Text className={`text-sm font-semibold capitalize ${activeTab === t ? 'text-white' : 'text-muted-fg'}`}>
              {t}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {loading ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#C9A227" />
        </View>
      ) : (
        <ScrollView
          className="flex-1 px-4"
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
        >
          {/* Pending */}
          {pending.length > 0 && (
            <>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2">
                Pending ({pending.length})
              </Text>
              {pending.map(t => (
                <TaskItem key={t.id} task={t} onToggle={() => toggleTask(t.id)} onDelete={() => deleteTask(t.id)} />
              ))}
            </>
          )}

          {/* Completed */}
          {completed.length > 0 && (
            <>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-2 mt-4">
                Done ({completed.length})
              </Text>
              {completed.map(t => (
                <TaskItem key={t.id} task={t} onToggle={() => toggleTask(t.id)} onDelete={() => deleteTask(t.id)} />
              ))}
            </>
          )}

          {/* Empty */}
          {filtered.length === 0 && (
            <View className="items-center py-16">
              <Text className="text-4xl mb-3">✅</Text>
              <Text className="text-base font-semibold text-white">No {activeTab} tasks</Text>
              <Text className="text-sm text-muted-fg mt-1">Tap + to add one.</Text>
            </View>
          )}

          <View className="h-6" />
        </ScrollView>
      )}

      <AddTaskModal
        visible={showAdd}
        type={activeTab}
        onClose={() => setShowAdd(false)}
        onSave={addTask}
      />
    </View>
  )
}
