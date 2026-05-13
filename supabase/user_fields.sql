-- Additional user profile fields collected at signup
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS gender TEXT CHECK (gender IN ('male', 'female', 'prefer_not_to_say'));
