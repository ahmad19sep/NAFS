-- Goal alignment: stores the latest AI analysis of how well the user's
-- recent activity is moving them toward each goal.
--
-- Shape:
-- {
--   "score": 0..100,
--   "doing_well":  string,
--   "missing":     string,
--   "suggested_action": string,
--   "analyzed_at": ISO timestamp
-- }

ALTER TABLE public.goals
  ADD COLUMN IF NOT EXISTS ai_alignment JSONB;
