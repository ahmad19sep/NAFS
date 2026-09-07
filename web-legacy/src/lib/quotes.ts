/**
 * A line for the day, chosen by how the day is actually going.
 *
 * CURATED, NOT GENERATED. Nothing here comes from a model. A misquoted ayah or
 * an invented hadith is far worse than no quote at all, so every entry is a
 * widely-known text in a standard English rendering, with its reference, and
 * the list is short enough to be checked by hand. Review it before relying on
 * it; add to it only from a source you have verified yourself.
 *
 * Faith mode on → Quran and hadith. Off → stoic and secular. The same shape
 * either way, so the rest of the app does not care which.
 *
 * Selection is deterministic by date: the line holds still for a day rather
 * than flickering on every render, and rotates tomorrow.
 */

export type Mood = 'struggling' | 'steady' | 'strong'

export interface Quote {
  text: string
  source: string
}

interface Entry extends Quote {
  mood: Mood
  faith: boolean
}

const ENTRIES: Entry[] = [
  // ── Faith · struggling ───────────────────────────────────────────────────
  { mood: 'struggling', faith: true,
    text: 'Indeed, with hardship comes ease.',
    source: 'Quran 94:6' },
  { mood: 'struggling', faith: true,
    text: 'Allah does not burden a soul beyond that it can bear.',
    source: 'Quran 2:286' },
  { mood: 'struggling', faith: true,
    text: 'Do not despair of the mercy of Allah.',
    source: 'Quran 39:53' },
  { mood: 'struggling', faith: true,
    text: 'And whoever relies upon Allah — then He is sufficient for him.',
    source: 'Quran 65:3' },
  { mood: 'struggling', faith: true,
    text: 'Strive for what benefits you, seek help from Allah, and do not give up.',
    source: 'Sahih Muslim 2664' },

  // ── Faith · steady ───────────────────────────────────────────────────────
  { mood: 'steady', faith: true,
    text: 'The most beloved deeds to Allah are those done consistently, even if they are small.',
    source: 'Bukhari & Muslim' },
  { mood: 'steady', faith: true,
    text: 'Indeed, Allah will not change the condition of a people until they change what is in themselves.',
    source: 'Quran 13:11' },
  { mood: 'steady', faith: true,
    text: 'And that there is not for man except that for which he strives.',
    source: 'Quran 53:39' },
  { mood: 'steady', faith: true,
    text: 'Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.',
    source: 'Al-Hakim' },

  // ── Faith · strong ───────────────────────────────────────────────────────
  { mood: 'strong', faith: true,
    text: 'If you are grateful, I will surely increase you.',
    source: 'Quran 14:7' },
  { mood: 'strong', faith: true,
    text: 'So whoever does an atom’s weight of good will see it.',
    source: 'Quran 99:7' },
  { mood: 'strong', faith: true,
    text: 'The strong believer is better and more beloved to Allah than the weak believer, while there is good in both.',
    source: 'Sahih Muslim 2664' },

  // ── Secular · struggling ─────────────────────────────────────────────────
  { mood: 'struggling', faith: false,
    text: 'The impediment to action advances action. What stands in the way becomes the way.',
    source: 'Marcus Aurelius, Meditations 5.20' },
  { mood: 'struggling', faith: false,
    text: 'It is not because things are difficult that we do not dare; it is because we do not dare that things are difficult.',
    source: 'Seneca, Letters 104' },
  { mood: 'struggling', faith: false,
    text: 'Fall seven times, stand up eight.',
    source: 'Japanese proverb' },

  // ── Secular · steady ─────────────────────────────────────────────────────
  { mood: 'steady', faith: false,
    text: 'We are what we repeatedly do. Excellence, then, is not an act but a habit.',
    source: 'Will Durant, on Aristotle' },
  { mood: 'steady', faith: false,
    text: 'Little by little, one travels far.',
    source: 'Proverb' },
  { mood: 'steady', faith: false,
    text: 'You have power over your mind, not outside events. Realise this, and you will find strength.',
    source: 'Marcus Aurelius, Meditations' },

  // ── Secular · strong ─────────────────────────────────────────────────────
  { mood: 'strong', faith: false,
    text: 'Progress is not achieved by luck or accident, but by working on yourself daily.',
    source: 'Epictetus, Discourses' },
  { mood: 'strong', faith: false,
    text: 'Luck is what happens when preparation meets opportunity.',
    source: 'Attributed to Seneca' },
]

/**
 * How the day is going, from what the app already knows.
 *
 * A broken streak or a low score reads as struggling; a high score as strong;
 * anything in between, including a day with nothing logged yet, is steady —
 * no verdict is passed on a day that has not been recorded.
 */
export function moodFor(input: {
  score: number | null
  streakJustBroken?: boolean
  nothingLoggedYet?: boolean
}): Mood {
  if (input.streakJustBroken) return 'struggling'
  if (input.nothingLoggedYet || input.score == null) return 'steady'
  if (input.score < 40) return 'struggling'
  if (input.score >= 75) return 'strong'
  return 'steady'
}

/** Small, stable hash of a string — enough to pick an index, no more. */
function hash(s: string): number {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619) >>> 0
  }
  return h
}

/**
 * The line for this mood and mode, held still for the given date.
 * Never throws: an empty pool falls back to the steady pool, and if somehow
 * that is empty too, returns null rather than inventing text.
 */
export function pickQuote(mood: Mood, faithMode: boolean, dateSeed: string): Quote | null {
  let pool = ENTRIES.filter((e) => e.mood === mood && e.faith === faithMode)
  if (pool.length === 0) pool = ENTRIES.filter((e) => e.mood === 'steady' && e.faith === faithMode)
  if (pool.length === 0) return null

  const e = pool[hash(`${dateSeed}|${mood}|${faithMode}`) % pool.length]
  return { text: e.text, source: e.source }
}

/** For tests and review: every entry, so the list can be audited. */
export function allQuotes(): readonly Entry[] {
  return ENTRIES
}
