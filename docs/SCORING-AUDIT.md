# Scoring audit — DATA-01

Ticket DATA-01 from the NAFS Improvement Blueprint: inventory every displayed
percentage, map it to its numerator, denominator and source, and explain the
report discrepancy **before** any formula changes.

No score formula was changed. Fixtures in
[`report.test.ts`](../web-legacy/src/lib/report.test.ts) pin current behaviour so
that the day a formula does move, the diff shows exactly which promise moved.

---

## The 53% is explained

Daily scores in the supplied report were 87, 79, 38, 30, 61, — , 20, with
Saturday unlogged. They sum to 315.

[`report.ts:470-471`](../web-legacy/src/lib/report.ts#L470-L471):

```ts
const scored = days.filter((d) => d.logged)
const avgScore = Math.round(avg(scored.map((d) => d.score)) ?? 0)
```

`avgScore` is the mean over **logged days only**: 315 / 6 = 52.5 → 53%.
Zero-filling the unlogged day would give 315 / 7 = 45%.

So the arithmetic is not a bug. The number is an observed-day mean presented in
a report whose period is seven days, under the label "Average daily score",
without the denominator beside it. The data object already carries the
denominator — `days_logged` and `days_elapsed` are rendered in the very next
tile — so labelling it honestly needs no new computation.

Reproduced by fixture **D01**.

---

## Every displayed percentage

| Metric | Numerator | Denominator | Unlogged days |
|---|---|---|---|
| `avg_score` | sum of daily scores | **logged** days | **ignored** |
| daily `score` | sum of per-feature % | features with `max > 0` | absent features ignored |
| `salahPct` | prayers prayed | `elapsed × 5` — **all** elapsed days | **counted as zero** |
| `prayerPoints` / max | points earned | `elapsed × 10` | **counted as zero** |
| `healthPct` | health flags on recorded rows | `4 × elapsed` — **all** elapsed days | **counted as zero** |
| `habitPct` | habit days done | habit days scheduled | schedule-driven |
| `tasksPct` | tasks completed | tasks that exist in the period | task-driven |
| `challengePct` | check-ins | challenge duration days | schedule-driven |
| `consistencyPct` | logged days | elapsed days | this *is* the coverage figure |

Helper: `pct(earned, possible)` returns `null` when `possible === 0`
([report.ts:109](../web-legacy/src/lib/report.ts#L109)) — so a zero denominator
already yields "no rate" rather than 0%, which is the behaviour DATA-02 wants
everywhere.

### The finding that matters more than the 53%

**The report mixes two missing-data policies in one document.** The headline
`avg_score` ignores unlogged days. `salahPct`, `healthPct` and the prayer-points
maximum divide by *every elapsed day*, so an unlogged day silently counts as a
total failure in those areas.

The two numbers therefore answer different questions. A week logged on 3 of 7
days can show a healthy headline average and a poor Salah and Health percentage
purely from the missing four days — no change in behaviour required. Any
comparison between areas, or between periods with different coverage, inherits
that skew.

This is not a rounding problem and cannot be fixed by relabelling one number.
It is the substance of DATA-02: pick one policy, state it next to every
percentage, and show coverage wherever a denominator is not fully observed.

---

## SAFE-01 — the weekday over-claim, fixed

[`report.ts`](../web-legacy/src/lib/report.ts) gated its recurring-weekday claim
on `weekdayAvgs.length >= 4` — the number of **distinct weekdays present**, not
how many times each was observed. A week with 6 logged days yields 6 distinct
weekdays, each with `days: 1`, so the gate passed and the report asserted
"Sunday is reliably your worst day" from exactly one Sunday.

Fixed by gating on observations per compared weekday
(`MIN_WEEKDAY_OBSERVATIONS = 4`) at both claim sites — the focus list and the
insight text. Below the gate the report now states the dated high and low days
and says plainly that one of each is not yet a pattern.

In practice a weekly report can no longer make the claim at all, since it sees
each weekday once. Monthly reports still can, and fixture
`still reports a real pattern once each weekday has enough observations`
proves the insight was gated rather than disabled.

Reproduced by fixture **D13**.

---

## Test harness

`vitest` (one devDependency, native TypeScript) with `npm test`. 113 tests:

| Fixture | Covers |
|---|---|
| D01 | Observed-day mean; unlogged ≠ zero; the 53% vs 45% difference |
| D02 | Completion on recorded, coverage, and the possible-completion bound |
| D03 | Nothing scheduled → no rate, never NaN |
| D04 | Eligible but nothing recorded ≠ 0% performance |
| D13 | One observation is not a weekday pattern; real patterns still surface |

D02–D04 run against [`coverage.ts`](../web-legacy/src/lib/coverage.ts), a pure
implementation of the blueprint's proposed metrics. **Nothing consumes it yet** —
it exists so DATA-02 migrates onto a definition that is already tested, rather
than inventing one mid-refactor.

```bash
cd web-legacy
npm test           # 113 tests
npx tsc --noEmit   # clean
npm run build      # compiles
```

---

## Not done, and deliberately so

- **No formula changed.** DATA-01 is an audit. Choosing between the observed-day
  mean and a zero-filled mean is an owner decision (blueprint page 27), not a
  refactor.
- **`coverage.ts` is unwired.** Wiring it is DATA-02 and changes displayed
  numbers, which needs the decision above first.
- **The mixed-policy skew is now disclosed in the report itself**, under "How
  these numbers are counted", rather than repaired. Repairing it means
  choosing one policy for the whole report.
- **Home vs Health denominator mismatch** (4 vs 5 categories) is untouched — the
  blueprint asks for it to be reproduced in one account with identical enabled
  modules first, which needs a running session.

### Next bounded task

Take the owner decision on missing-day policy, then wire `coverage.ts` into one
surface — Health is the best candidate, since its denominator is already the
most misleading — and extend the fixtures to that surface before touching the
others.

---

## DATA-03 — date resolution (partial)

The blueprint's first date rule: *resolve "today" using the account's explicit
timezone, not the server's date.*

`notify-tasks` already did this correctly, resolving each account's local date
and time before deciding whether a reminder is due. The two scheduled reports
did not: both derived a single UTC date **before** their per-user loop.

Because Vercel cron fires at a fixed UTC hour, that date lands on a different
calendar day depending where the account is. An account far enough east of UTC
received a daily report for the wrong day, and a weekly window shifted by one —
silently, since every figure in it was internally consistent.

Fixed: both reports now resolve `todayInTZ(u.timezone || 'UTC')` **inside** the
loop, per account, and derive the rest with `shiftDate`, a pure calendar
operation that never touches a timezone. `health-recommend`'s 14-day window had
the same flaw and is fixed the same way.

Covered by [`dates.test.ts`](../web-legacy/src/lib/dates.test.ts): month, year
and leap-day boundaries, window construction, round-trip stability, and that two
far-apart zones are never more than one day apart — which is what makes a fixed
UTC hour unsafe in the first place.

### Still open in DATA-03

- **38 sites still derive dates from `toISOString()`**, which is UTC. Most are
  wide range starts where a one-day edge does not change what the user sees, but
  they have not been audited one by one.
- **`todayString()` uses the runtime's local zone.** Correct in the browser;
  on the server it is whatever `TZ` is set to. Client code calling it is fine —
  server code should take the account's timezone instead.
- **Sleep sessions store wall-clock times, not instants.** The blueprint asks
  for real instants so duration survives a timezone change or DST. Today a
  session recorded either side of a clock change computes from wall-clock
  arithmetic. Changing that needs a migration and belongs with DATA-02.

---

## LOG-01 — write safety (partial)

The blueprint's rule: *do not let two devices overwrite each other's totals
silently. Use atomic increments for additive quantities.*

### Fixed: the lost update on reading progress

`/api/habits/log` handled subject habits (a book with a page position) in three
steps: read the day's stored amount, read `habits.subject_position`, then write
position + (new − old). Two requests interleaving between the read and the write
meant one person's pages were dropped — and because the surviving number was
still plausible, nothing ever surfaced the loss. One device retrying on a flaky
connection was enough.

That now runs inside `log_subject_habit`
([supabase/atomic_habit_progress.sql](../supabase/atomic_habit_progress.sql)),
which takes a row lock on the habit before reading, so concurrent calls queue
rather than race, and returns the position it settled on so the client shows the
true total instead of its own guess. If the function is missing the route
returns 503 naming the migration, rather than falling back to the racy path and
quietly corrupting a running total.

The design is idempotent by construction: a day's log stores the **absolute**
amount read that day, and the position moves by the difference from what was
stored before. Replaying a request after a lost response applies a zero
adjustment. Pinned by [`habit-progress.test.ts`](../web-legacy/src/lib/habit-progress.test.ts),
which also covers D06 bounds and D08 replay.

Because the SQL cannot be unit-tested from here, the arithmetic it must
implement is duplicated as an executable specification in
[`habit-progress.ts`](../web-legacy/src/lib/habit-progress.ts). Change one,
change both — the alternative is an untested rule living only in a migration.

### Fixed: completing a task was destructive on retry

`/api/tasks/[id]/toggle` read the stored status and flipped it. A lost
response followed by a client retry flipped the task back, silently undoing
what the user had just done; a fast double-tap did the same.

The client now sends the state it wants rather than asking for a flip, so
repeating the request is harmless. When the task is already in that state the
route returns it unchanged instead of rewriting, so a replay cannot move
`completed_at` and make an old task look freshly done. The flip survives only
as a fallback for a client that sends no body. Pinned by
[`task-status.test.ts`](../web-legacy/src/lib/task-status.test.ts), including a
test asserting the old double-flip behaviour is the bug.

### Fixed: a retried creation no longer makes two tasks

Creation cannot be made idempotent on its own — "insert a row" repeated is
two rows however carefully it is written — so it is guarded by a request id
the client sends. The first request carrying that id claims it in
[`mutations`](../supabase/mutations.sql), does the work and records the
result; a retry finds the claim and replays the original task.

The client generates one id per creation intent and keeps it across a failed
attempt, so pressing Save again after a dropped connection is recognised as
the same task. It is cleared only once the task exists.

Payloads are hashed canonically, with keys sorted at every depth, because
`JSON.stringify` preserves insertion order — without that an honest retry
that built its body differently would look like new content and be refused.
The same id with genuinely different content is a conflict, not a second
write, and a claim with no result yet answers "in flight" rather than
inventing a result or doing the work twice.

Pinned by [`idempotency.test.ts`](../web-legacy/src/lib/idempotency.test.ts),
including that a conflict is never mistaken for a replay — the dangerous
confusion, since it would return the wrong task.

### Fixed: two devices no longer overwrite each other on the Health page

Every field on that page is an absolute value taken from the form, so the
upsert was last-write-wins. Open the day on a phone and a laptop, save on
both, and whichever saved first was silently replaced — including fields the
second device never touched and whose values it had never seen.

The row's `updated_at` is now read when the page loads and required to still
match at save time. If it moved, the write is refused and the page says so,
offering to reload. Neither version is discarded automatically: what is on
screen stays until the reload is taken. An insert colliding on (user_id, date)
is treated the same way, since it means the row appeared while the page was
open.

The rules live in [`write-conflict.ts`](../web-legacy/src/lib/write-conflict.ts)
so the dangerous case is pinned: a guarded update that matched **no rows**
returns no error, and reading that as success is the entire bug.

### Fixed: deleting a task is reversible

Deleting was permanent behind a browser `confirm()`. A mis-tap lost the task
and everything it recorded about that day.

Deletes now set `deleted_at` and the page offers Undo, which restores the
original row rather than inserting a copy — so the task keeps its id, its
creation time and its place in history. Restoring is idempotent, so a
double-tap on Undo does nothing odd.

The hiding is done in RLS, not in the queries. Thirteen places read tasks —
dashboard, history, profile, reports, the tasks page, the coach context, goal
alignment, both report crons, task reminders. Adding "and not deleted" to each
is a condition someone will eventually forget, and the failure is silent: a
deleted task quietly reappearing in a report or in what the AI is told. The
SELECT policy hides them instead, so no query *can* forget. UPDATE
deliberately does not filter, because a restore has to reach a hidden row.

See [`task_soft_delete.sql`](../supabase/task_soft_delete.sql), which carries its
own rollback. Without it, deleting still works and simply reports itself as
not undoable — the UI only offers Undo when the task can actually come back.

### Still open in LOG-01

- **Only creation is guarded.** `withIdempotency` is generic, but no other
  write passes a request id yet.
- **No pending state on every surface.** Tasks now say "Not saved" on a
  network failure; the other forms do not.
- **Undo covers task deletion only.** Habit logs, health entries and the rest
  still correct by overwriting, with nothing that targets the original
  mutation.
