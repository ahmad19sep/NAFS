import { useState, useEffect } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  Alert, ActivityIndicator, Switch,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { LogOut, User, Bell, Trash2, ChevronRight, Shield, Clock, FileText } from 'lucide-react-native'
import { useRouter } from 'expo-router'
import { supabase } from '@/lib/supabase'
import type { UserRow } from '@/types/database'

const TIMEZONES = [
  'Asia/Karachi',
  'Asia/Dubai',
  'Asia/Riyadh',
  'Europe/London',
  'America/New_York',
  'America/Los_Angeles',
  'Asia/Kolkata',
]

function Row({
  icon, label, value, onPress, danger,
}: {
  icon: React.ReactNode
  label: string
  value?: string
  onPress?: () => void
  danger?: boolean
}) {
  return (
    <TouchableOpacity
      onPress={onPress}
      className="flex-row items-center gap-x-3 px-4 py-3.5 border-b border-white/5"
      disabled={!onPress}
    >
      <View className="h-9 w-9 rounded-xl bg-white/5 items-center justify-center">
        {icon}
      </View>
      <Text className={`flex-1 text-sm font-medium ${danger ? 'text-red-400' : 'text-white'}`}>
        {label}
      </Text>
      {value && <Text className="text-xs text-muted-fg mr-1">{value}</Text>}
      {onPress && <ChevronRight size={16} color={danger ? '#f87171' : '#6B8CA8'} />}
    </TouchableOpacity>
  )
}

export default function ProfileScreen() {
  const insets = useSafeAreaInsets()
  const router = useRouter()
  const [profile, setProfile] = useState<UserRow | null>(null)
  const [loading, setLoading] = useState(true)
  const [editingName, setEditingName] = useState(false)
  const [name, setName] = useState('')
  const [savingName, setSavingName] = useState(false)

  useEffect(() => {
    loadProfile()
  }, [])

  async function loadProfile() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data } = await supabase.from('users').select('*').eq('id', user.id).single()
    setProfile(data)
    setName(data?.name ?? '')
    setLoading(false)
  }

  async function saveName() {
    if (!name.trim() || !profile) return
    setSavingName(true)
    await supabase.from('users').update({ name: name.trim() }).eq('id', profile.id)
    setProfile(prev => prev ? { ...prev, name: name.trim() } : null)
    setSavingName(false)
    setEditingName(false)
  }

  async function changeTimezone(tz: string) {
    if (!profile) return
    await supabase.from('users').update({ timezone: tz }).eq('id', profile.id)
    setProfile(prev => prev ? { ...prev, timezone: tz } : null)
  }

  async function signOut() {
    Alert.alert('Sign out?', 'You will be returned to the login screen.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Sign out', style: 'destructive',
        onPress: async () => {
          await supabase.auth.signOut()
          router.replace('/auth')
        },
      },
    ])
  }

  async function deleteAccount() {
    Alert.alert(
      'Delete account?',
      'This permanently deletes all your data and cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete everything', style: 'destructive',
          onPress: async () => {
            try {
              const { data: { user } } = await supabase.auth.getUser()
              if (!user) return
              // Delete all user data (Supabase CASCADE handles related rows)
              await supabase.from('users').delete().eq('id', user.id)
              await supabase.auth.signOut()
              router.replace('/auth')
            } catch {
              Alert.alert('Error', 'Could not delete account. Please try again.')
            }
          },
        },
      ]
    )
  }

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <ScrollView className="flex-1">
        {/* Avatar + name */}
        <View className="items-center pt-8 pb-6 px-4">
          <View className="h-20 w-20 rounded-full bg-teal items-center justify-center mb-3">
            <Text className="text-3xl font-bold text-white">
              {(profile?.name ?? '?')[0]?.toUpperCase()}
            </Text>
          </View>

          {editingName ? (
            <View className="flex-row items-center gap-x-2 mt-2">
              <TextInput
                className="rounded-xl border border-white/10 bg-white/10 px-4 py-2 text-white text-base font-semibold"
                value={name}
                onChangeText={setName}
                autoFocus
                onBlur={saveName}
              />
              <TouchableOpacity onPress={saveName} disabled={savingName}>
                {savingName
                  ? <ActivityIndicator size="small" color="#C9A227" />
                  : <Text className="text-gold font-semibold">Save</Text>
                }
              </TouchableOpacity>
            </View>
          ) : (
            <TouchableOpacity onPress={() => setEditingName(true)} className="items-center">
              <Text className="text-xl font-bold text-white">{profile?.name}</Text>
              <Text className="text-xs text-muted-fg mt-0.5">Tap to edit name</Text>
            </TouchableOpacity>
          )}

          <Text className="text-sm text-muted-fg mt-1">{profile?.email}</Text>

          <View className="flex-row items-center gap-x-1.5 mt-2">
            <Text className="text-xs text-muted-fg">Member since</Text>
            <Text className="text-xs text-white">
              {profile?.created_at
                ? new Date(profile.created_at).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
                : '—'}
            </Text>
          </View>
        </View>

        {/* Settings */}
        <View className="mx-4 rounded-2xl border border-white/10 bg-white/5 overflow-hidden mb-4">
          <View className="px-4 py-3 border-b border-white/10">
            <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider">Account</Text>
          </View>
          <Row
            icon={<Clock size={16} color="#6B8CA8" />}
            label="Timezone"
            value={profile?.timezone ?? 'Asia/Karachi'}
            onPress={() => {
              Alert.alert(
                'Select Timezone',
                undefined,
                [
                  ...TIMEZONES.map(tz => ({
                    text: tz,
                    onPress: () => changeTimezone(tz),
                  })),
                  { text: 'Cancel', style: 'cancel' },
                ]
              )
            }}
          />
        </View>

        <View className="mx-4 rounded-2xl border border-white/10 bg-white/5 overflow-hidden mb-4">
          <View className="px-4 py-3 border-b border-white/10">
            <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider">Progress</Text>
          </View>
          <Row
            icon={<FileText size={16} color="#C9A227" />}
            label="Weekly & monthly reports"
            value="Print / PDF"
            onPress={() => router.push('/(app)/reports')}
          />
        </View>

        <View className="mx-4 rounded-2xl border border-white/10 bg-white/5 overflow-hidden mb-4">
          <View className="px-4 py-3 border-b border-white/10">
            <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider">Session</Text>
          </View>
          <Row
            icon={<LogOut size={16} color="#6B8CA8" />}
            label="Sign out"
            onPress={signOut}
          />
        </View>

        <View className="mx-4 rounded-2xl border border-red-500/20 bg-red-500/5 overflow-hidden mb-8">
          <View className="px-4 py-3 border-b border-red-500/10">
            <Text className="text-xs font-semibold text-red-400/70 uppercase tracking-wider">Danger zone</Text>
          </View>
          <Row
            icon={<Trash2 size={16} color="#f87171" />}
            label="Delete account & all data"
            onPress={deleteAccount}
            danger
          />
        </View>

        {/* Islamic footer */}
        <View className="items-center px-4 pb-8">
          <Text className="text-3xl mb-2">🤲</Text>
          <Text className="text-xs text-muted-fg text-center leading-5">
            "Indeed, Allah will not change the condition of a people{'\n'}
            until they change what is in themselves."{'\n'}— Quran 13:11
          </Text>
        </View>
      </ScrollView>
    </View>
  )
}
