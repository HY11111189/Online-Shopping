  const storageKeys = {
  token: 'shopping.token',
  username: 'shopping.username',
  expiresIn: 'shopping.expi    resIn',
  expiresAt: 'shopping.expiresAt',
  customerId: 'shopping.customerId',
  lastOrder: 'shopping.lastOrderNumber',
  lastPayment: 'shopping.lastPaymentNumber',
}

export function loadSession() {
  const expiresAt = Number(localStorage.getItem(storageKeys.expiresAt) || 0)
  if (expiresAt && Date.now() > expiresAt) {
    clearSession()
    return {
      token: '',
      username: '',
      expiresIn: 0,
      expiresAt: 0,
      customerId: 0,
      lastOrderNumber: '',
      lastPaymentNumber: '',
    }
  }
  return {
    token: localStorage.getItem(storageKeys.token) || '',
    username: localStorage.getItem(storageKeys.username) || '',
    expiresIn: Number(localStorage.getItem(storageKeys.expiresIn) || 0),
    expiresAt,
    customerId: Number(localStorage.getItem(storageKeys.customerId) || 0),
    lastOrderNumber: localStorage.getItem(storageKeys.lastOrder) || '',
    lastPaymentNumber: localStorage.getItem(storageKeys.lastPayment) || '',
  }
}

export function saveSession(session) {
  localStorage.setItem(storageKeys.token, session.token || '')
  localStorage.setItem(storageKeys.username, session.username || '')
  localStorage.setItem(storageKeys.expiresIn, String(session.expiresIn || 0))
  localStorage.setItem(storageKeys.expiresAt, String(session.expiresAt || 0))
  localStorage.setItem(storageKeys.customerId, String(session.customerId || 0))
  localStorage.setItem(storageKeys.lastOrder, session.lastOrderNumber || '')
  localStorage.setItem(storageKeys.lastPayment, session.lastPaymentNumber || '')
}

export function clearSession() {
  Object.values(storageKeys).forEach((key) => localStorage.removeItem(key))
}

export function isSessionActive(session) {
  return Boolean(session?.token) && (!session.expiresAt || Date.now() < session.expiresAt)
}

export function signinUrl(returnTo) {
  const fallback = '/index.html'
  const target = typeof returnTo === 'string' && returnTo.startsWith('/') ? returnTo : fallback
  return `/signin.html?returnTo=${encodeURIComponent(target)}`
}
