import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  ActivityIndicator, RefreshControl, Alert,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { supabase } from '@/lib/supabase'
import { todayString, formatDate } from '@/lib/utils'
import type { PrayerLogRow, QuranLogRow } from '@/types/database'

// 0 = missed, 1 = prayed alone, 2 = prayed in jamat (per prayer_jamat.sql)
const PRAYERS = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'] as const
type PrayerKey = typeof PRAYERS[number]

const PRAYER_META: Record<PrayerKey, { label: string; icon: string }> = {
  fajr:    { label: 'Fajr',    icon: '🌅' },
  dhuhr:   { label: 'Dhuhr',   icon: '☀️'  },
  asr:     { label: 'Asr',     icon: '🌤️'  },
  maghrib: { label: 'Maghrib', icon: '🌇' },
  isha:    { label: 'Isha',    icon: '🌙' },
}

function prayerColor(val: number): string {
  if (val === 2) return '#C9A227'   // gold — jamat
  if (val === 1) return '#34d399'   // green — alone
  return 'transparent'
}
function prayerBorderColor(val: number): string {
  if (val === 2) return '#C9A227'
  if (val === 1) return '#34d399'
  return 'rgba(255,255,255,0.2)'
}
function prayerLabel(val: number): string {
  if (val === 2) return 'Jamaʿat ✓'
  if (val === 1) return 'Prayed ✓'
  return 'Tap'
}

// cycle 0 → 1 → 2 → 0
function nextPrayer(val: number): number {
  return (val + 1) % 3
}

