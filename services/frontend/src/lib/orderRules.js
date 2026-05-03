const DAY_MS = 24 * 60 * 60 * 1000

function orderCreatedTimeOf(order) {
  return new Date(order?.createdAt || Date.now()).getTime()
}

export function canCancel(order) {
  if (!order) return false
  if (order.status === 'CREATED') {
    return Date.now() - orderCreatedTimeOf(order) <= DAY_MS
  }
  if (order.status === 'PAID') {
    return Date.now() - orderCreatedTimeOf(order) <= DAY_MS
  }
  return false
}

export function canRefund(order) {
  if (!order) return false
  if (order.status !== 'PAID') return false
  const age = Date.now() - orderCreatedTimeOf(order)
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

export function orderActionLabel(order) {
  const action = orderAction(order)
  if (action === 'cancel') return 'Cancel order'
  if (action === 'refund') return 'Refund order'
  if (order?.status === 'CANCELLED') return 'Order cancelled'
  if (order?.status === 'REFUNDED') return 'Order refunded'
  return null
}
