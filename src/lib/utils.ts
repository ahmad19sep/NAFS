import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDate(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
}

export function formatDateShort(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

/**
 * Today's date as YYYY-MM-DD in the runtime's local timezone.
 *
 * Browser → user's local time (correct).
 * Node on Vercel → UTC unless TZ env var is set. For per-user accuracy in
 * production, set `TZ=Asia/Karachi` (or your users' timezone) in Vercel project
 * settings → Environment Variables. For multi-user apps with mixed timezones,
 * pass the user's stored `users.timezone` and use `todayInTZ(tz)` instead.
 */
export function todayString(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** Today's date as YYYY-MM-DD in a specific IANA timezone. */
export function todayInTZ(tz: string): string {
  try {
    const fmt = new Intl.DateTimeFormat('en-CA', {
      timeZone: tz,
      year: 'numeric', month: '2-digit', day: '2-digit',
    })
    const parts = fmt.formatToParts(new Date())
    const m: Record<string, string> = {}
    for (const p of parts) m[p.type] = p.value
    return `${m.year}-${m.month}-${m.day}`
  } catch {
    return todayString()
  }
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

export function formatPullDays(days: number): string {
  const abs = Math.abs(days).toFixed(1)
  return days >= 0 ? `+${abs} days closer` : `${days.toFixed(1)} days further`
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

export function timeAgo(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return 'Today'
  if (diffDays === 1) return 'Yesterday'
  if (diffDays < 7) return `${diffDays} days ago`
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`
  return `${Math.floor(diffDays / 30)} months ago`
}

export function clampScore(score: number): number {
  return Math.min(100, Math.max(0, Math.round(score)))
}

export function scoreColor(score: number): string {
  if (score >= 80) return 'text-emerald-400'
  if (score >= 60) return 'text-yellow-400'
  if (score >= 40) return 'text-orange-400'
  return 'text-red-400'
}

export function scoreGlow(score: number): string {
  if (score >= 80) return 'shadow-[0_0_24px_rgba(52,211,153,0.35)]'
  if (score >= 60) return 'shadow-[0_0_24px_rgba(250,204,21,0.35)]'
  if (score >= 40) return 'shadow-[0_0_24px_rgba(251,146,60,0.35)]'
  return 'shadow-[0_0_24px_rgba(248,113,113,0.35)]'
}
