'use client'

import { useMemo, useState } from 'react'
import { Plus, X, Search, Utensils } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  type Meal, type MealItem, type FoodOption,
  CORE_MEALS, EXTRA_MEAL_PRESETS, FOOD_CATEGORIES,
  makeId, isCoreMeal, mealsEaten, searchFood,
} from '@/lib/food'

export default function MealsCard({ meals, onChange }: {
  meals: Meal[]
  onChange: (next: Meal[]) => void
}) {
  // Which meal's food picker is open
  const [pickerFor, setPickerFor] = useState<string | null>(null)
  const [showAddMeal, setShowAddMeal] = useState(false)

  const eaten = mealsEaten(meals)

  function addItem(mealId: string, food: { name: string; emoji: string }) {
    onChange(meals.map((m) => m.id === mealId
      ? { ...m, items: [...m.items, { id: makeId('f'), name: food.name, emoji: food.emoji }] }
      : m))
  }
  function removeItem(mealId: string, itemId: string) {
    onChange(meals.map((m) => m.id === mealId
      ? { ...m, items: m.items.filter((i) => i.id !== itemId) }
      : m))
  }
  function addMeal(preset: { key: string; label: string; emoji: string }) {
    // Same meal twice in a day is fine (two snacks), so the key is reused but
    // the id is not.
    onChange([...meals, { id: makeId(preset.key), key: preset.key, label: preset.label, emoji: preset.emoji, items: [] }])
    setShowAddMeal(false)
  }
  function removeMeal(mealId: string) {
    onChange(meals.filter((m) => m.id !== mealId))
  }

  return (
    <div className="nafs-card p-5">
      {/* Header */}
      <div className="flex items-center gap-3 mb-4">
        <div className="h-9 w-9 rounded-xl border border-orange-400/20 bg-orange-500/10 flex items-center justify-center">
          <Utensils size={16} className={eaten > 0 ? 'text-orange-400' : 'text-muted-foreground'} />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-[15px] font-semibold text-foreground leading-tight">Meals</p>
          <p className="text-[11px] text-muted-foreground mt-0.5">what you ate today</p>
        </div>
        <p className="tabular-nums text-right">
          <span className={cn('text-2xl font-semibold', eaten > 0 ? 'text-orange-400' : 'text-foreground')}>
            {eaten}
          </span>
          <span className="text-sm text-muted-foreground"> {eaten === 1 ? 'time' : 'times'}</span>
        </p>
      </div>

      <div className="space-y-2.5">
        {meals.map((meal) => (
          <div key={meal.id} className={cn('rounded-xl border p-3 transition-colors',
            meal.items.length > 0
              ? 'border-orange-400/25 bg-orange-500/[0.07]'
              : 'border-white/10 bg-white/5'
          )}>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-base">{meal.emoji}</span>
              <p className="flex-1 text-sm font-semibold text-foreground">{meal.label}</p>
              {meal.items.length > 0 && (
                <span className="text-[10px] font-semibold uppercase tracking-wider text-orange-300">
                  {meal.items.length} item{meal.items.length === 1 ? '' : 's'}
                </span>
              )}
              {/* Breakfast, lunch and dinner are permanent */}
              {!isCoreMeal(meal.key) && (
                <button onClick={() => removeMeal(meal.id)} aria-label={`Remove ${meal.label}`}
                  className="h-6 w-6 rounded-md flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors">
                  <X size={11} />
                </button>
              )}
            </div>

            {meal.items.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mb-2">
                {meal.items.map((item) => (
                  <span key={item.id}
                    className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/[0.07] py-1 pl-2 pr-1 text-xs text-foreground">
                    <span>{item.emoji}</span>
                    {item.name}
                    <button onClick={() => removeItem(meal.id, item.id)} aria-label={`Remove ${item.name}`}
                      className="h-4 w-4 rounded-full flex items-center justify-center text-muted-foreground/60 hover:text-red-400 hover:bg-red-500/15 transition-colors">
                      <X size={9} />
                    </button>
                  </span>
                ))}
              </div>
            )}

            <button onClick={() => setPickerFor(meal.id)}
              className="w-full rounded-lg border border-dashed border-white/15 py-2 text-[11px] font-semibold
                         text-muted-foreground hover:text-orange-300 hover:border-orange-400/40 transition-all active:scale-95
                         flex items-center justify-center gap-1">
              <Plus size={11} />
              {meal.items.length === 0 ? 'Add food' : 'Add more'}
            </button>
          </div>
        ))}
      </div>

      {/* Extra meals */}
      {showAddMeal ? (
        <div className="mt-2.5 rounded-xl border border-white/10 bg-white/5 p-3">
          <div className="flex items-center justify-between mb-2">
            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Add another meal</p>
            <button onClick={() => setShowAddMeal(false)} className="text-muted-foreground/60 hover:text-foreground">
              <X size={12} />
            </button>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {EXTRA_MEAL_PRESETS.map((p) => (
              <button key={p.key} onClick={() => addMeal(p)}
                className="flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1.5
                           text-xs font-medium text-foreground hover:border-orange-400/40 hover:bg-orange-500/10 transition-all active:scale-95">
                <span>{p.emoji}</span>{p.label}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <button onClick={() => setShowAddMeal(true)}
          className="mt-2.5 w-full rounded-xl border border-dashed border-white/15 py-3 text-xs font-semibold
                     text-muted-foreground hover:text-orange-300 hover:border-orange-400/40 transition-all active:scale-95
                     flex items-center justify-center gap-1.5">
          <Plus size={13} /> Add another meal
        </button>
      )}

      {pickerFor && (
        <FoodPicker
          mealLabel={meals.find((m) => m.id === pickerFor)?.label ?? 'meal'}
          onPick={(food) => addItem(pickerFor, food)}
          onClose={() => setPickerFor(null)}
        />
      )}
    </div>
  )
}

// ============================================================
// Food picker — search the menu, or add anything not in it
// ============================================================
function FoodPicker({ mealLabel, onPick, onClose }: {
  mealLabel: string
  onPick: (food: { name: string; emoji: string }) => void
  onClose: () => void
}) {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | null>(null)
  const [justAdded, setJustAdded] = useState<string | null>(null)

  const results = useMemo(() => searchFood(query, category), [query, category])
  const typed = query.trim()
  // Offer "add as other" whenever what they typed isn't already an exact match
  const showAddOther = typed.length > 0 &&
    !results.some((f) => f.name.toLowerCase() === typed.toLowerCase())

  function pick(food: { name: string; emoji: string }) {
    onPick(food)
    setJustAdded(food.name)
    setQuery('')
    window.setTimeout(() => setJustAdded(null), 1200)
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col justify-end bg-black/60 backdrop-blur-sm"
      onClick={onClose}>
      <div className="max-h-[85vh] rounded-t-3xl border-t border-white/10 bg-[#0E1A2B] flex flex-col animate-slide-up"
        onClick={(e) => e.stopPropagation()}>

        {/* Header */}
        <div className="flex items-center gap-3 px-4 pt-4 pb-3 flex-shrink-0">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-foreground">Add to {mealLabel}</p>
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {justAdded ? `Added ${justAdded} ✓` : 'Tap to add — keep tapping for more'}
            </p>
          </div>
          <button onClick={onClose}
            className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 flex items-center justify-center
                       text-muted-foreground hover:text-foreground transition-colors">
            <X size={15} />
          </button>
        </div>

        {/* Search */}
        <div className="px-4 pb-3 flex-shrink-0">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input value={query} onChange={(e) => setQuery(e.target.value)}
              placeholder="Search naan, biryani, chai…" autoFocus
              className="log-input w-full pl-9 pr-3 text-sm" />
          </div>
        </div>

        {/* Categories */}
        <div className="px-4 pb-3 flex-shrink-0 overflow-x-auto">
          <div className="flex gap-1.5 w-max">
            <button onClick={() => setCategory(null)}
              className={cn('rounded-full border px-3 py-1.5 text-xs font-medium whitespace-nowrap transition-all',
                category === null
                  ? 'border-orange-400/50 bg-orange-500/15 text-orange-300'
                  : 'border-white/10 bg-white/5 text-muted-foreground'
              )}>
              All
            </button>
            {FOOD_CATEGORIES.map((c) => (
              <button key={c.id} onClick={() => setCategory(category === c.id ? null : c.id)}
                className={cn('flex items-center gap-1 rounded-full border px-3 py-1.5 text-xs font-medium whitespace-nowrap transition-all',
                  category === c.id
                    ? 'border-orange-400/50 bg-orange-500/15 text-orange-300'
                    : 'border-white/10 bg-white/5 text-muted-foreground'
                )}>
                <span>{c.emoji}</span>{c.label}
              </button>
            ))}
          </div>
        </div>

        {/* Results */}
        <div className="flex-1 overflow-y-auto px-4 pb-6">
          {showAddOther && (
            <button onClick={() => pick({ name: typed, emoji: '🍽️' })}
              className="mb-3 w-full rounded-xl border border-gold/40 bg-gold/10 p-3 text-left flex items-center gap-2.5
                         hover:bg-gold/15 transition-all active:scale-95">
              <span className="text-lg">🍽️</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gold">Add &ldquo;{typed}&rdquo;</p>
                <p className="text-[10px] text-muted-foreground">not in the menu — add it as your own</p>
              </div>
              <Plus size={14} className="text-gold" />
            </button>
          )}

          {results.length === 0 && !showAddOther && (
            <p className="py-8 text-center text-sm text-muted-foreground">Nothing here — try another search.</p>
          )}

          <div className="grid grid-cols-2 gap-2">
            {results.map((f: FoodOption) => (
              <button key={`${f.cat}-${f.name}`} onClick={() => pick(f)}
                className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 p-2.5 text-left
                           hover:border-orange-400/40 hover:bg-orange-500/10 transition-all active:scale-95">
                <span className="text-lg flex-shrink-0">{f.emoji}</span>
                <span className="text-xs font-medium text-foreground leading-tight">{f.name}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export { CORE_MEALS }
