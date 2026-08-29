import type { ReportData } from '@/lib/report'
import { prettyDate } from '@/lib/report'

// ─────────────────────────────────────────────────────────────────────────────
// A4 print stylesheet. Light theme on purpose — dark backgrounds waste ink and
// most printers drop them anyway.
// ─────────────────────────────────────────────────────────────────────────────

const NAVY = '#0B1A2B'
const TEAL = '#0F4C5C'
const GOLD = '#C9A227'

function esc(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function scoreColor(score: number): string {
  if (score >= 80) return '#128a5c'
  if (score >= 60) return '#b07d00'
  if (score >= 40) return '#c2620d'
  return '#c02a2a'
}

function bar(pct: number, color: string): string {
  const w = Math.max(0, Math.min(100, pct))
  return `<span class="bar"><span class="bar-fill" style="width:${w}%;background:${color}"></span></span>`
}

function section(title: string, body: string, note?: string): string {
  if (!body.trim()) return ''
  return `
  <section>
    <h2>${esc(title)}${note ? `<span class="h2-note">${esc(note)}</span>` : ''}</h2>
    ${body}
  </section>`
}

function kpi(label: string, value: string, sub = '', color = NAVY): string {
  return `
    <div class="kpi">
      <div class="kpi-value" style="color:${color}">${esc(value)}</div>
      <div class="kpi-label">${esc(label)}</div>
      ${sub ? `<div class="kpi-sub">${esc(sub)}</div>` : ''}
    </div>`
}

function num(n: number | null, suffix = ''): string {
  return n == null ? '—' : `${n}${suffix}`
}

// ─────────────────────────────────────────────────────────────────────────────

export function reportToHtml(d: ReportData): string {
  const periodName = d.period === 'weekly' ? 'Weekly' : 'Monthly'
  const deltaHtml = d.score_delta == null
    ? '<span class="muted">no prior period</span>'
    : d.score_delta === 0
      ? '<span class="muted">same as last period</span>'
      : `<span style="color:${d.score_delta > 0 ? '#128a5c' : '#c02a2a'}">${d.score_delta > 0 ? '▲' : '▼'} ${Math.abs(d.score_delta)} pts vs last ${d.period === 'weekly' ? 'week' : 'month'}</span>`

  // ── Daily score chart ──────────────────────────────────────────────────────
  const chart = d.days.length === 0 ? '' : `
    <div class="chart">
      ${d.days.map(day => `
        <div class="chart-col">
          <div class="chart-val">${day.logged ? day.score : ''}</div>
          <div class="chart-bar-wrap">
            <div class="chart-bar" style="height:${Math.max(2, day.score)}%;background:${day.logged ? scoreColor(day.score) : '#d8dee6'}"></div>
          </div>
          <div class="chart-day">${esc(day.weekday)}</div>
          <div class="chart-date">${esc(prettyDate(day.date, { day: 'numeric' }))}</div>
        </div>`).join('')}
    </div>`

  // ── Salah ──────────────────────────────────────────────────────────────────
  const salah = d.prayers_possible === 0 ? '' : `
    <table>
      <thead><tr><th>Prayer</th><th>Prayed</th><th>Jamaat</th><th>Missed</th><th class="w-40">Consistency</th></tr></thead>
      <tbody>
        ${d.prayer_breakdown.map(p => `
          <tr>
            <td><strong>${esc(p.label)}</strong></td>
            <td>${p.prayed}/${d.days_elapsed}</td>
            <td>${p.jamat}</td>
            <td class="${p.missed > 0 ? 'bad' : ''}">${p.missed}</td>
            <td>${bar(p.pct, scoreColor(p.pct))}<span class="pct">${p.pct}%</span></td>
          </tr>`).join('')}
      </tbody>
      <tfoot>
        <tr>
          <td><strong>Total</strong></td>
          <td><strong>${d.prayers_prayed}/${d.prayers_possible}</strong></td>
          <td><strong>${d.prayers_jamat}</strong></td>
          <td><strong>${d.prayers_possible - d.prayers_prayed}</strong></td>
          <td><strong>${Math.round((d.prayers_prayed / Math.max(d.prayers_possible, 1)) * 100)}%</strong></td>
        </tr>
      </tfoot>
    </table>
    <p class="note">
      Quality points ${d.prayer_points}/${d.prayer_points_max} (1 pt prayed alone, 2 pts in jamaat)${d.extra_prayers > 0 ? ` · ${d.extra_prayers} extra/nafl prayers logged` : ''}${d.quran_pages > 0 ? ` · Quran ${d.quran_pages} pages across ${d.quran_days} days` : ''}
    </p>`

  // ── Habits ─────────────────────────────────────────────────────────────────
  const habits = d.habits.length === 0 ? '' : `
    <table>
      <thead><tr><th>Habit</th><th>Done</th><th class="w-40">Rate</th><th>Best run</th><th>Streak</th><th>Volume</th></tr></thead>
      <tbody>
        ${d.habits.map(h => `
          <tr>
            <td>${esc(h.emoji)} <strong>${esc(h.name)}</strong></td>
            <td>${h.done_days}/${h.eligible_days}</td>
            <td>${bar(h.pct, scoreColor(h.pct))}<span class="pct">${h.pct}%</span></td>
            <td>${h.best_run}d</td>
            <td>${h.current_streak}d <span class="muted">(best ${h.longest_streak}d)</span></td>
            <td>${h.type === 'duration'
                  ? `${h.total_minutes} min`
                  : h.type === 'count'
                    ? `${h.total_value}${h.unit ? ' ' + esc(h.unit) : ''}`
                    : '—'}</td>
          </tr>`).join('')}
      </tbody>
      <tfoot>
        <tr><td colspan="6"><strong>Overall habit completion: ${d.habit_completion_pct}%</strong></td></tr>
      </tfoot>
    </table>`

  // ── Tasks ──────────────────────────────────────────────────────────────────
  const tasks = d.tasks_total === 0 ? '' : `
    <table>
      <thead><tr><th>Type</th><th>Completed</th><th>Total</th><th class="w-40">Rate</th></tr></thead>
      <tbody>
        ${d.tasks.map(t => `
          <tr>
            <td style="text-transform:capitalize"><strong>${esc(t.type)}</strong></td>
            <td>${t.completed}</td>
            <td>${t.total}</td>
            <td>${bar(t.pct, scoreColor(t.pct))}<span class="pct">${t.pct}%</span></td>
          </tr>`).join('')}
      </tbody>
      <tfoot>
        <tr><td><strong>All tasks</strong></td><td><strong>${d.tasks_completed}</strong></td><td><strong>${d.tasks_total}</strong></td><td><strong>${d.tasks_pct}%</strong></td></tr>
      </tfoot>
    </table>
    ${d.missed_tasks.length === 0 ? '<p class="note good">Nothing left unfinished. 🎉</p>' : `
      <h3>Unfinished (${d.missed_tasks.length})</h3>
      <ul class="list">
        ${d.missed_tasks.slice(0, 40).map(t => `
          <li><span class="pill pill-${esc(t.priority)}">${esc(t.priority)}</span> ${esc(t.title)}
              <span class="muted">— ${esc(t.type)}, ${esc(prettyDate(t.date))}</span></li>`).join('')}
      </ul>
      ${d.missed_tasks.length > 40 ? `<p class="note">+ ${d.missed_tasks.length - 40} more</p>` : ''}`}`

  // ── Challenges ─────────────────────────────────────────────────────────────
  const challenges = d.challenges.length === 0 ? '' : `
    <table>
      <thead><tr><th>Challenge</th><th>Check-ins</th><th class="w-40">Rate</th><th>Progress</th><th>Streak</th><th>Status</th></tr></thead>
      <tbody>
        ${d.challenges.map(c => `
          <tr>
            <td>${esc(c.emoji)} <strong>${esc(c.title)}</strong></td>
            <td>${c.checkins}/${c.possible_days}</td>
            <td>${bar(c.pct, scoreColor(c.pct))}<span class="pct">${c.pct}%</span></td>
            <td>day ${c.day_of} of ${c.duration_days}</td>
            <td>${c.current_streak}d <span class="muted">(best ${c.longest_streak}d)</span></td>
            <td style="text-transform:capitalize">${esc(c.status)}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  // ── Goals ──────────────────────────────────────────────────────────────────
  const goals = d.goals.length === 0 ? '' : `
    <table>
      <thead><tr><th>Goal</th><th class="w-40">Progress</th><th>Milestones</th><th>Deadline</th></tr></thead>
      <tbody>
        ${d.goals.map(g => `
          <tr>
            <td>${esc(g.emoji)} <strong>${esc(g.title)}</strong></td>
            <td>${bar(g.progress_pct, GOLD)}<span class="pct">${g.progress_pct}%</span></td>
            <td>${g.milestones_total ? `${g.milestones_done}/${g.milestones_total}` : '—'}</td>
            <td>${g.deadline
                  ? `${esc(prettyDate(g.deadline, { day: 'numeric', month: 'short', year: 'numeric' }))} <span class="${(g.days_left ?? 0) < 0 ? 'bad' : 'muted'}">(${(g.days_left ?? 0) < 0 ? `${Math.abs(g.days_left!)}d overdue` : `${g.days_left}d left`})</span>`
                  : '—'}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  // ── Health ─────────────────────────────────────────────────────────────────
  const h = d.health
  const health = h.days_logged === 0 ? '' : `
    <div class="kpi-row">
      ${kpi('Avg sleep', num(h.avg_sleep, 'h'), `${h.days_logged} days logged`, TEAL)}
      ${kpi('Avg steps', h.avg_steps == null ? '—' : Math.round(h.avg_steps).toLocaleString(), `${h.total_steps.toLocaleString()} total`, TEAL)}
      ${kpi('Exercise', `${h.exercise_days}d`, `${h.exercise_minutes} min total`, TEAL)}
      ${kpi('Avg water', num(h.avg_water), 'glasses/day', TEAL)}
      ${kpi('Avg mood', h.avg_mood == null ? '—' : `${h.avg_mood}/10`, '', TEAL)}
      ${kpi('Weight', h.weight_end == null ? '—' : `${h.weight_end} kg`,
            h.weight_change == null ? '' : `${h.weight_change > 0 ? '+' : ''}${h.weight_change} kg this period`, TEAL)}
    </div>`

  // ── Weekday pattern ────────────────────────────────────────────────────────
  const weekdays = d.weekday_avgs.length < 2 ? '' : `
    <table>
      <thead><tr><th>Day</th><th class="w-40">Average score</th><th>Days counted</th></tr></thead>
      <tbody>
        ${d.weekday_avgs.map(w => `
          <tr>
            <td><strong>${esc(w.weekday)}</strong></td>
            <td>${bar(w.avg, scoreColor(w.avg))}<span class="pct">${w.avg}%</span></td>
            <td>${w.days}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  // ── Day-by-day log ─────────────────────────────────────────────────────────
  const dailyLog = d.days.length === 0 ? '' : `
    <table class="dense">
      <thead>
        <tr>
          <th>Date</th><th>Score</th><th>Salah</th><th>Jamaat</th><th>Habits</th>
          <th>Tasks</th><th>Quran</th><th>Sleep</th><th>Steps</th><th>Water</th><th>Exercise</th><th>Mood</th>
        </tr>
      </thead>
      <tbody>
        ${d.days.map(day => `
          <tr class="${day.logged ? '' : 'empty-row'}">
            <td><strong>${esc(day.weekday)}</strong> ${esc(prettyDate(day.date))}</td>
            <td style="color:${day.logged ? scoreColor(day.score) : '#98a5b3'};font-weight:700">${day.logged ? day.score + '%' : '—'}</td>
            <td>${day.prayers_prayed}/5</td>
            <td>${day.prayers_jamat}</td>
            <td>${day.habits_total ? `${day.habits_done}/${day.habits_total}` : '—'}</td>
            <td>${day.tasks_total ? `${day.tasks_done}/${day.tasks_total}` : '—'}</td>
            <td>${day.quran_pages || '—'}</td>
            <td>${day.sleep_hours == null ? '—' : day.sleep_hours + 'h'}</td>
            <td>${day.steps == null ? '—' : Number(day.steps).toLocaleString()}</td>
            <td>${day.water_glasses || '—'}</td>
            <td>${day.exercise_done ? (day.exercise_minutes ? `${day.exercise_minutes}m` : '✓') : '—'}</td>
            <td>${day.mood == null ? '—' : day.mood}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  // ── Reflections ────────────────────────────────────────────────────────────
  const reflections = d.reflections.length === 0 ? '' : `
    ${d.reflections.map(r => `
      <div class="quote">
        <div class="quote-date">${esc(prettyDate(r.date, { weekday: 'short', day: 'numeric', month: 'short' }))}</div>
        <div class="quote-text">${esc(r.text)}</div>
      </div>`).join('')}`

  // ── Document ───────────────────────────────────────────────────────────────
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>NAFS ${esc(periodName)} Report — ${esc(d.label)}</title>
<style>
  @page { size: A4; margin: 14mm 12mm; }
  * { box-sizing: border-box; }
  body {
    font-family: -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    color: #12212f; margin: 0; font-size: 11px; line-height: 1.5;
    -webkit-print-color-adjust: exact; print-color-adjust: exact;
  }
  header.cover {
    background: linear-gradient(135deg, ${NAVY} 0%, ${TEAL} 100%);
    color: #fff; padding: 20px 22px; border-radius: 10px; margin-bottom: 18px;
  }
  .brand { font-size: 12px; letter-spacing: 4px; text-transform: uppercase; color: ${GOLD}; font-weight: 700; }
  .cover h1 { font-size: 24px; margin: 6px 0 2px; font-weight: 800; }
  .cover .sub { font-size: 12px; opacity: .85; }
  .cover .meta { margin-top: 12px; font-size: 10px; opacity: .7; }

  .kpi-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
  .kpi {
    flex: 1 1 100px; min-width: 100px; border: 1px solid #e3e8ee; border-radius: 8px;
    padding: 10px 8px; text-align: center; background: #fbfcfd;
  }
  .kpi-value { font-size: 20px; font-weight: 800; line-height: 1.1; }
  .kpi-label { font-size: 9px; text-transform: uppercase; letter-spacing: .6px; color: #64748b; margin-top: 3px; }
  .kpi-sub { font-size: 9px; color: #94a3b8; margin-top: 2px; }

  section { margin-bottom: 16px; page-break-inside: avoid; }
  h2 {
    font-size: 13px; font-weight: 800; color: ${NAVY}; margin: 0 0 8px;
    padding-bottom: 5px; border-bottom: 2px solid ${GOLD};
  }
  .h2-note { float: right; font-size: 9px; font-weight: 500; color: #94a3b8; padding-top: 3px; }
  h3 { font-size: 11px; margin: 10px 0 5px; color: ${TEAL}; }

  table { width: 100%; border-collapse: collapse; margin-bottom: 4px; }
  th {
    text-align: left; font-size: 9px; text-transform: uppercase; letter-spacing: .5px;
    color: #64748b; border-bottom: 1px solid #d9e0e8; padding: 5px 6px; font-weight: 700;
  }
  td { padding: 5px 6px; border-bottom: 1px solid #eef2f6; vertical-align: middle; }
  tfoot td { border-top: 1.5px solid #d9e0e8; border-bottom: none; background: #fbfcfd; }
  table.dense td, table.dense th { padding: 3px 4px; font-size: 9.5px; }
  .w-40 { width: 34%; }
  .empty-row td { color: #a9b5c2; }

  .bar { display: inline-block; width: 60%; height: 7px; background: #e9eef3; border-radius: 4px; overflow: hidden; vertical-align: middle; }
  .bar-fill { display: block; height: 100%; border-radius: 4px; }
  .pct { display: inline-block; margin-left: 6px; font-variant-numeric: tabular-nums; font-weight: 600; }

  .chart { display: flex; align-items: flex-end; gap: 2px; height: 130px; padding: 6px 0 0; }
  .chart-col { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; }
  .chart-val { font-size: 8px; color: #64748b; height: 11px; }
  .chart-bar-wrap { flex: 1; width: 100%; display: flex; align-items: flex-end; justify-content: center; }
  .chart-bar { width: 78%; border-radius: 3px 3px 0 0; }
  .chart-day { font-size: 8px; color: #475569; margin-top: 3px; font-weight: 600; }
  .chart-date { font-size: 7.5px; color: #94a3b8; }

  ul.list { margin: 4px 0; padding-left: 16px; }
  ul.list li { margin-bottom: 3px; }
  .pill { display: inline-block; font-size: 8px; text-transform: uppercase; font-weight: 700;
          padding: 1px 5px; border-radius: 20px; letter-spacing: .4px; }
  .pill-high { background: #fde2e2; color: #a51b1b; }
  .pill-medium { background: #fef3c7; color: #92620a; }
  .pill-low { background: #dbeafe; color: #1e4fa3; }

  .insights { background: #fbfcfd; border: 1px solid #e3e8ee; border-left: 3px solid ${GOLD};
              border-radius: 6px; padding: 10px 12px 10px 26px; margin: 0; }
  .insights li { margin-bottom: 5px; }

  .quote { border-left: 3px solid #dbe3ea; padding: 2px 0 2px 10px; margin-bottom: 8px; page-break-inside: avoid; }
  .quote-date { font-size: 9px; color: ${TEAL}; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; }
  .quote-text { white-space: pre-wrap; color: #33465a; }

  .note { font-size: 9.5px; color: #64748b; margin: 4px 0 0; }
  .muted { color: #94a3b8; }
  .bad { color: #c02a2a; }
  .good { color: #128a5c; }

  footer { margin-top: 20px; padding-top: 10px; border-top: 1px solid #e3e8ee;
           text-align: center; font-size: 9px; color: #94a3b8; }
  footer .ayah { color: ${TEAL}; font-style: italic; margin-bottom: 4px; }
  .page-break { page-break-before: always; }
</style>
</head>
<body>

<header class="cover">
  <div class="brand">NAFS</div>
  <h1>${esc(periodName)} Progress Report</h1>
  <div class="sub">${esc(d.label)} · ${esc(d.user.name)}</div>
  <div class="meta">
    Generated ${esc(d.generated_at)}${d.user.email ? ` · ${esc(d.user.email)}` : ''}
    · Covering ${d.days_elapsed} of ${d.days_in_period} days
  </div>
</header>

<div class="kpi-row">
  ${kpi('Average score', `${d.avg_score}%`, '', scoreColor(d.avg_score))}
  ${kpi('Days logged', `${d.days_logged}/${d.days_elapsed}`, `${Math.round((d.days_logged / Math.max(d.days_elapsed, 1)) * 100)}% consistency`)}
  ${kpi('Salah', `${d.prayers_prayed}/${d.prayers_possible}`, `${d.prayers_jamat} in jamaat`)}
  ${kpi('Habits', `${d.habit_completion_pct}%`, `${d.habits.length} tracked`)}
  ${kpi('Tasks', `${d.tasks_completed}/${d.tasks_total}`, `${d.tasks_pct}% done`)}
  ${kpi('Best streak', `${d.best_streak}d`, 'days at 60%+')}
</div>

<p class="note" style="margin-bottom:14px">
  Trend: ${deltaHtml}
  ${d.best_day ? ` · Best day <strong>${esc(d.best_day.weekday)} ${esc(prettyDate(d.best_day.date))}</strong> (${d.best_day.score}%)` : ''}
  ${d.worst_day ? ` · Toughest day <strong>${esc(d.worst_day.weekday)} ${esc(prettyDate(d.worst_day.date))}</strong> (${d.worst_day.score}%)` : ''}
</p>

${section('Daily score', chart, `${d.strong_days} strong · ${d.weak_days} weak`)}

${section('What the numbers say', `<ul class="insights">${d.insights.map(i => `<li>${esc(i)}</li>`).join('')}</ul>`)}

${section('Salah & Quran', salah)}
${section('Habits', habits, `${d.habit_completion_pct}% overall`)}
${section('Tasks', tasks, `${d.tasks_pct}% completed`)}
${section('Challenges', challenges)}
${section('Goals', goals)}
${section('Health', health)}
${section('Weekday pattern', weekdays)}

${dailyLog ? `<div class="page-break"></div>${section('Day-by-day log', dailyLog)}` : ''}
${section('Your reflections', reflections, `${d.reflections.length} entries`)}

<footer>
  <div class="ayah">"Indeed, Allah will not change the condition of a people until they change what is in themselves." — Quran 13:11</div>
  NAFS · ${esc(periodName)} report for ${esc(d.user.name)} · ${esc(d.start)} to ${esc(d.end)}
</footer>

</body>
</html>`
}

/** Filename used when the PDF is shared/saved. */
export function reportFileName(d: ReportData): string {
  const kind = d.period === 'weekly' ? 'Weekly' : 'Monthly'
  const who = d.user.name.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '') || 'NAFS'
  return `NAFS-${kind}-Report-${who}-${d.start}.pdf`
}
