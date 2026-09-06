import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { SleepSession } from '@/types/database'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function todayString(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function todayInTZ(tz: string): string {
  try {
    const fmt = new Intl.DateTimeFormat('en-CA', {
      timeZone: tz,
      year: 'numeric', month: '2-digit', day: '2-digit',
    })
    const parts = fmt.formatToParts(new Date())
    const map: Record<string, string> = {}
    for (const p of parts) map[p.type] = p.value
    return `${map.year}-${map.month}-${map.day}`
  } catch {
    return todayString()
  }
}

export function formatDate(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
}

export function formatDateShort(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

export function daysUntil(dateStr: string): number {
  const target = new Date(dateStr)
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  target.setHours(0, 0, 0, 0)
  return Math.ceil((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
}

export function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  return d.toISOString().split('T')[0]
}

export function timeAgo(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return 'Today'
  if (diffDays === 1) return 'Yesterday'
  if (diffDays < 7) return `${diffDays}d ago`
  if (diffDays < 30) return `${Math.floor(diffDays / 7)}w ago`
  return `${Math.floor(diffDays / 30)}mo ago`
}

export function clampScore(score: number): number {
  return Math.min(100, Math.max(0, Math.round(score)))
}

export function getStreakEmoji(streak: number): string {
  if (streak >= 30) return '🔥'
  if (streak >= 14) return '⚡'
  if (streak >= 7) return '✨'
  if (streak >= 3) return '🌱'
  return '🌙'
}

export function getMoodEmoji(mood: number): string {
  const emojis = ['😞', '😟', '😐', '🙂', '😊', '😄', '🤩', '💪', '🔥', '⚡']
  return emojis[Math.min(Math.max(Math.round(mood) - 1, 0), 9)]
}

// Accepts "HH:MM" or the "HH:MM:SS" that Postgres TIME columns return.
export function parseTimeToMinutes(time: string): number | null {
  const m = /^(\d{1,2}):(\d{2})/.exec(time.trim())
  if (!m) return null
  const hours = Number(m[1])
  const mins = Number(m[2])
  if (hours > 23 || mins > 59) return null
  return hours * 60 + mins
}

// Null when either time is unparseable. An end at or before the start is read as
// crossing midnight, so 23:00 → 06:30 is 7h 30m rather than negative.
export function sleepSessionMinutes(start: string, end: string): number | null {
  const from = parseTimeToMinutes(start)
  const to = parseTimeToMinutes(end)
  if (from == null || to == null) return null
  if (to === from) return 0
  return to > from ? to - from : to + 1440 - from
}

export function totalSleepMinutes(sessions: SleepSession[]): number {
  return sessions.reduce((sum, s) => sum + (sleepSessionMinutes(s.start, s.end) ?? 0), 0)
}

export function formatDuration(mins: number): string {
  const h = Math.floor(mins / 60)
  const m = mins % 60
  if (!h) return `${m}m`
  if (!m) return `${h}h`
  return `${h}h ${m}m`
}

// Monday of the ISO week containing dateStr (YYYY-MM-DD)
export function isoWeekMonday(dateStr: string): string {
  const d = new Date(dateStr)
  const day = d.getDay() // 0=Sun
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return d.toISOString().split('T')[0]
}

// First day of month for a given YYYY-MM-DD
export function monthStart(dateStr: string): string {
  return dateStr.slice(0, 7) + '-01'
}
