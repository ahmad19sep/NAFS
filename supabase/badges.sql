-- Earned badges per user.
-- Shape: { "badge_id": "2026-05-15T12:30:00Z", ... }
-- Mapping badge_id → ISO timestamp of when it was earned.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS badges JSONB NOT NULL DEFAULT '{}';
