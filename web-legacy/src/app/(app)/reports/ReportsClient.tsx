'use client'

import { useState } from 'react'
import Link from 'next/link'
import { ChevronLeft, ChevronRight, Printer, ExternalLink, FileText } from 'lucide-react'
import { cn } from '@/lib/utils'
import { prettyDate, type ReportData, type ReportPeriod } from '@/lib/report'
import { reportToHtml, reportFileName } from '@/lib/report-html'

function scoreClass(score: number): string {
  if (score >= 80) return 'text-emerald-400'
  if (score >= 60) return 'text-gold'
  if (score >= 40) return 'text-orange-400'
  return 'text-red-400'
}

function scoreHex(score: number): string {
  if (score >= 80) return '#34d399'
  if (score >= 60) return '#C9A227'
  if (score >= 40) return '#fb923c'
  return '#f87171'
}

function Card({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <div className="nafs-card p-4">
      <div className="mb-3 flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-foreground">{title}</h2>
        {note && <span className="text-[10px] text-muted-foreground">{note}</span>}
      </div>
      {children}
    </div>
  )
}

function Tile({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: string }) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/5 p-2.5 text-center">
      <p className={cn('text-lg font-bold tabular-nums leading-tight', tone ?? 'text-foreground')}>{value}</p>
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      {sub && <p className="text-[9px] text-muted-foreground/70 mt-0.5">{sub}</p>}
    </div>
  )
}

function Meter({ label, right, pct, color }: { label: string; right: string; pct: number; color: string }) {
  return (
    <div className="mb-2.5">
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="truncate text-xs text-foreground">{label}</span>
        <span className="flex-shrink-0 text-[10px] tabular-nums text-muted-foreground">{right}</span>
      </div>
      <div className="h-1.5 rounded-full bg-white/10">
        <div className="h-full rounded-full transition-all"
          style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: color }} />
      </div>
    </div>
  )
}

