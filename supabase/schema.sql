-- NAFS v2 — Full Database Schema
-- Run in Supabase SQL editor: supabase.com > SQL editor > New query

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.users (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  name TEXT NOT NULL DEFAULT '',
  email TEXT NOT NULL DEFAULT '',
  avatar_url TEXT,
  timezone TEXT NOT NULL DEFAULT 'Asia/Karachi',
  onboarding_complete BOOLEAN NOT NULL DEFAULT FALSE,
  push_subscription JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- HABITS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.habits (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '⭐',
  type TEXT NOT NULL DEFAULT 'boolean' CHECK (type IN ('boolean', 'count', 'duration')),
  target_value NUMERIC NOT NULL DEFAULT 1,
  unit TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL DEFAULT 'custom',
  current_streak INTEGER NOT NULL DEFAULT 0,
  longest_streak INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Habit daily logs
CREATE TABLE IF NOT EXISTS public.habit_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  habit_id UUID NOT NULL REFERENCES public.habits(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  value NUMERIC NOT NULL DEFAULT 0,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, habit_id, date)
);

-- ============================================================
-- CHALLENGES
-- ============================================================
CREATE TABLE IF NOT EXISTS public.challenges (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '🎯',
  description TEXT,
  duration_days INTEGER NOT NULL DEFAULT 21,
  start_date DATE NOT NULL,
  requires_photo BOOLEAN NOT NULL DEFAULT FALSE,
  sadqa_amount DECIMAL(10,2),
  sadqa_currency TEXT NOT NULL DEFAULT 'PKR',
  current_streak INTEGER NOT NULL DEFAULT 0,
  longest_streak INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.challenge_checkins (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  challenge_id UUID NOT NULL REFERENCES public.challenges(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  photo_url TEXT,
  sadqa_paid BOOLEAN NOT NULL DEFAULT FALSE,
  sadqa_receipt_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (challenge_id, date)
);

-- ============================================================
-- GOALS
-- ============================================================
CREATE TABLE IF NOT EXISTS public.goals (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '⭐',
  description TEXT,
  deadline DATE,
  progress_pct INTEGER NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
  ai_plan TEXT,
  linked_habit_ids TEXT[] NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.goal_milestones (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  goal_id UUID NOT NULL REFERENCES public.goals(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  done BOOLEAN NOT NULL DEFAULT FALSE,
  target_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- DAILY CHECK-INS (morning plan + evening review)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.daily_checkins (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  tasks JSONB NOT NULL DEFAULT '[]',
  evening_text TEXT,
  ai_verdict TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date)
);

-- ============================================================
-- AI REPORTS (verdicts, tribunal, etc.)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.ai_reports (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  type TEXT NOT NULL CHECK (type IN ('tribunal', 'pull', 'gap', 'letter_reply')),
  week_start DATE,
  content_md TEXT NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  model_used TEXT NOT NULL DEFAULT 'gemini-2.0-flash'
);

-- AI conversations (Ask NAFS chat)
CREATE TABLE IF NOT EXISTS public.ai_conversations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  messages JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Future self letters
CREATE TABLE IF NOT EXISTS public.future_self_letters (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  written_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  target_deliver_date DATE NOT NULL,
  delivered_at TIMESTAMPTZ,
  ai_reply_text TEXT
);

-- ============================================================
-- ROW LEVEL SECURITY
-- ============================================================
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.habits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.habit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.challenge_checkins ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goal_milestones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_checkins ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.future_self_letters ENABLE ROW LEVEL SECURITY;

-- Drop policies first so this script is safe to re-run
DROP POLICY IF EXISTS "own" ON public.users;
DROP POLICY IF EXISTS "own" ON public.habits;
DROP POLICY IF EXISTS "own" ON public.habit_logs;
DROP POLICY IF EXISTS "own" ON public.challenges;
DROP POLICY IF EXISTS "own" ON public.challenge_checkins;
DROP POLICY IF EXISTS "own" ON public.goals;
DROP POLICY IF EXISTS "own" ON public.goal_milestones;
DROP POLICY IF EXISTS "own" ON public.daily_checkins;
DROP POLICY IF EXISTS "own" ON public.ai_reports;
DROP POLICY IF EXISTS "own" ON public.ai_conversations;
DROP POLICY IF EXISTS "own" ON public.future_self_letters;

CREATE POLICY "own" ON public.users FOR ALL USING (auth.uid() = id);
CREATE POLICY "own" ON public.habits FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.habit_logs FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.challenges FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.challenge_checkins FOR ALL USING (
  challenge_id IN (SELECT id FROM public.challenges WHERE user_id = auth.uid())
);
CREATE POLICY "own" ON public.goals FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.goal_milestones FOR ALL USING (
  goal_id IN (SELECT id FROM public.goals WHERE user_id = auth.uid())
);
CREATE POLICY "own" ON public.daily_checkins FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.ai_reports FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.ai_conversations FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.future_self_letters FOR ALL USING (auth.uid() = user_id);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_habit_logs_user_date ON public.habit_logs (user_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_habit_logs_habit_date ON public.habit_logs (habit_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_challenges_user ON public.challenges (user_id, status);
CREATE INDEX IF NOT EXISTS idx_goals_user ON public.goals (user_id);
CREATE INDEX IF NOT EXISTS idx_daily_checkins_user_date ON public.daily_checkins (user_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_ai_reports_user ON public.ai_reports (user_id, generated_at DESC);

-- ============================================================
-- STORAGE BUCKETS (create manually in Supabase Storage UI)
-- ============================================================
-- challenge-photos  (public)
-- log-photos        (public)
-- voice-notes       (private)
