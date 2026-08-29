-- ============================================================
-- NAFS — FULL DATA RESET
-- Deletes EVERY account and ALL their data (habits, prayers,
-- logs, challenges, goals, tasks, AI reports, letters, photos).
-- Seed/content tables (e.g. AI starter packs) are kept.
--
-- ⚠️ IRREVERSIBLE. Run only when you want a 100% clean slate.
--
-- How to run: supabase.com → your project → SQL Editor →
-- paste this file → Run.
-- ============================================================

-- 1) Delete all auth accounts.
--    public.users references auth.users ON DELETE CASCADE, and every
--    data table references public.users ON DELETE CASCADE, so this one
--    statement wipes: users, habits, habit_logs, prayer_logs, challenges,
--    challenge_checkins, goals, goal_milestones, daily_checkins, tasks,
--    health_logs, screentime_logs, ai_reports, ai_conversations,
--    future_self_letters.
DELETE FROM auth.users;

-- 2) Delete all uploaded files (avatars, dream images, photo proofs).
DELETE FROM storage.objects
WHERE bucket_id IN (
  'avatars', 'dream-images', 'log-photos', 'challenge-photos', 'voice-notes'
);

-- 3) Verify — both counts should be 0.
SELECT
  (SELECT COUNT(*) FROM auth.users)    AS remaining_accounts,
  (SELECT COUNT(*) FROM public.users)  AS remaining_profiles;
