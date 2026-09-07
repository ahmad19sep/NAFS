/**
 * Minimal runtime validator for model output — AI-01 from the Improvement
 * Blueprint.
 *
 * A TypeScript generic is a compile-time fiction: `aiJSON<StarterPack>()`
 * asserts a shape nobody checked. An open model will occasionally return the
 * right JSON with the wrong fields, a string where a number belongs, or an
 * enum value that doesn't exist. This checks the value that actually arrived.
 *
 * Hand-rolled rather than pulling in a validation library: the shapes here are
 * small, and the blueprint asks for no unnecessary dependencies.
 */

export type Schema =
  | { kind: 'string'; minLength?: number; maxLength?: number }
  | { kind: 'number'; min?: number; max?: number; int?: boolean }
  | { kind: 'boolean' }
  | { kind: 'enum'; values: readonly string[] }
  | { kind: 'array'; of: Schema; minItems?: number; maxItems?: number }
  | { kind: 'object'; fields: Record<string, Schema>; optional?: readonly string[] }
  | { kind: 'nullable'; of: Schema }

export type ValidationResult<T> =
  | { ok: true; value: T }
  | { ok: false; errors: string[] }

function check(value: unknown, schema: Schema, path: string, errors: string[]): void {
  switch (schema.kind) {
    case 'nullable':
      if (value === null || value === undefined) return
      return check(value, schema.of, path, errors)

    case 'string': {
      if (typeof value !== 'string') {
        errors.push(`${path}: expected string, got ${typeName(value)}`)
        return
      }
      if (schema.minLength != null && value.length < schema.minLength) {
        errors.push(`${path}: shorter than ${schema.minLength}`)
      }
      if (schema.maxLength != null && value.length > schema.maxLength) {
        errors.push(`${path}: longer than ${schema.maxLength}`)
      }
      return
    }

    case 'number': {
      // Reject NaN and Infinity outright — both survive typeof 'number'.
      if (typeof value !== 'number' || !Number.isFinite(value)) {
        errors.push(`${path}: expected number, got ${typeName(value)}`)
        return
      }
      if (schema.int && !Number.isInteger(value)) errors.push(`${path}: expected an integer`)
      if (schema.min != null && value < schema.min) errors.push(`${path}: below ${schema.min}`)
      if (schema.max != null && value > schema.max) errors.push(`${path}: above ${schema.max}`)
      return
    }

    case 'boolean':
      if (typeof value !== 'boolean') errors.push(`${path}: expected boolean, got ${typeName(value)}`)
      return

    case 'enum':
      if (typeof value !== 'string' || !schema.values.includes(value)) {
        errors.push(`${path}: not one of ${schema.values.join(' | ')}`)
      }
      return

    case 'array': {
      if (!Array.isArray(value)) {
        errors.push(`${path}: expected array, got ${typeName(value)}`)
        return
      }
      if (schema.minItems != null && value.length < schema.minItems) {
        errors.push(`${path}: fewer than ${schema.minItems} items`)
      }
      if (schema.maxItems != null && value.length > schema.maxItems) {
        errors.push(`${path}: more than ${schema.maxItems} items`)
      }
      value.forEach((item, i) => check(item, schema.of, `${path}[${i}]`, errors))
      return
    }

    case 'object': {
      if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        errors.push(`${path}: expected object, got ${typeName(value)}`)
        return
      }
      const optional = new Set(schema.optional ?? [])
      const record = value as Record<string, unknown>
      for (const [key, fieldSchema] of Object.entries(schema.fields)) {
        const present = key in record && record[key] !== undefined
        if (!present) {
          if (!optional.has(key)) errors.push(`${path}.${key}: missing`)
          continue
        }
        check(record[key], fieldSchema, `${path}.${key}`, errors)
      }
      // Unknown extra keys are tolerated: a model adding commentary fields
      // isn't a reason to discard otherwise-valid output.
      return
    }
  }
}

function typeName(v: unknown): string {
  if (v === null) return 'null'
  if (Array.isArray(v)) return 'array'
  return typeof v
}

export function validate<T>(value: unknown, schema: Schema): ValidationResult<T> {
  const errors: string[] = []
  check(value, schema, 'root', errors)
  // Cap the report so a wildly wrong payload can't produce a huge retry prompt.
  return errors.length === 0
    ? { ok: true, value: value as T }
    : { ok: false, errors: errors.slice(0, 8) }
}

/**
 * Parse model output as JSON.
 *
 * Strips at most one complete surrounding markdown fence, which models add
 * routinely. It deliberately does NOT hunt for the first `{` and last `}`:
 * that "repair" silently turns prose containing a brace into a confident
 * wrong object. Malformed output should fail and be retried, not guessed at.
 */
export function parseJson(raw: string): { ok: true; value: unknown } | { ok: false; error: string } {
  if (!raw || !raw.trim()) return { ok: false, error: 'empty response' }

  let text = raw.trim()
  const fence = /^```(?:json)?\s*\n([\s\S]*?)\n?```$/i.exec(text)
  if (fence) text = fence[1].trim()

  try {
    return { ok: true, value: JSON.parse(text) }
  } catch {
    // One narrow repair, then give up. See repairStrayObjectQuotes.
    const repaired = repairStrayObjectQuotes(text)
    if (repaired !== null) {
      try {
        return { ok: true, value: JSON.parse(repaired) }
      } catch {
        // fall through
      }
    }
    return { ok: false, error: 'not valid JSON' }
  }
}

/**
 * Undo one specific malformation the free model produces, and nothing else.
 *
 * gpt-oss-20b intermittently wraps an object inside an array in quotes, so a
 * list of steps comes back as `[{...}, "{...}]` — a stray double quote after
 * the comma. Observed twice in live testing on plans of three or more steps;
 * the content was correct both times and only the punctuation was wrong.
 *
 * The repair is purely syntactic: remove a double quote that sits between a
 * comma or bracket and an opening brace, and its partner immediately after
 * the matching closing brace. It never edits content, never guesses a value,
 * and the result still has to parse — if it does not, the caller's corrective
 * retry runs as before. Returns null when there is nothing of this shape.
 */
export function repairStrayObjectQuotes(text: string): string | null {
  // Only attempt this on something that looks like the shape it breaks.
  if (!/[,[]\s*"\s*\{/.test(text)) return null

  const out: string[] = []
  let inString = false
  let escaped = false
  let repairs = 0
  // The last character written that was not whitespace. Tracked rather than
  // re-joining `out` each time, which would make this quadratic.
  let prevMeaningful = ''

  const push = (ch: string) => {
    out.push(ch)
    if (!/\s/.test(ch)) prevMeaningful = ch
  }

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]

    if (inString) {
      push(ch)
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === '"') inString = false
      continue
    }

    if (ch === '"') {
      // A quote opening an object inside an array: drop it.
      if (/^\s*\{/.test(text.slice(i + 1)) && (prevMeaningful === ',' || prevMeaningful === '[')) {
        repairs++
        continue
      }
      // Its partner, closing that object: drop it too.
      if (repairs > 0 && prevMeaningful === '}' && /^\s*[,\]]/.test(text.slice(i + 1))) {
        repairs++
        continue
      }
      inString = true
      push(ch)
      continue
    }

    push(ch)
  }

  return repairs > 0 ? out.join('') : null
}
