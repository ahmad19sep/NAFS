'use client'

import { useState } from 'react'
import Link from 'next/link'
import {
  ChevronLeft, ChevronRight, Printer, ExternalLink, FileText,
  Sparkles, Loader2, RefreshCw,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  prettyDate,
  type ReportData, type ReportPeriod, type AreaComparison, type Severity,
} from '@/lib/report'
import { reportToHtml, reportFileName, type ReportReview } from '@/lib/report-html'
import RichText from '@/components/RichText'

// Roles validated against the navy card surface (#132B41):
//   now  #6EC5D8  7.34:1   teal, this period
//   prev #2C6E7F  2.51:1   same hue, dark step (ordinal floor 2:1)
//   accent #C9A227 5.99:1  emphasis, always directly labelled
//   up   #3fca3f  6.71:1   down #f08a8a  6.01:1   (delta text)
const NOW = '#6EC5D8'
const PREV = '#2C6E7F'
const ACCENT = '#C9A227'
const TRACK = 'rgba(255,255,255,0.10)'

const SEVERITY_ICON: Record<Severity, string> = { critical: '●', serious: '▲', warning: '■' }
const SEVERITY_CHIP: Record<Severity, string> = {
  critical: 'bg-[#d03b3b]/20 text-red-100',
  serious: 'bg-[#ec835a]/20 text-orange-100',
  warning: 'bg-[#fab219]/20 text-amber-100',
}
const SEVERITY_EDGE: Record<Severity, string> = {
  critical: 'border-l-[#d03b3b]',
  serious: 'border-l-[#ec835a]',
  warning: 'border-l-[#fab219]',
}

function clamp(n: number) { return Math.max(0, Math.min(100, n)) }

// ─── Pieces ───────────────────────────────────────────────────────────────────

function Card({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <div className="nafs-card p-4">
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">{title}</h2>
        {note && <span className="flex-shrink-0 text-[10px] text-muted-foreground">{note}</span>}
      </div>
      {children}
    </div>
  )
}

/** Signed change — arrow plus sign, so direction never rests on colour alone. */
function Delta({ value, unit = 'pts' }: { value: number | null; unit?: string }) {
  if (value == null) return <span className="text-muted-foreground">—</span>
  if (value === 0) return <span className="text-muted-foreground tabular-nums">▬ 0</span>
  const up = value > 0
  return (
    <span
      className="whitespace-nowrap font-semibold tabular-nums"
      style={{ color: up ? '#3fca3f' : '#f08a8a' }}
    >
      {up ? '▲' : '▼'} {up ? '+' : ''}{value}{unit ? ` ${unit}` : ''}
    </span>
  )
}

/** One ratio against a limit. One hue — never a value ramp. */
function Meter({ label, right, pct }: { label: string; right: string; pct: number }) {
  return (
    <div className="mb-2.5">
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="truncate text-xs text-foreground">{label}</span>
        <span className="flex-shrink-0 text-[10px] tabular-nums text-muted-foreground">{right}</span>
      </div>
      <div className="h-1.5 rounded-full" style={{ background: TRACK }}>
        <div className="h-full rounded-full" style={{ width: `${clamp(pct)}%`, background: NOW }} />
      </div>
    </div>
  )
}

