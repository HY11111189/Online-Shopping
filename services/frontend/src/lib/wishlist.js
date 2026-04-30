const KEY = 'shopping.wishlistItems'

export function loadWishlist() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '[]')
  } catch {
    return []
  }
}

export function saveWishlist(items) {
  localStorage.setItem(KEY, JSON.stringify(items))
}