export default function ReportsClient({ report: d }: { report: ReportData }) {
  const [printing, setPrinting] = useState(false)
  const unit = d.period === 'weekly' ? 'week' : 'month'

  function href(period: ReportPeriod, offset: number) {
    return `/reports?period=${period}&offset=${Math.max(0, offset)}`
  }

  /** Render the printable document into a hidden iframe and open the print dialog. */
  function print() {
    setPrinting(true)
    const html = reportToHtml(d)
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
        // keep the frame alive long enough for the print dialog to read from it
        setTimeout(() => frame.remove(), 60_000)
      }
    }
    frame.srcdoc = html
    document.body.appendChild(frame)
    // never leave the button stuck on "Preparing…" if load never fires
    setTimeout(() => setPrinting(false), 8_000)
  }

  /**
   * Fallback for webviews where window.print() is a no-op (the Android shell):
   * open the report as a standalone page the system browser can print or share.
   */
  function openPrintable() {
    const blob = new Blob([reportToHtml(d, { autoPrint: true })], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const w = window.open(url, '_blank')
    if (!w) {
      // popup blocked — fall back to a download of the same document
      const a = document.createElement('a')
      a.href = url
      a.download = `${reportFileName(d)}.html`
      a.click()
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }

  return (
    <div className="mx-auto max-w-md space-y-5 px-4 pb-32">
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

      {/* KPI tiles */}
      <div className="grid grid-cols-3 gap-2">
        <Tile label="Avg score" value={`${d.avg_score}%`} tone={scoreClass(d.avg_score)}
          sub={d.score_delta == null ? undefined
            : d.score_delta === 0 ? 'same as last'
            : `${d.score_delta > 0 ? '▲' : '▼'} ${Math.abs(d.score_delta)} pts`} />
        <Tile label="Days logged" value={`${d.days_logged}/${d.days_elapsed}`}
          sub={`${Math.round((d.days_logged / Math.max(d.days_elapsed, 1)) * 100)}% consistent`} />
        {d.deenEnabled
          ? <Tile label="Salah" value={`${d.prayers_prayed}/${d.prayers_possible}`} tone="text-emerald-400" sub={`${d.prayers_jamat} jamaat`} />
          : <Tile label="Perfect days" value={`${d.perfect_days}`} tone="text-emerald-400" />}
        <Tile label="Habits" value={`${d.habit_completion_pct}%`} tone="text-gold" sub={`${d.habits.length} tracked`} />
        <Tile label="Tasks" value={`${d.tasks_completed}/${d.tasks_total}`} tone="text-emerald-400" sub={`${d.tasks_pct}% done`} />
        <Tile label="Best streak" value={`${d.best_streak}d`} tone="text-orange-400" sub="at 60%+" />
      </div>

      {/* Daily score chart */}
      {d.days.length > 0 && (
        <Card title="Daily score" note={`${d.strong_days} strong · ${d.weak_days} weak`}>
          <div className="flex h-24 items-end gap-[3px]">
            {d.days.map((day) => (
              <div key={day.date} className="flex h-full flex-1 flex-col justify-end" title={`${day.weekday} ${prettyDate(day.date)} — ${day.logged ? day.score + '%' : 'not logged'}`}>
                <div className="rounded-t"
                  style={{
                    height: `${Math.max(2, day.score)}%`,
                    background: day.logged ? scoreHex(day.score) : 'rgba(255,255,255,0.10)',
                  }} />
              </div>
            ))}
          </div>
          <div className="mt-1.5 flex justify-between text-[9px] text-muted-foreground">
            <span>{prettyDate(d.days[0].date)}</span>
            <span>{prettyDate(d.days[d.days.length - 1].date)}</span>
          </div>
          {d.best_day && d.worst_day && (
            <p className="mt-2 text-[10px] text-muted-foreground">
              Best {d.best_day.weekday} {prettyDate(d.best_day.date)} ({d.best_day.score}%) ·
              {' '}Toughest {d.worst_day.weekday} {prettyDate(d.worst_day.date)} ({d.worst_day.score}%)
            </p>
          )}
        </Card>
      )}

      {/* Insights */}
      <Card title="What the numbers say">
        <ul className="space-y-2">
          {d.insights.map((line, i) => (
            <li key={i} className="flex gap-2 text-xs leading-relaxed text-foreground/85">
              <span className="text-gold">•</span>
              <span>{line}</span>
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
              pct={p.pct} color={scoreHex(p.pct)} />
          ))}
          {d.extra_prayers.length > 0 && (
            <div className="mt-3 border-t border-white/10 pt-3">
              <p className="mb-2 text-[10px] uppercase tracking-wider text-muted-foreground">Extra / nafl</p>
              {d.extra_prayers.map((x) => (
                <Meter key={x.name} label={x.name} right={`${x.done}/${x.days}`} pct={x.pct} color={scoreHex(x.pct)} />
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
              right={`${h.done_days}/${h.scheduled_days} · best run ${h.best_run}d`}
              pct={h.pct} color={scoreHex(h.pct)} />
          ))}
        </Card>
      )}

      {/* Tasks */}
      {d.tasks_total > 0 && (
        <Card title="Tasks" note={`${d.tasks_pct}% completed`}>
          {d.tasks.map((t) => (
            <Meter key={t.type} label={t.type.charAt(0).toUpperCase() + t.type.slice(1)}
              right={`${t.completed}/${t.total}`} pct={t.pct} color="#34d399" />
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
              right={`${c.checkins}/${c.possible_days} · day ${c.day_of}/${c.duration_days}`}
              pct={c.pct} color="#34d399" />
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
              pct={g.progress_pct} color="#C9A227" />
          ))}
        </Card>
      )}

      {/* Health */}
      {d.health.days_logged > 0 && (
        <Card title="Health" note={`${d.health.days_logged} days logged`}>
          <div className="grid grid-cols-3 gap-2">
            <Tile label="Avg sleep" value={d.health.avg_sleep == null ? '—' : `${d.health.avg_sleep}h`} />
            <Tile label="Avg steps" value={d.health.avg_steps == null ? '—' : Math.round(d.health.avg_steps).toLocaleString()} />
            <Tile label="Exercise" value={`${d.health.exercise_days}d`} sub={`${d.health.exercise_minutes} min`} />
            <Tile label="Avg water" value={d.health.avg_water == null ? '—' : `${d.health.avg_water}`} sub="glasses/day" />
            <Tile label="Avg mood" value={d.health.avg_mood == null ? '—' : `${d.health.avg_mood}/10`} />
            <Tile label="Weight" value={d.health.weight_end == null ? '—' : `${d.health.weight_end}kg`}
              sub={d.health.weight_change == null ? undefined
                : `${d.health.weight_change > 0 ? '+' : ''}${d.health.weight_change} kg`} />
          </div>
          {d.health.metrics.length > 0 && (
            <div className="mt-3 border-t border-white/10 pt-3">
              <p className="mb-2 text-[10px] uppercase tracking-wider text-muted-foreground">Your metrics</p>
              {d.health.metrics.map((m) => (
                <Meter key={m.id} label={`${m.emoji} ${m.name}`} right={`${m.done}/${m.days}`} pct={m.pct} color={scoreHex(m.pct)} />
              ))}
            </div>
          )}
        </Card>
      )}

      {/* Weekday pattern */}
      {d.weekday_avgs.length >= 2 && (
        <Card title="Weekday pattern">
          {d.weekday_avgs.map((w) => (
            <Meter key={w.weekday} label={w.weekday} right={`${w.avg}%`} pct={w.avg} color={scoreHex(w.avg)} />
          ))}
        </Card>
      )}

      {/* Reflections */}
      {d.reflections.length > 0 && (
        <Card title="Your reflections" note={`${d.reflections.length} entries`}>
          {d.reflections.slice(0, 5).map((r) => (
            <div key={r.date} className="mb-3 border-l-2 border-white/15 pl-3">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-teal-300">
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
        The printout adds the full day-by-day log and every reflection.
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
            className="flex items-center justify-center gap-2 rounded-xl border border-white/15 bg-white/10 px-4 py-3 text-sm font-semibold text-foreground backdrop-blur transition-colors hover:bg-white/15">
            <ExternalLink size={16} />
          </button>
        </div>
      </div>
    </div>
  )
}
