const KEY = 'shopping.checkoutSelection'

export function loadSelectedCartItems() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY) || '[]')
  } catch {
    return []
  }
}

export function saveSelectedCartItems(itemIds) {
  sessionStorage.setItem(KEY, JSON.stringify(itemIds))
}

export function clearSelectedCartItems() {
  sessionStorage.removeItem(KEY)
}
