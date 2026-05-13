-- "Tell us more about yourself" — a small profile expansion.
-- Stored as a single JSONB column so we can add fields without migrations.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS about_me JSONB NOT NULL DEFAULT '{}';

-- Suggested shape:
--   { bio: string, occupation: string, birth_date: 'YYYY-MM-DD',
--     location: string, interests: string[] }
