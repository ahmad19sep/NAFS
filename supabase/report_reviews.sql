-- report_reviews: the coach's written read of one weekly or monthly report.
--
-- Its own table rather than a row in ai_reports, because a review is keyed by
-- (period, start_date) and ai_reports has no period column. A weekly period
-- and a monthly period can begin on the same date — whenever the 1st falls on
-- the week's first day — so keying on the start date alone would let a
-- monthly review surface on a weekly report.
--
-- One review per period: writing again replaces it, so the printed report
-- always carries the newest read.
--
-- Run once in the Supabase SQL editor. Safe to re-run.

create table if not exists public.report_reviews (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.users(id) on delete cascade,
  period      text not null check (period in ('weekly', 'monthly')),
  -- First day of the period the review covers.
  start_date  date not null,
  content_md  text not null,
  -- Which model actually wrote it, so the printout can say so honestly.
  model_used  text not null default 'unknown',
  generated_at timestamptz not null default now(),
  unique (user_id, period, start_date)
);

create index if not exists report_reviews_user_idx
  on public.report_reviews (user_id, start_date desc);

alter table public.report_reviews enable row level security;

drop policy if exists "report_reviews: own rows" on public.report_reviews;
create policy "report_reviews: own rows" on public.report_reviews
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Verify: should return the table with its unique constraint.
select conname, pg_get_constraintdef(oid) as definition
from pg_constraint
where conrelid = 'public.report_reviews'::regclass;

-- Rollback:
-- drop table if exists public.report_reviews;