/** Before → after for one area. Two shades of one hue, 2px surface ring on the dots. */
function Dumbbell({ row }: { row: AreaComparison }) {
  const now = row.current ?? 0
  const prev = row.previous
  const lo = prev == null ? now : Math.min(now, prev)
  const hi = prev == null ? now : Math.max(now, prev)
  return (
    <div className="relative h-3 w-full">
      <div className="absolute left-0 right-0 top-[5px] h-0.5 rounded" style={{ background: TRACK }} />
      {prev != null && (
        <div
          className="absolute top-[5px] h-0.5 rounded"
          style={{ left: `${clamp(lo)}%`, width: `${clamp(hi - lo)}%`, background: PREV }}
        />
      )}
      {prev != null && (
        <span
          className="absolute top-0.5 -ml-1 h-2 w-2 rounded-full ring-2 ring-[#0B1A2B]"
          style={{ left: `${clamp(prev)}%`, background: PREV }}
        />
      )}
      <span
        className="absolute top-0.5 -ml-1 h-2 w-2 rounded-full ring-2 ring-[#0B1A2B]"
        style={{ left: `${clamp(now)}%`, background: NOW }}
      />
    </div>
  )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

interface Props {
  report: ReportData
  /** The coach's written read of this period, if one has been written. */
  initialReview: ReportReview | null
  /** Which model writes it right now. From the server, so it is true. */
  deepModel: 'anthropic' | 'cloudflare'
}

export default function ReportsClient({ report: d, initialReview, deepModel }: Props) {
  const [printing, setPrinting] = useState(false)
  const unit = d.period === 'weekly' ? 'week' : 'month'

  // The read travels into the printout, so it has to live here rather than
  // inside the card — print() reads it at the moment the user prints.
  const [review, setReview] = useState<ReportReview | null>(initialReview)
  const [writing, setWriting] = useState(false)
  const [reviewNote, setReviewNote] = useState<string | null>(null)
  const [reviewError, setReviewError] = useState<string | null>(null)

  async function writeReview() {
    if (writing) return
    setWriting(true); setReviewError(null); setReviewNote(null)
    try {
      const res = await fetch('/api/ai/report-review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ period: d.period, offset: d.offset }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data?.review) throw new Error(data?.error || 'The read could not be written right now.')
      setReview(data.review)
      const notes: string[] = []
      if (data.fellBack) {
        notes.push(`Written by the free model — Claude did not answer. ${data.fellBackReason ?? ''}`.trim())
      }
      if (data.saved === false && data.hint) notes.push(data.hint)
      if (notes.length) setReviewNote(notes.join(' '))
    } catch (e: any) {
      setReviewError(e?.message || 'The read could not be written right now.')
    } finally {
      setWriting(false)
    }
  }

  function href(period: ReportPeriod, offset: number) {
    return `/reports?period=${period}&offset=${Math.max(0, offset)}`
  }

  /** Render the printable document into a hidden iframe and open the print dialog. */
  function print() {
    setPrinting(true)
    const frame = document.createElement('iframe')
    frame.setAttribute('aria-hidden', 'true')
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden'
    // onload before srcdoc, and before the frame is in the DOM, so the event
    // can never fire ahead of the handler being attached
    frame.onload = () => {
      try {
        frame.contentWindow?.focus()
        frame.contentWindow?.print()
      } catch {
        openPrintable()
      } finally {
        setPrinting(false)
        setTimeout(() => frame.remove(), 60_000)
      }
    }
    frame.srcdoc = reportToHtml(d, { review })
    document.body.appendChild(frame)
    // never leave the button stuck on "Preparing…" if load never fires
    setTimeout(() => setPrinting(false), 8_000)
  }

  /**
   * Fallback for webviews where window.print() is a no-op, and for iOS
   * standalone PWAs where printing an iframe is unreliable: open the report as
   * its own page, where the system Share → Print always works.
   */
  function openPrintable() {
    const blob = new Blob([reportToHtml(d, { autoPrint: true, review })], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const w = window.open(url, '_blank')
    if (!w) {
      const a = document.createElement('a')
      a.href = url
      a.download = `${reportFileName(d)}.html`
      a.click()
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }

  const pctRows = d.comparison.filter((c) => c.unit === '%')
  const countRows = d.comparison.filter((c) => c.unit !== '%')

  return (
    <div className="mx-auto max-w-md space-y-4 px-4 pb-32">
      {/* Header */}
      <div className="flex items-center gap-3 pt-3">
        <Link href="/dashboard"
          className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/5 transition-colors hover:bg-white/10">
          <ChevronLeft size={16} className="text-muted-foreground" />
        </Link>
        <div className="flex-1">
          <p className="text-xs text-muted-foreground">Detailed progress you can print</p>
          <h1 className="text-2xl font-bold text-foreground">Reports</h1>
        </div>
        <FileText size={20} className="text-gold" />
      </div>

      {/* Weekly / monthly */}
      <div className="grid grid-cols-2 gap-2">
        {(['weekly', 'monthly'] as ReportPeriod[]).map((p) => (
          <Link key={p} href={href(p, 0)} scroll={false}
            className={cn('rounded-xl border py-2 text-center text-sm font-semibold capitalize transition-all',
              d.period === p
                ? 'border-gold/50 bg-gold/10 text-gold'
                : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
            )}>
            {p}
          </Link>
        ))}
      </div>

      {/* Period stepper */}
      <div className="flex items-center justify-between">
        <Link href={href(d.period, d.offset + 1)} scroll={false}
          className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/5 transition-colors hover:bg-white/10">
          <ChevronLeft size={16} className="text-muted-foreground" />
        </Link>
        <div className="text-center">
          <p className="text-sm font-semibold text-foreground">{d.label}</p>
          <p className="text-[10px] text-muted-foreground">
            {d.offset === 0 ? `This ${unit}` : d.offset === 1 ? `Last ${unit}` : `${d.offset} ${unit}s ago`}
          </p>
        </div>
        {d.offset === 0 ? (
          <span className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/5 bg-white/[0.02] opacity-30">
            <ChevronRight size={16} className="text-muted-foreground" />
          </span>
        ) : (
          <Link href={href(d.period, d.offset - 1)} scroll={false}
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/5 transition-colors hover:bg-white/10">
            <ChevronRight size={16} className="text-muted-foreground" />
          </Link>
        )}
      </div>

      {/* Hero — the one number the report leads with */}
      <div className="nafs-card border-l-2 p-4" style={{ borderLeftColor: NOW }}>
        <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
          Average daily score
        </p>
        <div className="flex items-end gap-3">
          <span className="text-[52px] font-extrabold leading-none tracking-tight" style={{ color: NOW }}>
            {d.avg_score}<span className="text-2xl font-bold">%</span>
          </span>
          <div className="pb-1.5 text-xs">
            {d.score_delta == null ? (
              <span className="text-muted-foreground">No previous {unit} yet</span>
            ) : (
              <>
                <Delta value={d.score_delta} />
                <span className="ml-1 text-muted-foreground">
                  vs {d.prev_avg_score}% last {unit}
                </span>
              </>
            )}
          </div>
        </div>
        <div className="mt-3 grid grid-cols-3 gap-2 border-t border-white/10 pt-3 text-center">
          {[
            [`${d.days_logged}/${d.days_elapsed}`, 'Days logged'],
            [`${d.strong_days}`, 'Strong days'],
            [`${d.best_streak}d`, 'Best streak'],
          ].map(([v, l]) => (
            <div key={l}>
              <p className="text-lg font-bold leading-tight text-foreground">{v}</p>
              <p className="text-[9px] uppercase tracking-wider text-muted-foreground">{l}</p>
            </div>
          ))}
        </div>
      </div>

      {/* The coach's read — explains the period in words, and prints with it */}
      {d.days_logged > 0 && (
        <div className="nafs-card p-4">
          <div className="mb-1 flex items-start justify-between gap-2">
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-gold/20 bg-gold/10">
                <Sparkles size={15} className="text-gold" />
              </div>
              <div>
                <h2 className="text-sm font-semibold leading-tight text-foreground">The coach&apos;s read</h2>
                <p className="text-[10px] text-muted-foreground">
                  {deepModel === 'anthropic' ? 'Written by Claude' : 'Written by the free model'} · prints with the report
                </p>
              </div>
            </div>
            {review && (
              <span className="flex-shrink-0 text-[10px] text-muted-foreground">
                {prettyDate(review.generated_at.slice(0, 10))}
              </span>
            )}
          </div>

          {review ? (
            <>
              <div className="mt-3 text-xs leading-relaxed text-foreground/90">
                <RichText text={review.content_md} />
              </div>
              <p className="mt-3 border-t border-white/10 pt-2 text-[10px] leading-relaxed text-muted-foreground">
                One reading of the numbers. The cards below are the record — trust a figure
                there over a figure here.
              </p>
            </>
          ) : (
            <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
              Six short sections in plain words: how the {unit} went, what improved, where you
              need to improve, the pattern underneath, three things to do next {unit}, and one
              question. It is written once and saved, so the printout carries it.
            </p>
          )}

          {reviewNote && <p className="mt-2 text-[11px] text-muted-foreground">{reviewNote}</p>}
          {reviewError && <p className="mt-2 text-[11px] text-red-400">{reviewError}</p>}

          <button onClick={writeReview} disabled={writing}
            className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-primary px-4 py-2.5
                       text-xs font-semibold text-white transition-all active:scale-[0.99] disabled:opacity-50">
            {writing
              ? <><Loader2 size={14} className="animate-spin" /> Reading your {unit}…</>
              : review
                ? <><RefreshCw size={14} /> Write it again</>
                : <><Sparkles size={14} /> Write the read</>}
          </button>
        </div>
      )}

      {/* Progress vs last period */}
      {d.comparison.length > 0 && (
        <Card title={`Progress vs last ${unit}`} note={d.previousLabel}>
          <div className="mb-3 flex items-center gap-3 text-[10px] text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full" style={{ background: PREV }} /> Last {unit}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full" style={{ background: NOW }} /> This {unit}
            </span>
            <span className="ml-auto">0–100%</span>
          </div>

          {pctRows.map((c) => (
            <div key={c.key} className="mb-3">
              <div className="mb-1 flex items-baseline justify-between gap-2">
                <span className="truncate text-xs text-foreground">
                  {c.emoji} {c.label}
                </span>
                <span className="flex-shrink-0 text-[11px] tabular-nums">
                  <span className="font-semibold text-foreground">{c.current == null ? '—' : `${c.current}%`}</span>
                  <span className="ml-2"><Delta value={c.delta} /></span>
                </span>
              </div>
              <Dumbbell row={c} />
              <p className="mt-0.5 text-[10px] text-muted-foreground">{c.detail}</p>
            </div>
          ))}

          {countRows.map((c) => (
            <div key={c.key} className="mb-1 flex items-baseline justify-between gap-2 border-t border-white/10 pt-2">
              <span className="truncate text-xs text-foreground">{c.emoji} {c.label}</span>
              <span className="flex-shrink-0 text-[11px] tabular-nums">
                <span className="font-semibold text-foreground">{c.current ?? 0}</span>
                <span className="ml-2"><Delta value={c.delta} unit="pages" /></span>
              </span>
            </div>
          ))}

          <p className="mt-2 border-t border-white/10 pt-2 text-[10px] leading-relaxed text-muted-foreground">
            Every area is measured against all {d.days_elapsed} elapsed days, so a day you
            did not log counts against it.
          </p>
        </Card>
      )}

      {/* Where to improve */}
      {d.focus.length > 0 && (
        <Card title="Where to improve next" note={`${d.focus.length} ranked`}>
          <ol className="space-y-2.5">
            {d.focus.map((f, i) => (
              <li key={f.key}
                className={cn('rounded-xl border border-white/10 border-l-2 bg-white/[0.03] p-3', SEVERITY_EDGE[f.severity])}>
                <div className="flex items-center gap-2">
                  <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-white/10 text-[10px] font-bold text-foreground">
                    {i + 1}
                  </span>
                  <span className="flex-1 truncate text-sm font-semibold text-foreground">
                    {f.emoji} {f.title}
                  </span>
                  <span className={cn('flex-shrink-0 rounded-full px-2 py-0.5 text-[9px] font-bold uppercase tracking-wide', SEVERITY_CHIP[f.severity])}>
                    {SEVERITY_ICON[f.severity]} {f.severityLabel}
                  </span>
                </div>
                <p className="mt-1.5 text-[10px] text-muted-foreground">
                  {f.area} · <span className="font-semibold text-foreground/80">{f.stat}</span>
                  {f.delta != null && f.delta !== 0 && <> · <Delta value={f.delta} /></>}
                </p>
                <p className="mt-1 text-xs leading-relaxed text-foreground/80">{f.why}</p>
                <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                  <span className="font-semibold text-foreground/70">Do this:</span> {f.action}
                </p>
              </li>
            ))}
          </ol>
        </Card>
      )}

      {/* Wins */}
      {d.wins.length > 0 && (
        <Card title="What went well">
          {d.wins.map((w) => (
            <div key={w.key} className="flex items-baseline gap-2 border-b border-white/5 py-1.5 last:border-b-0">
              <span style={{ color: '#3fca3f' }} className="font-bold">▲</span>
              <span className="flex-1 text-xs text-foreground">{w.emoji} {w.label}</span>
              <span className="text-[11px] font-semibold tabular-nums" style={{ color: '#3fca3f' }}>
                +{w.delta}{w.unit === 'pages' ? ' pages' : ' pts'}
              </span>
            </div>
          ))}
        </Card>
      )}

      {/* Daily score — one series, emphasis on the extremes */}
      {d.days.length > 0 && (
        <Card title="Daily score" note={`${d.strong_days} strong · ${d.weak_days} weak`}>
          <div className="relative flex h-24 items-end gap-[3px] border-b border-white/15">
            {d.avg_score > 0 && (
              <div className="pointer-events-none absolute inset-x-0 border-t border-white/20"
                style={{ bottom: `${clamp(d.avg_score)}%` }}>
                <span className="absolute right-0 -top-3.5 bg-[#0B1A2B] px-1 text-[9px] text-muted-foreground">
                  avg {d.avg_score}%
                </span>
              </div>
            )}
            {d.days.map((day) => {
              const emph = day.logged && (day.date === d.best_day?.date || day.date === d.worst_day?.date)
              return (
                <div key={day.date} className="flex h-full flex-1 flex-col justify-end"
                  title={`${day.weekday} ${prettyDate(day.date)} — ${day.logged ? day.score + '%' : 'not logged'}`}>
                  <div className="rounded-t"
                    style={{
                      height: `${Math.max(2, clamp(day.score))}%`,
                      background: day.logged ? (emph ? ACCENT : NOW) : TRACK,
                    }} />
                </div>
              )
            })}
          </div>
          <div className="mt-1.5 flex justify-between text-[9px] text-muted-foreground">
            <span>{prettyDate(d.days[0].date)}</span>
            {d.best_day && d.worst_day && (
              <span style={{ color: ACCENT }}>
                best {d.best_day.weekday} {d.best_day.score}% · low {d.worst_day.weekday} {d.worst_day.score}%
              </span>
            )}
            <span>{prettyDate(d.days[d.days.length - 1].date)}</span>
          </div>
        </Card>
      )}

      {/* Insights */}
      <Card title="What the numbers say">
        <ul className="space-y-2">
          {d.insights.map((line, i) => (
            <li key={i} className="flex gap-2 text-xs leading-relaxed text-foreground/85">
              <span className="text-gold">•</span><span>{line}</span>
            </li>
          ))}
        </ul>
      </Card>

      {/* Salah */}
      {d.deenEnabled && d.prayers_possible > 0 && (
        <Card title="Salah" note={`${d.prayer_points}/${d.prayer_points_max} quality pts`}>
          {d.prayer_breakdown.map((p) => (
            <Meter key={p.key} label={p.label}
              right={`${p.prayed}/${d.days_elapsed}${p.jamat ? ` · ${p.jamat} jamaat` : ''}`}
              pct={p.pct} />
          ))}
          {d.extra_prayers.length > 0 && (
            <div className="mt-3 border-t border-white/10 pt-3">
              <p className="mb-2 text-[10px] uppercase tracking-wider text-muted-foreground">Extra / nafl</p>
              {d.extra_prayers.map((x) => (
                <Meter key={x.name} label={x.name} right={`${x.done}/${x.days}`} pct={x.pct} />
              ))}
            </div>
          )}
          {d.quran_pages > 0 && (
            <p className="mt-1 text-[10px] text-muted-foreground">
              Quran {d.quran_pages} pages over {d.quran_days} days
              {d.quran_surahs.length > 0 && ` — ${d.quran_surahs.slice(0, 4).join(', ')}`}
            </p>
          )}
        </Card>
      )}

      {/* Habits */}
      {d.habits.length > 0 && (
        <Card title="Habits" note={`${d.habit_completion_pct}% overall`}>
          {d.habits.map((h) => (
            <Meter key={h.id} label={`${h.emoji} ${h.name}`}
              right={`${h.done_days}/${h.scheduled_days} · best run ${h.best_run}d`} pct={h.pct} />
          ))}
        </Card>
      )}

      {/* Tasks */}
      {d.tasks_total > 0 && (
        <Card title="Tasks" note={`${d.tasks_pct}% completed`}>
          {d.tasks.map((t) => (
            <Meter key={t.type} label={t.type.charAt(0).toUpperCase() + t.type.slice(1)}
              right={`${t.completed}/${t.total}`} pct={t.pct} />
          ))}
          {d.missed_tasks.length > 0 && (
            <div className="mt-2 border-t border-white/10 pt-2">
              <p className="mb-1.5 text-[10px] text-muted-foreground">Unfinished ({d.missed_tasks.length})</p>
              {d.missed_tasks.slice(0, 6).map((t, i) => (
                <p key={i} className="mb-1 truncate text-[11px] text-foreground/70">
                  • {t.title} <span className="text-muted-foreground">— {prettyDate(t.date)}</span>
                </p>
              ))}
              {d.missed_tasks.length > 6 && (
                <p className="text-[10px] text-muted-foreground">+{d.missed_tasks.length - 6} more in the printout</p>
              )}
            </div>
          )}
        </Card>
      )}

      {/* Challenges */}
      {d.challenges.length > 0 && (
        <Card title="Challenges">
          {d.challenges.map((c) => (
            <Meter key={c.id} label={`${c.emoji} ${c.title}`}
              right={`${c.checkins}/${c.possible_days} · day ${c.day_of}/${c.duration_days}`} pct={c.pct} />
          ))}
        </Card>
      )}

      {/* Goals */}
      {d.goals.length > 0 && (
        <Card title="Goals">
          {d.goals.map((g) => (
            <Meter key={g.id} label={`${g.emoji} ${g.title}`}
              right={g.milestones_total
                ? `${g.progress_pct}% · ${g.milestones_done}/${g.milestones_total} milestones`
                : `${g.progress_pct}%`}
              pct={g.progress_pct} />
          ))}
        </Card>
      )}

      {/* Health */}
      {d.health.days_logged > 0 && (
        <Card title="Health" note={`${d.health.days_logged} days logged`}>
          <div className="grid grid-cols-3 gap-2">
            {[
              ['Avg sleep', d.health.avg_sleep == null ? '—' : `${d.health.avg_sleep}h`, ''],
              ['Avg steps', d.health.avg_steps == null ? '—' : Math.round(d.health.avg_steps).toLocaleString(), ''],
              ['Exercise', `${d.health.exercise_days}d`, `${d.health.exercise_minutes} min`],
              ['Avg water', d.health.avg_water == null ? '—' : String(d.health.avg_water), 'glasses/day'],
              ['Avg mood', d.health.avg_mood == null ? '—' : `${d.health.avg_mood}/10`, ''],
              ['Weight', d.health.weight_end == null ? '—' : `${d.health.weight_end}kg`,
                d.health.weight_change == null ? '' : `${d.health.weight_change > 0 ? '+' : ''}${d.health.weight_change} kg`],
            ].map(([label, value, sub]) => (
              <div key={label} className="rounded-xl border border-white/10 bg-white/5 p-2.5 text-center">
                <p className="text-base font-bold leading-tight text-foreground">{value}</p>
                <p className="text-[9px] uppercase tracking-wider text-muted-foreground">{label}</p>
                {sub && <p className="text-[9px] text-muted-foreground/70">{sub}</p>}
              </div>
            ))}
          </div>
          {d.health.metrics.length > 0 && (
            <div className="mt-3 border-t border-white/10 pt-3">
              <p className="mb-2 text-[10px] uppercase tracking-wider text-muted-foreground">Your metrics</p>
              {d.health.metrics.map((m) => (
                <Meter key={m.id} label={`${m.emoji} ${m.name}`} right={`${m.done}/${m.days}`} pct={m.pct} />
              ))}
            </div>
          )}
        </Card>
      )}

      {/* Weekday pattern */}
      {d.weekday_avgs.length >= 2 && (
        <Card title="Weekday pattern">
          {d.weekday_avgs.map((w) => (
            <Meter key={w.weekday} label={w.weekday} right={`${w.avg}%`} pct={w.avg} />
          ))}
        </Card>
      )}

      {/* Reflections */}
      {d.reflections.length > 0 && (
        <Card title="Your reflections" note={`${d.reflections.length} entries`}>
          {d.reflections.slice(0, 5).map((r) => (
            <div key={r.date} className="mb-3 border-l-2 border-white/15 pl-3">
              <p className="text-[10px] font-semibold uppercase tracking-wider" style={{ color: NOW }}>
                {prettyDate(r.date, { weekday: 'short', day: 'numeric', month: 'short' })}
              </p>
              {r.text && <p className="mt-0.5 whitespace-pre-wrap text-xs leading-relaxed text-foreground/80">{r.text}</p>}
              {r.verdict && <p className="mt-1 text-[11px] text-muted-foreground"><strong>NAFS:</strong> {r.verdict}</p>}
            </div>
          ))}
          {d.reflections.length > 5 && (
            <p className="text-[10px] text-muted-foreground">+{d.reflections.length - 5} more in the printout</p>
          )}
        </Card>
      )}

      {/* Empty state */}
      {d.days_logged === 0 && (
        <div className="nafs-card p-8 text-center">
          <p className="mb-3 text-4xl">📄</p>
          <p className="font-semibold text-foreground">Nothing logged yet</p>
          <p className="mt-2 text-sm text-muted-foreground">
            Log prayers, habits or tasks and this {unit}&apos;s report will fill in.
          </p>
        </div>
      )}

      <p className="text-center text-[10px] text-muted-foreground">
        The printout adds the full day-by-day log and every reflection
        {review ? ", and opens with the coach's read" : ''}.
      </p>

      {/* Print bar */}
      <div className="fixed inset-x-0 bottom-0 z-40 px-4"
        style={{ paddingBottom: 'calc(env(safe-area-inset-bottom, 0px) + 84px)' }}>
        <div className="mx-auto flex max-w-md gap-2">
          <button onClick={print} disabled={printing}
            className="btn-gold flex flex-1 items-center justify-center gap-2 py-3 text-sm">
            <Printer size={16} />
            {printing ? 'Preparing…' : 'Print / Save as PDF'}
          </button>
          <button onClick={openPrintable} title="Open the printable page in a new tab"
            aria-label="Open the printable page in a new tab"
            className="flex items-center justify-center rounded-xl border border-white/15 bg-white/10 px-4 py-3 text-sm font-semibold text-foreground backdrop-blur transition-colors hover:bg-white/15">
            <ExternalLink size={16} />
          </button>
        </div>
      </div>
    </div>
  )
}
