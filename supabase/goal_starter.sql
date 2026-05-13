-- Stores the AI-suggested starter pack of tasks + habits for each goal.
-- Generated on demand from the goal's title/description/type.

ALTER TABLE public.goals
  ADD COLUMN IF NOT EXISTS ai_starter_pack JSONB;

-- Suggested shape:
-- {
--   "summary": "To get A+ in programming, focus on daily practice and...",
--   "suggested_tasks": [
--     { "title": "Solve 3 LeetCode mediums", "priority": "high" }
--   ],
--   "suggested_habits": [
--     { "name": "DSA practice", "emoji": "💻", "type": "duration", "time_target_mins": 60 }
--   ],
--   "generated_at": "2026-..."
-- }