export default function DeenScreen() {
  const insets = useSafeAreaInsets()
  const today = todayString()
  const [prayerLog, setPrayerLog] = useState<PrayerLogRow | null>(null)
  const [quranLog, setQuranLog] = useState<QuranLogRow | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [savingQuran, setSavingQuran] = useState(false)

  const [pages, setPages] = useState('')
  const [surahName, setSurahName] = useState('')
  const [notes, setNotes] = useState('')

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const [{ data: pl }, { data: ql }] = await Promise.all([
      supabase.from('prayer_logs').select('*').eq('user_id', user.id).eq('date', today).maybeSingle(),
      supabase.from('quran_log').select('*').eq('user_id', user.id).eq('date', today).maybeSingle(),
    ])
    setPrayerLog(pl as PrayerLogRow ?? null)
    const q = ql as QuranLogRow | null
    setQuranLog(q)
    if (q) { setPages(q.pages_read?.toString() ?? ''); setSurahName(q.surah ?? ''); setNotes(q.notes ?? '') }
  }, [today])

  useEffect(() => { load().finally(() => setLoading(false)) }, [load])
  const onRefresh = useCallback(async () => { setRefreshing(true); await load(); setRefreshing(false) }, [load])

  async function tapPrayer(prayer: PrayerKey) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const current = prayerLog ? (prayerLog[prayer] as number) : 0
    const next = nextPrayer(current)
    const update = { [prayer]: next }

    if (prayerLog) {
      await supabase.from('prayer_logs').update(update).eq('id', prayerLog.id)
      setPrayerLog(prev => prev ? { ...prev, ...update } : null)
    } else {
      const base = PRAYERS.reduce<Record<string, number>>((acc, p) => ({ ...acc, [p]: 0 }), {})
      const { data } = await supabase.from('prayer_logs').insert({
        user_id: user.id, date: today, extra_prayers: [], ...base, ...update,
      }).select().single()
      if (data) setPrayerLog(data as PrayerLogRow)
    }
  }

  async function saveQuran() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user || !pages) return
    setSavingQuran(true)
    try {
      const payload = {
        user_id: user.id, date: today,
        pages_read: parseInt(pages) || 0,
        surah: surahName || null,
        notes: notes || null,
      }
      if (quranLog) {
        await supabase.from('quran_log').update(payload).eq('id', quranLog.id)
      } else {
        const { data } = await supabase.from('quran_log').insert(payload).select().single()
        if (data) setQuranLog(data as QuranLogRow)
      }
      Alert.alert('Saved', 'Quran log updated.')
    } finally {
      setSavingQuran(false)
    }
  }

  // score: sum of all prayer values (max = 5×2 = 10)
  const prayerScore = prayerLog
    ? PRAYERS.reduce((s, p) => s + ((prayerLog[p] as number) || 0), 0)
    : 0

  if (loading) {
    return <View className="flex-1 bg-navy items-center justify-center"><ActivityIndicator color="#C9A227" /></View>
  }

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <View>
          <Text className="text-xl font-bold text-white">Deen</Text>
          <Text className="text-xs text-muted-fg mt-0.5">{formatDate(today)}</Text>
        </View>
        <View className="items-end">
          <Text className="text-2xl font-bold text-gold">{prayerScore}/10</Text>
          <Text className="text-[10px] text-muted-fg">prayer score</Text>
        </View>
      </View>

      <ScrollView
        className="flex-1 px-4"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#C9A227" />}
      >
        {/* Prayers */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-4">
          <View className="flex-row items-center justify-between mb-1">
            <Text className="text-sm font-semibold text-white">Prayers</Text>
            <Text className="text-xs text-muted-fg">Tap: missed → alone → jamat</Text>
          </View>

          {/* Legend */}
          <View className="flex-row gap-x-4 mb-4">
            <View className="flex-row items-center gap-x-1.5">
              <View className="h-3 w-3 rounded-full border border-white/20" />
              <Text className="text-[10px] text-muted-fg">Missed</Text>
            </View>
            <View className="flex-row items-center gap-x-1.5">
              <View className="h-3 w-3 rounded-full bg-emerald-400" />
              <Text className="text-[10px] text-muted-fg">Alone</Text>
            </View>
            <View className="flex-row items-center gap-x-1.5">
              <View className="h-3 w-3 rounded-full bg-gold" />
              <Text className="text-[10px] text-muted-fg">Jamaʿat</Text>
            </View>
          </View>

          <View className="gap-y-3">
            {PRAYERS.map(p => {
              const val = prayerLog ? ((prayerLog[p] as number) || 0) : 0
              return (
                <TouchableOpacity
                  key={p}
                  onPress={() => tapPrayer(p)}
                  className="flex-row items-center justify-between rounded-xl px-4 py-3 border"
                  style={{
                    borderColor: prayerBorderColor(val),
                    backgroundColor: val > 0 ? prayerColor(val) + '18' : 'rgba(255,255,255,0.03)',
                  }}
                >
                  <View className="flex-row items-center gap-x-3">
                    <Text className="text-xl">{PRAYER_META[p].icon}</Text>
                    <Text className="text-sm font-medium text-white">{PRAYER_META[p].label}</Text>
                  </View>
                  <View className="flex-row items-center gap-x-2">
                    <Text className="text-xs font-semibold" style={{ color: val > 0 ? prayerColor(val) : '#6B8CA8' }}>
                      {prayerLabel(val)}
                    </Text>
                    <View
                      className="h-6 w-6 rounded-full border-2 items-center justify-center"
                      style={{ borderColor: prayerBorderColor(val), backgroundColor: val > 0 ? prayerColor(val) : 'transparent' }}
                    >
                      {val > 0 && <Text className="text-white text-xs font-bold">{val === 2 ? '★' : '✓'}</Text>}
                    </View>
                  </View>
                </TouchableOpacity>
              )
            })}
          </View>
        </View>

        {/* Quran */}
        <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-8">
          <Text className="text-sm font-semibold text-white mb-4">📖 Quran</Text>
          <View className="gap-y-4">
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Pages read today</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="0"
                placeholderTextColor="#6B8CA8"
                value={pages}
                onChangeText={setPages}
                keyboardType="numeric"
              />
            </View>
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Surah / Section</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="e.g. Al-Baqarah"
                placeholderTextColor="#6B8CA8"
                value={surahName}
                onChangeText={setSurahName}
              />
            </View>
            <View>
              <Text className="text-xs text-muted-fg uppercase tracking-wider mb-1.5">Notes / reflection</Text>
              <TextInput
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                placeholder="What stood out today…"
                placeholderTextColor="#6B8CA8"
                value={notes}
                onChangeText={setNotes}
                multiline
                numberOfLines={3}
                style={{ minHeight: 80, textAlignVertical: 'top' }}
              />
            </View>
            <TouchableOpacity
              onPress={saveQuran}
              disabled={savingQuran || !pages}
              className="rounded-xl bg-teal py-3 items-center"
              style={{ opacity: savingQuran || !pages ? 0.5 : 1 }}
            >
              {savingQuran
                ? <ActivityIndicator color="#fff" />
                : <Text className="text-white font-semibold">Save Quran Log</Text>
              }
            </TouchableOpacity>
          </View>
        </View>
      </ScrollView>
    </View>
  )
}
