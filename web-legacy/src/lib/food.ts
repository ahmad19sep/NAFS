// Meals eaten per day, and the food menu behind the picker.
//
// Breakfast, lunch and dinner always exist and cannot be removed; anything else
// (snack, sehri, iftar, tea) is added on top. Meals live in health_logs.meals.

export interface MealItem {
  id: string
  name: string
  emoji: string
}

export interface Meal {
  id: string
  key: string          // 'breakfast' | 'lunch' | 'dinner' | custom slug
  label: string
  emoji: string
  items: MealItem[]
}

/** The three meals that are always present, in the order they're eaten. */
export const CORE_MEALS = [
  { key: 'breakfast', label: 'Breakfast', emoji: '🌅' },
  { key: 'lunch',     label: 'Lunch',     emoji: '☀️' },
  { key: 'dinner',    label: 'Dinner',    emoji: '🌙' },
] as const

/** Extra meals offered when adding one, beyond the three core meals. */
export const EXTRA_MEAL_PRESETS = [
  { key: 'snack',     label: 'Snack',      emoji: '🍿' },
  { key: 'brunch',    label: 'Brunch',     emoji: '🥐' },
  { key: 'tea',       label: 'Tea time',   emoji: '🫖' },
  { key: 'sehri',     label: 'Sehri',      emoji: '🌒' },
  { key: 'iftar',     label: 'Iftar',      emoji: '🌆' },
  { key: 'latenight', label: 'Late night', emoji: '🌃' },
] as const

export function makeId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
}

/**
 * Guarantees breakfast/lunch/dinner exist and lead the list, keeping whatever
 * was already logged in them, with extra meals following in their saved order.
 */
export function ensureCoreMeals(stored: Meal[] | null | undefined): Meal[] {
  const existing = Array.isArray(stored) ? stored : []
  const core = CORE_MEALS.map((c) => {
    const found = existing.find((m) => m.key === c.key)
    return {
      id: found?.id ?? makeId(c.key),
      key: c.key,
      label: c.label,
      emoji: c.emoji,
      items: Array.isArray(found?.items) ? found!.items : [],
    }
  })
  const extras = existing
    .filter((m) => !CORE_MEALS.some((c) => c.key === m.key))
    .map((m) => ({ ...m, items: Array.isArray(m.items) ? m.items : [] }))
  return [...core, ...extras]
}

export function isCoreMeal(key: string): boolean {
  return CORE_MEALS.some((c) => c.key === key)
}

/** How many times you actually ate — meals with at least one food logged. */
export function mealsEaten(meals: Meal[]): number {
  return meals.filter((m) => m.items.length > 0).length
}

export function totalFoodItems(meals: Meal[]): number {
  return meals.reduce((n, m) => n + m.items.length, 0)
}

// ─── Food menu ────────────────────────────────────────────────────────────────

export interface FoodCategory {
  id: string
  label: string
  emoji: string
}

export const FOOD_CATEGORIES: FoodCategory[] = [
  { id: 'bread',     label: 'Roti & Bread', emoji: '🫓' },
  { id: 'curry',     label: 'Salan & Curry', emoji: '🍛' },
  { id: 'daal',      label: 'Daal & Sabzi', emoji: '🫘' },
  { id: 'rice',      label: 'Rice',         emoji: '🍚' },
  { id: 'breakfast', label: 'Breakfast',    emoji: '🍳' },
  { id: 'meat',      label: 'Meat & BBQ',   emoji: '🍖' },
  { id: 'fastfood',  label: 'Fast food',    emoji: '🍔' },
  { id: 'snack',     label: 'Snacks',       emoji: '🍿' },
  { id: 'sweet',     label: 'Sweets',       emoji: '🍰' },
  { id: 'fruit',     label: 'Fruits',       emoji: '🍎' },
  { id: 'drink',     label: 'Beverages',    emoji: '☕' },
  { id: 'juice',     label: 'Juices & shakes', emoji: '🥤' },
  { id: 'dairy',     label: 'Dairy & eggs', emoji: '🥚' },
  { id: 'side',      label: 'Sides & salad', emoji: '🥗' },
]

