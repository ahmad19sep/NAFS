-- Auto-sync public.users from auth.users
-- This trigger fires on every new signup (email, Google, etc.) and creates
-- the matching public.users row with name + email + avatar + created_at + gender
-- pulled from auth.users.
--
-- OAuth users (Google, etc.) get onboarding_complete = TRUE automatically so
-- they go straight to /dashboard. Email-password users start with FALSE so
-- they're walked through /onboarding.
--
-- Run this once. Re-runnable safely.

-- ============================================================
-- 1. Trigger function
-- ============================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  provider TEXT;
  is_oauth BOOLEAN;
BEGIN
  provider := COALESCE(NEW.raw_app_meta_data->>'provider', 'email');
  is_oauth := provider <> 'email';

  BEGIN
    INSERT INTO public.users (
      id, email, name, gender, avatar_url, created_at, onboarding_complete
    )
    VALUES (
      NEW.id,
      COALESCE(NEW.email, ''),
      COALESCE(
        NULLIF(NEW.raw_user_meta_data->>'name', ''),
        NULLIF(NEW.raw_user_meta_data->>'full_name', ''),
        NULLIF(NEW.raw_user_meta_data->>'user_name', ''),
        split_part(COALESCE(NEW.email, ''), '@', 1)
      ),
      NULLIF(NEW.raw_user_meta_data->>'gender', ''),
      COALESCE(
        NULLIF(NEW.raw_user_meta_data->>'avatar_url', ''),
        NULLIF(NEW.raw_user_meta_data->>'picture', '')
      ),
      NEW.created_at,
      is_oauth   -- TRUE for Google etc, FALSE for email
    )
    ON CONFLICT (id) DO NOTHING;
  EXCEPTION
    WHEN OTHERS THEN
      -- Never block auth.users INSERT, even if our sync row fails
      RAISE WARNING 'handle_new_user failed for %: %', NEW.id, SQLERRM;
  END;

  RETURN NEW;
END;
$$;

-- ============================================================
-- 2. Trigger
-- ============================================================
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ============================================================
-- 3. One-time backfill — create + complete rows for existing auth users
-- ============================================================

-- Create rows for auth users that don't have a public.users row yet
INSERT INTO public.users (
  id, email, name, gender, avatar_url, created_at, onboarding_complete
)
SELECT
  au.id,
  COALESCE(au.email, ''),
  COALESCE(
    NULLIF(au.raw_user_meta_data->>'name', ''),
    NULLIF(au.raw_user_meta_data->>'full_name', ''),
    NULLIF(au.raw_user_meta_data->>'user_name', ''),
    split_part(COALESCE(au.email, ''), '@', 1)
  ),
  NULLIF(au.raw_user_meta_data->>'gender', ''),
  COALESCE(
    NULLIF(au.raw_user_meta_data->>'avatar_url', ''),
    NULLIF(au.raw_user_meta_data->>'picture', '')
  ),
  au.created_at,
  COALESCE(au.raw_app_meta_data->>'provider', 'email') <> 'email'
FROM auth.users au
LEFT JOIN public.users pu ON pu.id = au.id
WHERE pu.id IS NULL;

-- Backfill any missing fields on existing rows
UPDATE public.users pu
SET
  name = COALESCE(
    NULLIF(pu.name, ''),
    NULLIF(au.raw_user_meta_data->>'name', ''),
    NULLIF(au.raw_user_meta_data->>'full_name', ''),
    NULLIF(au.raw_user_meta_data->>'user_name', ''),
    split_part(COALESCE(au.email, ''), '@', 1)
  ),
  email      = COALESCE(NULLIF(pu.email, ''), au.email, ''),
  gender     = COALESCE(pu.gender, NULLIF(au.raw_user_meta_data->>'gender', '')),
  avatar_url = COALESCE(
                 pu.avatar_url,
                 NULLIF(au.raw_user_meta_data->>'avatar_url', ''),
                 NULLIF(au.raw_user_meta_data->>'picture', '')
               ),
  created_at = COALESCE(pu.created_at, au.created_at),
  -- If user signed in via OAuth and was never sent through onboarding,
  -- mark them complete so they go to dashboard
  onboarding_complete = CASE
    WHEN pu.onboarding_complete THEN TRUE
    WHEN COALESCE(au.raw_app_meta_data->>'provider', 'email') <> 'email' THEN TRUE
    ELSE FALSE
  END
FROM auth.users au
WHERE au.id = pu.id;
