-- Email reports preferences

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS notify_email_daily  BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS notify_email_weekly BOOLEAN NOT NULL DEFAULT FALSE;
