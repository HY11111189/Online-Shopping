const DAY_MS = 24 * 60 * 60 * 1000

function createdTimeOf(order) {
  return new Date(order?.createdAt || Date.now()).getTime()
}

function paidTimeOf(order) {
  return new Date(order?.createdAt || order?.paidAt || Date.now()).getTime()
}

export function canCancel(order) {
  if (!order) return false
  if (order.status === 'CREATED') {
    return Date.now() - createdTimeOf(order) <= DAY_MS
  }
  if (order.status === 'PAID') {
    return Date.now() - paidTimeOf(order) <= DAY_MS
  }
  return false
}

export function canRefund(order) {
  if (!order) return false
  if (order.status !== 'PAID') return false
  const age = Date.now() - paidTimeOf(order)
  return age > DAY_MS && age <= 7 * DAY_MS
}

export function orderAction(order) {
  if (canCancel(order)) {
    return 'cancel'
  }
  if (canRefund(order)) {
    return 'refund'
  }
  return null
}
