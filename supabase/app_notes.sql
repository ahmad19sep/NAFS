-- app_notes: bugs and improvement ideas about the app itself.
--
-- Not about the user's habits — about NAFS. Something breaks, or an idea
-- arrives while using it, and it gets written down before it is forgotten.
-- The list is then read when the next update is planned.
--
-- Kept per user and behind RLS like everything else: these are notes, but
-- they can quote screens and data, so they are private by default.
--
-- Run once in the Supabase SQL editor. Safe to re-run.

create table if not exists public.app_notes (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.users(id) on delete cascade,
  kind        text not null check (kind in ('bug', 'idea')),
  title       text not null check (length(title) between 1 and 200),
  detail      text check (detail is null or length(detail) <= 2000),
  -- Where it was seen: a screen name, in the user's words. Optional.
  where_seen  text check (where_seen is null or length(where_seen) <= 80),
  -- open: still to do. done: shipped. dismissed: decided against.
  status      text not null default 'open' check (status in ('open', 'done', 'dismissed')),
  created_at  timestamptz not null default now(),
  resolved_at timestamptz
);

create index if not exists app_notes_user_idx
  on public.app_notes (user_id, status, created_at desc);

alter table public.app_notes enable row level security;

drop policy if exists "app_notes: own rows" on public.app_notes;
create policy "app_notes: own rows" on public.app_notes
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Verify: should return the table's check constraints.
select conname, pg_get_constraintdef(oid) as definition
from pg_constraint
where conrelid = 'public.app_notes'::regclass;

-- Rollback:
-- drop table if exists public.app_notes;
