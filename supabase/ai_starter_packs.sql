-- AI starter packs on habits and challenges (mirrors goals.ai_starter_pack)
-- Generated automatically when the user creates the resource.

ALTER TABLE public.habits
  ADD COLUMN IF NOT EXISTS ai_starter_pack JSONB;

ALTER TABLE public.challenges
  ADD COLUMN IF NOT EXISTS ai_starter_pack JSONB;
