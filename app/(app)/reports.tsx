import { useState, useEffect, useCallback } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity,
  ActivityIndicator, RefreshControl, Alert, Platform,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { ChevronLeft, ChevronRight, Printer, Share2, FileText } from 'lucide-react-native'
import * as Print from 'expo-print'
import * as Sharing from 'expo-sharing'
import * as FileSystem from 'expo-file-system'
import { buildReport, prettyDate, type ReportData, type ReportPeriod } from '@/lib/report'
import { reportToHtml, reportFileName } from '@/lib/report-html'

const GOLD = '#C9A227'

function scoreColor(score: number): string {
  if (score >= 80) return '#34d399'
  if (score >= 60) return '#fbbf24'
  if (score >= 40) return '#fb923c'
  return '#f87171'
}

// ─── Small building blocks ────────────────────────────────────────────────────

function Card({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <View className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-3">
      <View className="flex-row items-center justify-between mb-3">
        <Text className="text-sm font-semibold text-white">{title}</Text>
        {note && <Text className="text-[10px] text-muted-fg">{note}</Text>}
      </View>
      {children}
    </View>
  )
}

function Kpi({ label, value, sub, color }: { label: string; value: string; sub?: string; color?: string }) {
  return (
    <View className="w-[31%] rounded-2xl border border-white/10 bg-white/5 p-3 items-center mb-3">
      <Text className="text-xl font-bold" style={{ color: color ?? '#fff' }}>{value}</Text>
      <Text className="text-[10px] text-muted-fg mt-0.5 text-center">{label}</Text>
      {sub ? <Text className="text-[9px] text-muted-fg/60 mt-0.5 text-center">{sub}</Text> : null}
    </View>
  )
}

function Meter({ label, right, pct, color }: { label: string; right: string; pct: number; color: string }) {
  return (
    <View className="mb-2.5">
      <View className="flex-row items-center justify-between mb-1">
        <Text className="text-xs text-white flex-1 pr-2" numberOfLines={1}>{label}</Text>
        <Text className="text-[10px] text-muted-fg">{right}</Text>
      </View>
      <View className="h-1.5 rounded-full bg-white/10">
        <View className="h-full rounded-full" style={{ width: `${Math.max(0, Math.min(100, pct))}%`, backgroundColor: color }} />
      </View>
    </View>
  )
}

