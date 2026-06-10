-- ============================================================
-- Faith mode (Deen module opt-in) — Ascend rebrand
-- Run in Supabase SQL Editor. Safe to re-run.
--
-- New signups choose "Enable faith features?" at account creation.
-- deen_enabled=TRUE shows prayers, Deen tab and prayer scoring.
-- ============================================================

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS deen_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing accounts predate the toggle and were all using prayer features:
-- keep faith mode on for them.
UPDATE public.users SET deen_enabled = TRUE
WHERE created_at < '2026-06-11';

-- Recreate the signup trigger so deen_enabled flows from signup metadata.
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
      id, email, name, gender, avatar_url, created_at, onboarding_complete, deen_enabled
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
      is_oauth,
      COALESCE((NEW.raw_user_meta_data->>'deen_enabled')::boolean, FALSE)
    )
    ON CONFLICT (id) DO NOTHING;
  EXCEPTION
    WHEN OTHERS THEN
      RAISE WARNING 'handle_new_user failed for %: %', NEW.id, SQLERRM;
  END;

  RETURN NEW;
END;
$$;
