-- Coach notes: what the user told the coach, in their own words.
--
-- The coach's memory. A reason given for a miss, what is going on in their
-- life, what they want. Read back into every coach answer so it can say
-- "last time you said…" and notice when what they said they wanted and what
-- the records show have come apart. Nothing here is generated: every row is
-- text the user typed.
--
-- Run once in the Supabase SQL editor. Safe to re-run.

create table if not exists public.coach_notes (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.users(id) on delete cascade,
  -- miss_reason: why a repeated miss happened.
  -- low_score:   what was going on, on a day that went badly.
  -- life:        an answer to one of the coach's life questions.
  kind       text not null check (kind in ('miss_reason', 'low_score', 'life')),
  -- miss_reason: 'habit:<id>' or 'prayer:<key>'. life: the question key. low_score: null.
  subject    text check (subject is null or length(subject) <= 100),
  content    text not null check (length(content) between 1 and 2000),
  -- The user's own calendar day, not the server's.
  date       date not null,
  created_at timestamptz not null default now()
);

create index if not exists coach_notes_user_date_idx
  on public.coach_notes (user_id, date desc);

alter table public.coach_notes enable row level security;

drop policy if exists "coach_notes: own rows" on public.coach_notes;
create policy "coach_notes: own rows" on public.coach_notes
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Rollback:
-- drop table if exists public.coach_notes;