function ScoreChart({ data }: { data: ReportData }) {
  const H = 90
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false}>
      <View className="flex-row items-end gap-x-1">
        {data.days.map(d => (
          <View key={d.date} style={{ width: data.days.length > 14 ? 14 : 30 }} className="items-center">
            <View style={{ height: H, justifyContent: 'flex-end', width: '100%' }}>
              <View
                style={{
                  height: Math.max(2, (d.score / 100) * H),
                  backgroundColor: d.logged ? scoreColor(d.score) : 'rgba(255,255,255,0.12)',
                  borderRadius: 3,
                }}
              />
            </View>
            <Text className="text-[8px] text-muted-fg mt-1">
              {data.days.length > 14 ? prettyDate(d.date, { day: 'numeric' }) : d.weekday}
            </Text>
          </View>
        ))}
      </View>
    </ScrollView>
  )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function ReportsScreen() {
  const insets = useSafeAreaInsets()
  const [period, setPeriod] = useState<ReportPeriod>('weekly')
  const [offset, setOffset] = useState(0)
  const [data, setData] = useState<ReportData | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [busy, setBusy] = useState<'print' | 'share' | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await buildReport(period, offset))
    } catch (e: any) {
      Alert.alert('Could not build report', e?.message ?? 'Please try again.')
    }
  }, [period, offset])

  useEffect(() => {
    setLoading(true)
    load().finally(() => setLoading(false))
  }, [load])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await load()
    setRefreshing(false)
  }, [load])

  function switchPeriod(p: ReportPeriod) {
    setPeriod(p)
    setOffset(0)
  }

  async function print() {
    if (!data) return
    setBusy('print')
    try {
      await Print.printAsync({ html: reportToHtml(data) })
    } catch (e: any) {
      // the user dismissing the native print sheet also lands here on iOS
      if (!/cancel|dismiss/i.test(e?.message ?? '')) {
        Alert.alert('Print failed', e?.message ?? 'Please try again.')
      }
    } finally {
      setBusy(null)
    }
  }

  async function sharePdf() {
    if (!data) return
    setBusy('share')
    try {
      const { uri } = await Print.printToFileAsync({ html: reportToHtml(data) })

      // printToFileAsync writes to a random filename — rename it so the saved
      // PDF is recognisable in the user's files/WhatsApp/email.
      let target = uri
      const dir = FileSystem.cacheDirectory
      if (dir) {
        const named = dir + reportFileName(data)
        try {
          await FileSystem.deleteAsync(named, { idempotent: true })
          await FileSystem.moveAsync({ from: uri, to: named })
          target = named
        } catch {
          target = uri
        }
      }

      if (!(await Sharing.isAvailableAsync())) {
        Alert.alert('Saved', `Report saved to:\n${target}`)
        return
      }
      await Sharing.shareAsync(target, {
        mimeType: 'application/pdf',
        UTI: 'com.adobe.pdf',
        dialogTitle: `NAFS ${period === 'weekly' ? 'weekly' : 'monthly'} report`,
      })
    } catch (e: any) {
      Alert.alert('Export failed', e?.message ?? 'Please try again.')
    } finally {
      setBusy(null)
    }
  }

  const unit = period === 'weekly' ? 'week' : 'month'

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      {/* Header */}
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <View>
          <Text className="text-xl font-bold text-white">Reports</Text>
          <Text className="text-[11px] text-muted-fg">Detailed progress you can print</Text>
        </View>
        <FileText size={20} color={GOLD} />
      </View>

      {/* Weekly / Monthly */}
      <View className="flex-row mx-4 rounded-xl border border-white/10 bg-white/5 p-1 mb-3">
        {(['weekly', 'monthly'] as ReportPeriod[]).map(p => (
          <TouchableOpacity
            key={p}
            onPress={() => switchPeriod(p)}
            className={`flex-1 rounded-lg py-2 items-center ${period === p ? 'bg-teal' : ''}`}
          >
            <Text className={`text-sm font-semibold capitalize ${period === p ? 'text-white' : 'text-muted-fg'}`}>
              {p}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Period stepper */}
      <View className="flex-row items-center justify-between mx-4 mb-3">
        <TouchableOpacity
          onPress={() => setOffset(o => o + 1)}
          className="h-9 w-9 rounded-full border border-white/10 bg-white/5 items-center justify-center"
        >
          <ChevronLeft size={18} color="#6B8CA8" />
        </TouchableOpacity>

        <View className="items-center">
          <Text className="text-sm font-semibold text-white">{data?.label ?? '—'}</Text>
          <Text className="text-[10px] text-muted-fg">
            {offset === 0 ? `This ${unit}` : offset === 1 ? `Last ${unit}` : `${offset} ${unit}s ago`}
          </Text>
        </View>

        <TouchableOpacity
          onPress={() => setOffset(o => Math.max(0, o - 1))}
          disabled={offset === 0}
          className="h-9 w-9 rounded-full border border-white/10 bg-white/5 items-center justify-center"
          style={{ opacity: offset === 0 ? 0.3 : 1 }}
        >
          <ChevronRight size={18} color="#6B8CA8" />
        </TouchableOpacity>
      </View>

      {loading || !data ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color={GOLD} />
        </View>
      ) : (
        <ScrollView
          className="flex-1 px-4"
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={GOLD} />}
        >
          {/* KPIs */}
          <View className="flex-row flex-wrap justify-between">
            <Kpi label="Avg score" value={`${data.avg_score}%`} color={scoreColor(data.avg_score)}
                 sub={data.score_delta == null ? undefined
                   : data.score_delta === 0 ? 'same as last'
                   : `${data.score_delta > 0 ? '▲' : '▼'} ${Math.abs(data.score_delta)} pts`} />
            <Kpi label="Days logged" value={`${data.days_logged}/${data.days_elapsed}`}
                 sub={`${Math.round((data.days_logged / Math.max(data.days_elapsed, 1)) * 100)}% consistent`} />
            <Kpi label="Salah" value={`${data.prayers_prayed}/${data.prayers_possible}`}
                 sub={`${data.prayers_jamat} jamaat`} color="#34d399" />
            <Kpi label="Habits" value={`${data.habit_completion_pct}%`} sub={`${data.habits.length} tracked`} color={GOLD} />
            <Kpi label="Tasks" value={`${data.tasks_completed}/${data.tasks_total}`} sub={`${data.tasks_pct}% done`} color="#60a5fa" />
            <Kpi label="Best streak" value={`${data.best_streak}d`} sub="at 60%+" color="#a78bfa" />
          </View>

          {/* Daily scores */}
          {data.days.length > 0 && (
            <Card title="Daily score" note={`${data.strong_days} strong · ${data.weak_days} weak`}>
              <ScoreChart data={data} />
              {data.best_day && data.worst_day && (
                <Text className="text-[10px] text-muted-fg mt-2">
                  Best {data.best_day.weekday} {prettyDate(data.best_day.date)} ({data.best_day.score}%) ·
                  {' '}Toughest {data.worst_day.weekday} {prettyDate(data.worst_day.date)} ({data.worst_day.score}%)
                </Text>
              )}
            </Card>
          )}

          {/* Insights */}
          <Card title="What the numbers say">
            {data.insights.map((line, i) => (
              <View key={i} className="flex-row gap-x-2 mb-2">
                <Text className="text-gold text-xs">•</Text>
                <Text className="flex-1 text-xs text-white/85 leading-5">{line}</Text>
              </View>
            ))}
          </Card>

          {/* Salah */}
          {data.prayers_possible > 0 && (
            <Card title="Salah" note={`${data.prayer_points}/${data.prayer_points_max} quality pts`}>
              {data.prayer_breakdown.map(p => (
                <Meter
                  key={p.key}
                  label={p.label}
                  right={`${p.prayed}/${data.days_elapsed}${p.jamat ? ` · ${p.jamat} jamaat` : ''}`}
                  pct={p.pct}
                  color={scoreColor(p.pct)}
                />
              ))}
              {(data.quran_pages > 0 || data.extra_prayers > 0) && (
                <Text className="text-[10px] text-muted-fg mt-1">
                  {data.quran_pages > 0 && `Quran ${data.quran_pages} pages over ${data.quran_days} days`}
                  {data.quran_pages > 0 && data.extra_prayers > 0 && ' · '}
                  {data.extra_prayers > 0 && `${data.extra_prayers} extra prayers`}
                </Text>
              )}
            </Card>
          )}

          {/* Habits */}
          {data.habits.length > 0 && (
            <Card title="Habits" note={`${data.habit_completion_pct}% overall`}>
              {data.habits.map(h => (
                <Meter
                  key={h.id}
                  label={`${h.emoji} ${h.name}`}
                  right={`${h.done_days}/${h.eligible_days} · best run ${h.best_run}d`}
                  pct={h.pct}
                  color={scoreColor(h.pct)}
                />
              ))}
            </Card>
          )}

          {/* Tasks */}
          {data.tasks_total > 0 && (
            <Card title="Tasks" note={`${data.tasks_pct}% completed`}>
              {data.tasks.map(t => (
                <Meter
                  key={t.type}
                  label={t.type.charAt(0).toUpperCase() + t.type.slice(1)}
                  right={`${t.completed}/${t.total}`}
                  pct={t.pct}
                  color="#60a5fa"
                />
              ))}
              {data.missed_tasks.length > 0 && (
                <View className="mt-2 pt-2 border-t border-white/10">
                  <Text className="text-[10px] text-muted-fg mb-1.5">
                    Unfinished ({data.missed_tasks.length})
                  </Text>
                  {data.missed_tasks.slice(0, 6).map((t, i) => (
                    <Text key={i} className="text-[11px] text-white/70 mb-1" numberOfLines={1}>
                      • {t.title} <Text className="text-muted-fg">— {prettyDate(t.date)}</Text>
                    </Text>
                  ))}
                  {data.missed_tasks.length > 6 && (
                    <Text className="text-[10px] text-muted-fg">+{data.missed_tasks.length - 6} more in the PDF</Text>
                  )}
                </View>
              )}
            </Card>
          )}

          {/* Challenges */}
          {data.challenges.length > 0 && (
            <Card title="Challenges">
              {data.challenges.map(c => (
                <Meter
                  key={c.id}
                  label={`${c.emoji} ${c.title}`}
                  right={`${c.checkins}/${c.possible_days} · day ${c.day_of}/${c.duration_days}`}
                  pct={c.pct}
                  color="#34d399"
                />
              ))}
            </Card>
          )}

          {/* Goals */}
          {data.goals.length > 0 && (
            <Card title="Goals">
              {data.goals.map(g => (
                <Meter
                  key={g.id}
                  label={`${g.emoji} ${g.title}`}
                  right={g.milestones_total
                    ? `${g.progress_pct}% · ${g.milestones_done}/${g.milestones_total} milestones`
                    : `${g.progress_pct}%`}
                  pct={g.progress_pct}
                  color={GOLD}
                />
              ))}
            </Card>
          )}

          {/* Health */}
          {data.health.days_logged > 0 && (
            <Card title="Health" note={`${data.health.days_logged} days logged`}>
              <View className="flex-row flex-wrap justify-between">
                <Kpi label="Avg sleep" value={data.health.avg_sleep == null ? '—' : `${data.health.avg_sleep}h`} />
                <Kpi label="Avg steps" value={data.health.avg_steps == null ? '—' : Math.round(data.health.avg_steps).toLocaleString()} />
                <Kpi label="Exercise" value={`${data.health.exercise_days}d`} sub={`${data.health.exercise_minutes} min`} />
                <Kpi label="Avg water" value={data.health.avg_water == null ? '—' : `${data.health.avg_water}`} sub="glasses/day" />
                <Kpi label="Avg mood" value={data.health.avg_mood == null ? '—' : `${data.health.avg_mood}/10`} />
                <Kpi
                  label="Weight"
                  value={data.health.weight_end == null ? '—' : `${data.health.weight_end}kg`}
                  sub={data.health.weight_change == null ? undefined
                    : `${data.health.weight_change > 0 ? '+' : ''}${data.health.weight_change} kg`}
                />
              </View>
            </Card>
          )}

          {/* Weekday pattern */}
          {data.weekday_avgs.length >= 2 && (
            <Card title="Weekday pattern">
              {data.weekday_avgs.map(w => (
                <Meter key={w.weekday} label={w.weekday} right={`${w.avg}%`} pct={w.avg} color={scoreColor(w.avg)} />
              ))}
            </Card>
          )}

          {/* Reflections */}
          {data.reflections.length > 0 && (
            <Card title="Your reflections" note={`${data.reflections.length} entries`}>
              {data.reflections.slice(0, 5).map(r => (
                <View key={r.date} className="mb-3 pl-3 border-l-2 border-white/15">
                  <Text className="text-[10px] font-semibold text-teal-light uppercase">
                    {prettyDate(r.date, { weekday: 'short', day: 'numeric', month: 'short' })}
                  </Text>
                  <Text className="text-xs text-white/80 mt-0.5 leading-5">{r.text}</Text>
                </View>
              ))}
              {data.reflections.length > 5 && (
                <Text className="text-[10px] text-muted-fg">
                  +{data.reflections.length - 5} more in the PDF
                </Text>
              )}
            </Card>
          )}

          {/* Empty state */}
          {data.days_logged === 0 && (
            <View className="rounded-2xl border border-dashed border-white/20 p-8 items-center mb-3">
              <Text className="text-4xl mb-3">📄</Text>
              <Text className="text-base font-semibold text-white text-center">Nothing logged yet</Text>
              <Text className="text-sm text-muted-fg text-center mt-2">
                Log prayers, habits or tasks and this {unit}'s report will fill in.
              </Text>
            </View>
          )}

          <Text className="text-[10px] text-muted-fg text-center mb-2">
            The PDF includes the full day-by-day log and every reflection.
          </Text>

          <View className="h-32" />
        </ScrollView>
      )}

      {/* Print / share bar */}
      {!loading && data && (
        <View
          className="absolute left-0 right-0 flex-row gap-x-3 px-4 pt-3 bg-card border-t border-white/10"
          style={{ bottom: 0, paddingBottom: 12 }}
        >
          <TouchableOpacity
            onPress={print}
            disabled={busy !== null}
            className="flex-1 flex-row items-center justify-center gap-x-2 rounded-xl bg-gold py-3"
            style={{ opacity: busy ? 0.6 : 1 }}
          >
            {busy === 'print'
              ? <ActivityIndicator size="small" color="#0B1A2B" />
              : <Printer size={18} color="#0B1A2B" />}
            <Text className="text-sm font-bold text-navy">Print</Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={sharePdf}
            disabled={busy !== null}
            className="flex-1 flex-row items-center justify-center gap-x-2 rounded-xl border border-white/15 bg-white/5 py-3"
            style={{ opacity: busy ? 0.6 : 1 }}
          >
            {busy === 'share'
              ? <ActivityIndicator size="small" color={GOLD} />
              : <Share2 size={18} color={GOLD} />}
            <Text className="text-sm font-bold text-white">
              {Platform.OS === 'web' ? 'Download PDF' : 'Save / Share PDF'}
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  )
}
