-- Stores the latest AI-generated health recommendation for the user.
-- Generated automatically when first-time setup completes (and on demand).

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS ai_health_recommendation JSONB;

-- Suggested shape:
-- {
--   "summary": "Your BMI is 22 and sleep averages 7.5h — solid baseline...",
--   "priorities": ["Build daily walking habit", "Add strength training 2x/week"],
--   "suggested_goals": [
--     { "title": "Reach 10K steps a day for 30 days", "type": "monthly", "category": "health" }
--   ],
--   "suggested_habits": [
--     { "name": "Morning walk", "emoji": "🚶", "type": "duration", "time_target_mins": 20 }
--   ],
--   "generated_at": "2026-..."
-- }
