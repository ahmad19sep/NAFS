export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export interface Database {
  public: {
    Tables: {
      users: {
        Row: {
          id: string
          name: string
          email: string
          avatar_url: string | null
          dream_id: string | null
          timezone: string
          onboarding_complete: boolean
          push_subscription: Json | null
          created_at: string
        }
        Insert: {
          id: string
          name: string
          email: string
          avatar_url?: string | null
          dream_id?: string | null
          timezone?: string
          onboarding_complete?: boolean
          push_subscription?: Json | null
          created_at?: string
        }
        Update: {
          id?: string
          name?: string
          email?: string
          avatar_url?: string | null
          dream_id?: string | null
          timezone?: string
          onboarding_complete?: boolean
          push_subscription?: Json | null
          created_at?: string
        }
      }
      dreams: {
        Row: {
          id: string
          user_id: string
          statement: string
          dream_date: string
          image_url: string | null
          why: string | null
          total_hours_required: number
          public_board_visible: boolean
          created_at: string
          updated_at: string
        }
        Insert: {
          id?: string
          user_id: string
          statement: string
          dream_date: string
          image_url?: string | null
          why?: string | null
          total_hours_required: number
          public_board_visible?: boolean
          created_at?: string
          updated_at?: string
        }
        Update: {
          id?: string
          user_id?: string
          statement?: string
          dream_date?: string
          image_url?: string | null
          why?: string | null
          total_hours_required?: number
          public_board_visible?: boolean
          updated_at?: string
        }
      }
      activity_weights: {
        Row: {
          id: string
          dream_id: string
          activity_name: string
          weight_multiplier: number
          category: string
          created_at: string
        }
        Insert: {
          id?: string
          dream_id: string
          activity_name: string
          weight_multiplier: number
          category: string
          created_at?: string
        }
        Update: {
          activity_name?: string
          weight_multiplier?: number
          category?: string
        }
      }
      daily_logs: {
        Row: {
          id: string
          user_id: string
          date: string
          prayers: Json
          dream_work_hours: number
          study_hours: number
          screen_time_mins: number
          sleep_start: string | null
          sleep_end: string | null
          mood: number
          reflection_text: string | null
          voice_note_url: string | null
          photo_urls: string[]
          weighted_hours_today: number
          identity_score: number
          todays_pull_days: number
          created_at: string
          updated_at: string
        }
        Insert: {
          id?: string
          user_id: string
          date: string
          prayers?: Json
          dream_work_hours?: number
          study_hours?: number
          screen_time_mins?: number
          sleep_start?: string | null
          sleep_end?: string | null
          mood?: number
          reflection_text?: string | null
          voice_note_url?: string | null
          photo_urls?: string[]
          weighted_hours_today?: number
          identity_score?: number
          todays_pull_days?: number
          created_at?: string
          updated_at?: string
        }
        Update: {
          prayers?: Json
          dream_work_hours?: number
          study_hours?: number
          screen_time_mins?: number
          sleep_start?: string | null
          sleep_end?: string | null
          mood?: number
          reflection_text?: string | null
          voice_note_url?: string | null
          photo_urls?: string[]
          weighted_hours_today?: number
          identity_score?: number
          todays_pull_days?: number
          updated_at?: string
        }
      }
      challenges: {
        Row: {
          id: string
          user_id: string
          title: string
          duration_days: number
          start_date: string
          sadqa_amount: number | null
          sadqa_currency: string
          current_streak: number
          longest_streak: number
          status: 'active' | 'completed' | 'paused'
          created_at: string
        }
        Insert: {
          id?: string
          user_id: string
          title: string
          duration_days: number
          start_date: string
          sadqa_amount?: number | null
          sadqa_currency?: string
          current_streak?: number
          longest_streak?: number
          status?: 'active' | 'completed' | 'paused'
          created_at?: string
        }
        Update: {
          title?: string
          duration_days?: number
          sadqa_amount?: number | null
          sadqa_currency?: string
          current_streak?: number
          longest_streak?: number
          status?: 'active' | 'completed' | 'paused'
        }
      }
      challenge_checkins: {
        Row: {
          id: string
          challenge_id: string
          date: string
          completed: boolean
          broken_reason: string | null
          sadqa_paid: boolean
          sadqa_receipt_url: string | null
          created_at: string
        }
        Insert: {
          id?: string
          challenge_id: string
          date: string
          completed: boolean
          broken_reason?: string | null
          sadqa_paid?: boolean
          sadqa_receipt_url?: string | null
          created_at?: string
        }
        Update: {
          completed?: boolean
          broken_reason?: string | null
          sadqa_paid?: boolean
          sadqa_receipt_url?: string | null
        }
      }
      future_self_letters: {
        Row: {
          id: string
          user_id: string
          content: string
          written_at: string
          target_deliver_date: string
          delivered_at: string | null
          ai_reply_text: string | null
        }
        Insert: {
          id?: string
          user_id: string
          content: string
          written_at?: string
          target_deliver_date: string
          delivered_at?: string | null
          ai_reply_text?: string | null
        }
        Update: {
          delivered_at?: string | null
          ai_reply_text?: string | null
        }
      }
      ai_reports: {
        Row: {
          id: string
          user_id: string
          type: 'tribunal' | 'pull' | 'gap' | 'letter_reply'
          week_start: string | null
          content_md: string
          generated_at: string
          model_used: string
        }
        Insert: {
          id?: string
          user_id: string
          type: 'tribunal' | 'pull' | 'gap' | 'letter_reply'
          week_start?: string | null
          content_md: string
          generated_at?: string
          model_used?: string
        }
        Update: {
          content_md?: string
        }
      }
      ai_conversations: {
        Row: {
          id: string
          user_id: string
          messages: Json
          created_at: string
        }
        Insert: {
          id?: string
          user_id: string
          messages: Json
          created_at?: string
        }
        Update: {
          messages?: Json
        }
      }
      quran_log: {
        Row: {
          id: string
          user_id: string
          date: string
          pages_read: number
          surah: string | null
          ayah_from: number | null
          ayah_to: number | null
          notes: string | null
          created_at: string
        }
        Insert: {
          id?: string
          user_id: string
          date: string
          pages_read: number
          surah?: string | null
          ayah_from?: number | null
          ayah_to?: number | null
          notes?: string | null
          created_at?: string
        }
        Update: {
          pages_read?: number
          surah?: string | null
          ayah_from?: number | null
          ayah_to?: number | null
          notes?: string | null
        }
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      [_ in never]: never
    }
    Enums: {
      [_ in never]: never
    }
  }
}
