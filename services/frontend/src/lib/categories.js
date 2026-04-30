const CATEGORY_ALIASES = {
  baby: 'Toys & Games',
}

export function normalizeCategoryName(category) {
  const value = String(category || '').trim()
  if (!value) return ''
  return CATEGORY_ALIASES[value.toLowerCase()] || value
}

export function normalizeCategories(categories = []) {
  const seen = new Set()
  const normalized = []
  for (const category of categories) {
    const value = normalizeCategoryName(category)
    if (!value || seen.has(value)) continue
    seen.add(value)
    normalized.push(value)
  }
  return normalized
}
