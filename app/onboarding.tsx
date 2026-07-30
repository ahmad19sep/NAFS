import { useState } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, ActivityIndicator,
} from 'react-native'
import { useRouter } from 'expo-router'
import { supabase } from '@/lib/supabase'

const STARTER_PACKS = [
  {
    id: 'deen',
    label: 'Deen & Prayer',
    emoji: '🕌',
    habits: [
      { name: 'Pray Fajr', emoji: '🌅', type: 'boolean' as const, category: 'deen' },
      { name: 'Read Quran (15 min)', emoji: '📖', type: 'duration' as const, target_value: 15, unit: 'min', category: 'deen' },
      { name: 'Pray all 5 prayers', emoji: '🤲', type: 'boolean' as const, category: 'deen' },
    ],
  },
  {
    id: 'health',
    label: 'Health & Body',
    emoji: '💪',
    habits: [
      { name: 'Exercise', emoji: '🏃', type: 'duration' as const, target_value: 30, unit: 'min', category: 'health' },
      { name: 'Drink water (8 glasses)', emoji: '💧', type: 'count' as const, target_value: 8, unit: 'glasses', category: 'health' },
      { name: 'Sleep 7+ hours', emoji: '😴', type: 'boolean' as const, category: 'health' },
    ],
  },
  {
    id: 'mind',
    label: 'Mind & Focus',
    emoji: '🧠',
    habits: [
      { name: 'Read (30 min)', emoji: '📚', type: 'duration' as const, target_value: 30, unit: 'min', category: 'mind' },
      { name: 'No social media before 10am', emoji: '📵', type: 'boolean' as const, category: 'mind' },
      { name: 'Deep work session', emoji: '🎯', type: 'duration' as const, target_value: 90, unit: 'min', category: 'mind' },
    ],
  },
  {
    id: 'growth',
    label: 'Personal Growth',
    emoji: '🌱',
    habits: [
      { name: 'Journal / reflect', emoji: '📝', type: 'boolean' as const, category: 'growth' },
      { name: 'Learn something new', emoji: '💡', type: 'boolean' as const, category: 'growth' },
      { name: 'Cold shower', emoji: '🚿', type: 'boolean' as const, category: 'growth' },
    ],
  },
]

export default function OnboardingScreen() {
  const router = useRouter()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)

  function togglePack(id: string) {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function handleFinish() {
    setLoading(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return

      // Collect all selected habits
      const habits = STARTER_PACKS
        .filter(p => selected.has(p.id))
        .flatMap((p, pi) =>
          p.habits.map((h, hi) => ({
            user_id: user.id,
            name: h.name,
            emoji: h.emoji,
            type: h.type,
            target_value: (h as any).target_value ?? 1,
            unit: (h as any).unit ?? '',
            category: h.category,
            sort_order: pi * 10 + hi,
          }))
        )

      if (habits.length > 0) {
        await supabase.from('habits').insert(habits)
      }

      await supabase.from('users').update({ onboarding_complete: true }).eq('id', user.id)
      router.replace('/(app)/dashboard')
    } finally {
      setLoading(false)
    }
  }

  return (
    <View className="flex-1 bg-navy">
      <ScrollView className="flex-1" contentContainerStyle={{ padding: 24 }}>
        <View className="mt-12 mb-8">
          <Text className="text-2xl font-bold text-white">Choose your starter habits</Text>
          <Text className="text-sm text-muted-fg mt-2">
            Pick the packs that match your goals. You can add more anytime.
          </Text>
        </View>

        <View className="gap-y-3">
          {STARTER_PACKS.map(pack => (
            <TouchableOpacity
              key={pack.id}
              onPress={() => togglePack(pack.id)}
              className={`rounded-2xl border p-4 ${
                selected.has(pack.id)
                  ? 'border-teal bg-teal/20'
                  : 'border-white/10 bg-white/5'
              }`}
            >
              <View className="flex-row items-center gap-x-3 mb-3">
                <Text className="text-2xl">{pack.emoji}</Text>
                <Text className="text-base font-semibold text-white">{pack.label}</Text>
                {selected.has(pack.id) && (
                  <View className="ml-auto h-5 w-5 rounded-full bg-teal items-center justify-center">
                    <Text className="text-white text-xs font-bold">✓</Text>
                  </View>
                )}
              </View>
              <View className="gap-y-1.5">
                {pack.habits.map(h => (
                  <Text key={h.name} className="text-sm text-muted-fg">
                    {h.emoji} {h.name}
                  </Text>
                ))}
              </View>
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity
          onPress={handleFinish}
          disabled={loading}
          className="mt-8 rounded-xl bg-teal py-4 items-center"
          style={{ opacity: loading ? 0.5 : 1 }}
        >
          {loading
            ? <ActivityIndicator color="#fff" />
            : <Text className="text-white font-semibold text-base">
                {selected.size === 0 ? 'Skip for now →' : `Start with ${selected.size} pack${selected.size > 1 ? 's' : ''} →`}
              </Text>
          }
        </TouchableOpacity>

        <View className="h-8" />
      </ScrollView>
    </View>
  )
}