export interface FoodOption {
  name: string
  emoji: string
  cat: string
  /** extra words matched by search, e.g. urdu names */
  alt?: string
}

export const FOOD_MENU: FoodOption[] = [
  // Roti & bread
  { name: 'Roti',            emoji: '🫓', cat: 'bread', alt: 'chapati phulka' },
  { name: 'Tandoori Roti',   emoji: '🫓', cat: 'bread' },
  { name: 'Naan',            emoji: '🫓', cat: 'bread' },
  { name: 'Garlic Naan',     emoji: '🫓', cat: 'bread' },
  { name: 'Kulcha',          emoji: '🫓', cat: 'bread' },
  { name: 'Paratha',         emoji: '🥞', cat: 'bread' },
  { name: 'Aloo Paratha',    emoji: '🥞', cat: 'bread', alt: 'potato' },
  { name: 'Puri',            emoji: '🫓', cat: 'bread' },
  { name: 'Sheermal',        emoji: '🫓', cat: 'bread' },
  { name: 'Taftan',          emoji: '🫓', cat: 'bread' },
  { name: 'Bread Slice',     emoji: '🍞', cat: 'bread', alt: 'double roti' },
  { name: 'Toast',           emoji: '🍞', cat: 'bread' },
  { name: 'Bun',             emoji: '🥐', cat: 'bread' },
  { name: 'Rusk',            emoji: '🍞', cat: 'bread' },

  // Salan & curry
  { name: 'Chicken Salan',   emoji: '🍛', cat: 'curry', alt: 'murgh' },
  { name: 'Chicken Karahi',  emoji: '🍛', cat: 'curry' },
  { name: 'Chicken Handi',   emoji: '🍛', cat: 'curry' },
  { name: 'Qorma',           emoji: '🍛', cat: 'curry', alt: 'korma' },
  { name: 'Mutton Salan',    emoji: '🍛', cat: 'curry', alt: 'bakra goat' },
  { name: 'Mutton Karahi',   emoji: '🍛', cat: 'curry' },
  { name: 'Beef Salan',      emoji: '🍛', cat: 'curry' },
  { name: 'Aloo Gosht',      emoji: '🍛', cat: 'curry', alt: 'potato meat' },
  { name: 'Nihari',          emoji: '🍲', cat: 'curry' },
  { name: 'Paye',            emoji: '🍲', cat: 'curry', alt: 'siri paye trotters' },
  { name: 'Haleem',          emoji: '🍲', cat: 'curry' },
  { name: 'Keema',           emoji: '🍛', cat: 'curry', alt: 'mince' },
  { name: 'Chana Masala',    emoji: '🍛', cat: 'curry', alt: 'channay chickpea' },
  { name: 'Fish Curry',      emoji: '🐟', cat: 'curry' },
  { name: 'Egg Curry',       emoji: '🍛', cat: 'curry', alt: 'anda' },

  // Daal & sabzi
  { name: 'Daal Chawal',     emoji: '🍚', cat: 'daal', alt: 'lentil rice' },
  { name: 'Daal Mash',       emoji: '🫘', cat: 'daal' },
  { name: 'Daal Chana',      emoji: '🫘', cat: 'daal' },
  { name: 'Daal Masoor',     emoji: '🫘', cat: 'daal' },
  { name: 'Daal Moong',      emoji: '🫘', cat: 'daal' },
  { name: 'Bhindi',          emoji: '🥬', cat: 'daal', alt: 'okra' },
  { name: 'Karela',          emoji: '🥒', cat: 'daal', alt: 'bitter gourd' },
  { name: 'Aloo Matar',      emoji: '🥔', cat: 'daal', alt: 'potato peas' },
  { name: 'Palak',           emoji: '🥬', cat: 'daal', alt: 'spinach saag' },
  { name: 'Saag',            emoji: '🥬', cat: 'daal', alt: 'sarson' },
  { name: 'Baingan',         emoji: '🍆', cat: 'daal', alt: 'brinjal eggplant' },
  { name: 'Kaddu',           emoji: '🎃', cat: 'daal', alt: 'pumpkin' },
  { name: 'Gobhi',           emoji: '🥦', cat: 'daal', alt: 'cauliflower' },
  { name: 'Mix Sabzi',       emoji: '🥗', cat: 'daal', alt: 'vegetable' },
  { name: 'Paneer',          emoji: '🧀', cat: 'daal', alt: 'shahi cheese' },

  // Rice
  { name: 'Plain Rice',      emoji: '🍚', cat: 'rice', alt: 'chawal boiled' },
  { name: 'Chicken Biryani', emoji: '🍛', cat: 'rice' },
  { name: 'Beef Biryani',    emoji: '🍛', cat: 'rice' },
  { name: 'Mutton Biryani',  emoji: '🍛', cat: 'rice' },
  { name: 'Pulao',           emoji: '🍚', cat: 'rice', alt: 'pilaf yakhni' },
  { name: 'Fried Rice',      emoji: '🍚', cat: 'rice' },
  { name: 'Kabuli Pulao',    emoji: '🍚', cat: 'rice' },

  // Breakfast
  { name: 'Halwa Puri',      emoji: '🍽️', cat: 'breakfast' },
  { name: 'Omelette',        emoji: '🍳', cat: 'breakfast', alt: 'anda egg' },
  { name: 'Fried Egg',       emoji: '🍳', cat: 'breakfast', alt: 'anda' },
  { name: 'Boiled Egg',      emoji: '🥚', cat: 'breakfast', alt: 'anda' },
  { name: 'Channay',         emoji: '🫘', cat: 'breakfast', alt: 'chana chickpea' },
  { name: 'Cornflakes',      emoji: '🥣', cat: 'breakfast', alt: 'cereal' },
  { name: 'Oats',            emoji: '🥣', cat: 'breakfast', alt: 'porridge dalia' },
  { name: 'Honey',           emoji: '🍯', cat: 'breakfast', alt: 'shehad' },
  { name: 'Jam',             emoji: '🍓', cat: 'breakfast' },
  { name: 'Butter',          emoji: '🧈', cat: 'breakfast', alt: 'makhan' },
  { name: 'Sooji Halwa',     emoji: '🍮', cat: 'breakfast' },

  // Meat & BBQ
  { name: 'Chicken Tikka',   emoji: '🍗', cat: 'meat' },
  { name: 'Seekh Kabab',     emoji: '🍢', cat: 'meat' },
  { name: 'Chapli Kabab',    emoji: '🍔', cat: 'meat' },
  { name: 'Shami Kabab',     emoji: '🍢', cat: 'meat' },
  { name: 'Malai Boti',      emoji: '🍗', cat: 'meat' },
  { name: 'Chicken Roast',   emoji: '🍗', cat: 'meat' },
  { name: 'Fried Chicken',   emoji: '🍗', cat: 'meat' },
  { name: 'Fried Fish',      emoji: '🐟', cat: 'meat' },
  { name: 'Beef Steak',      emoji: '🥩', cat: 'meat' },
  { name: 'Mutton Chops',    emoji: '🍖', cat: 'meat' },

  // Fast food
  { name: 'Burger',          emoji: '🍔', cat: 'fastfood' },
  { name: 'Zinger Burger',   emoji: '🍔', cat: 'fastfood' },
  { name: 'Pizza',           emoji: '🍕', cat: 'fastfood' },
  { name: 'Shawarma',        emoji: '🌯', cat: 'fastfood' },
  { name: 'Paratha Roll',    emoji: '🌯', cat: 'fastfood' },
  { name: 'French Fries',    emoji: '🍟', cat: 'fastfood', alt: 'chips' },
  { name: 'Sandwich',        emoji: '🥪', cat: 'fastfood' },
  { name: 'Club Sandwich',   emoji: '🥪', cat: 'fastfood' },
  { name: 'Hot Dog',         emoji: '🌭', cat: 'fastfood' },
  { name: 'Pasta',           emoji: '🍝', cat: 'fastfood' },
  { name: 'Noodles',         emoji: '🍜', cat: 'fastfood', alt: 'chowmein' },
  { name: 'Nuggets',         emoji: '🍗', cat: 'fastfood' },
  { name: 'Broast',          emoji: '🍗', cat: 'fastfood' },
  { name: 'Spring Roll',     emoji: '🥟', cat: 'fastfood' },
  { name: 'Wrap',            emoji: '🌯', cat: 'fastfood' },

  // Snacks
  { name: 'Samosa',          emoji: '🥟', cat: 'snack' },
  { name: 'Pakora',          emoji: '🧆', cat: 'snack' },
  { name: 'Dahi Bhalay',     emoji: '🥣', cat: 'snack' },
  { name: 'Gol Gappay',      emoji: '🥟', cat: 'snack', alt: 'pani puri' },
  { name: 'Chaat',           emoji: '🥗', cat: 'snack' },
  { name: 'Chips',           emoji: '🍟', cat: 'snack', alt: 'crisps lays' },
  { name: 'Biscuits',        emoji: '🍪', cat: 'snack' },
  { name: 'Popcorn',         emoji: '🍿', cat: 'snack' },
  { name: 'Peanuts',         emoji: '🥜', cat: 'snack', alt: 'moongphali' },
  { name: 'Almonds',         emoji: '🌰', cat: 'snack', alt: 'badam' },
  { name: 'Dry Fruit',       emoji: '🌰', cat: 'snack' },

  // Sweets
  { name: 'Kheer',           emoji: '🍮', cat: 'sweet' },
  { name: 'Firni',           emoji: '🍮', cat: 'sweet' },
  { name: 'Gulab Jamun',     emoji: '🍡', cat: 'sweet' },
  { name: 'Jalebi',          emoji: '🍥', cat: 'sweet' },
  { name: 'Gajar Halwa',     emoji: '🍮', cat: 'sweet', alt: 'carrot' },
  { name: 'Barfi',           emoji: '🍬', cat: 'sweet' },
  { name: 'Ras Malai',       emoji: '🍮', cat: 'sweet' },
  { name: 'Ice Cream',       emoji: '🍨', cat: 'sweet' },
  { name: 'Cake',            emoji: '🍰', cat: 'sweet' },
  { name: 'Pastry',          emoji: '🧁', cat: 'sweet' },
  { name: 'Chocolate',       emoji: '🍫', cat: 'sweet' },
  { name: 'Custard',         emoji: '🍮', cat: 'sweet' },
  { name: 'Sheer Khurma',    emoji: '🍮', cat: 'sweet' },

  // Fruits
  { name: 'Mango',           emoji: '🥭', cat: 'fruit', alt: 'aam' },
  { name: 'Banana',          emoji: '🍌', cat: 'fruit', alt: 'kela' },
  { name: 'Apple',           emoji: '🍎', cat: 'fruit', alt: 'saib' },
  { name: 'Orange',          emoji: '🍊', cat: 'fruit', alt: 'kinnow malta' },
  { name: 'Grapes',          emoji: '🍇', cat: 'fruit', alt: 'angoor' },
  { name: 'Watermelon',      emoji: '🍉', cat: 'fruit', alt: 'tarbooz' },
  { name: 'Melon',           emoji: '🍈', cat: 'fruit', alt: 'kharbooza' },
  { name: 'Guava',           emoji: '🍐', cat: 'fruit', alt: 'amrood' },
  { name: 'Papaya',          emoji: '🍈', cat: 'fruit' },
  { name: 'Pomegranate',     emoji: '🍎', cat: 'fruit', alt: 'anar' },
  { name: 'Dates',           emoji: '🌴', cat: 'fruit', alt: 'khajoor khajur' },
  { name: 'Strawberry',      emoji: '🍓', cat: 'fruit' },
  { name: 'Pineapple',       emoji: '🍍', cat: 'fruit' },
  { name: 'Peach',           emoji: '🍑', cat: 'fruit', alt: 'aaru' },
  { name: 'Apricot',         emoji: '🍑', cat: 'fruit', alt: 'khubani' },
  { name: 'Plum',            emoji: '🍑', cat: 'fruit', alt: 'aloo bukhara' },

  // Beverages
  { name: 'Chai',            emoji: '☕', cat: 'drink', alt: 'tea doodh patti' },
  { name: 'Green Tea',       emoji: '🍵', cat: 'drink', alt: 'qahwa kahwa' },
  { name: 'Coffee',          emoji: '☕', cat: 'drink' },
  { name: 'Milk',            emoji: '🥛', cat: 'drink', alt: 'doodh' },
  { name: 'Lassi',           emoji: '🥛', cat: 'drink' },
  { name: 'Water',           emoji: '💧', cat: 'drink', alt: 'pani' },
  { name: 'Soft Drink',      emoji: '🥤', cat: 'drink', alt: 'coke pepsi sprite cola' },
  { name: 'Energy Drink',    emoji: '🥤', cat: 'drink' },
  { name: 'Rooh Afza',       emoji: '🥤', cat: 'drink', alt: 'sharbat' },
  { name: 'Sattu',           emoji: '🥤', cat: 'drink' },

  // Juices & shakes
  { name: 'Orange Juice',    emoji: '🧃', cat: 'juice' },
  { name: 'Apple Juice',     emoji: '🧃', cat: 'juice' },
  { name: 'Sugarcane Juice', emoji: '🧃', cat: 'juice', alt: 'ganne ka ras' },
  { name: 'Mango Shake',     emoji: '🥤', cat: 'juice' },
  { name: 'Banana Shake',    emoji: '🥤', cat: 'juice' },
  { name: 'Milkshake',       emoji: '🥤', cat: 'juice' },
  { name: 'Lemonade',        emoji: '🍋', cat: 'juice', alt: 'shikanjabeen nimbu pani' },
  { name: 'Falooda',         emoji: '🍨', cat: 'juice' },
  { name: 'Smoothie',        emoji: '🥤', cat: 'juice' },
  { name: 'Coconut Water',   emoji: '🥥', cat: 'juice' },

  // Dairy & eggs
  { name: 'Dahi',            emoji: '🥣', cat: 'dairy', alt: 'yogurt curd' },
  { name: 'Raita',           emoji: '🥣', cat: 'dairy' },
  { name: 'Cheese',          emoji: '🧀', cat: 'dairy' },
  { name: 'Cream',           emoji: '🥛', cat: 'dairy', alt: 'malai' },
  { name: 'Egg',             emoji: '🥚', cat: 'dairy', alt: 'anda' },

  // Sides & salad
  { name: 'Salad',           emoji: '🥗', cat: 'side' },
  { name: 'Achar',           emoji: '🥒', cat: 'side', alt: 'pickle' },
  { name: 'Chutney',         emoji: '🥣', cat: 'side' },
  { name: 'Ketchup',         emoji: '🍅', cat: 'side' },
  { name: 'Papad',           emoji: '🫓', cat: 'side' },
  { name: 'Soup',            emoji: '🍲', cat: 'side' },
]

/** Menu search across name and the alt spellings, newest-typed query wins. */
export function searchFood(query: string, category: string | null): FoodOption[] {
  const q = query.trim().toLowerCase()
  let list = FOOD_MENU
  if (category) list = list.filter((f) => f.cat === category)
  if (!q) return list
  return list.filter((f) =>
    f.name.toLowerCase().includes(q) || (f.alt ? f.alt.includes(q) : false)
  )
}
