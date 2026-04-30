export function money(value, currency = 'USD') {
  const amount = Number(value || 0)
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount)
}

export function compactAddress(address) {
  if (!address) return 'Not available'
  return [address.recipientName, [address.city, address.state].filter(Boolean).join(', '), address.postalCode].filter(Boolean).join(' • ')
}

export function fullAddress(address) {
  if (!address) return 'Not available'
  return [
    address.recipientName,
    address.addressLine1,
    address.addressLine2,
    [address.city, address.state, address.postalCode].filter(Boolean).join(', '),
    address.country,
  ].filter(Boolean).join(', ')
}

export function dateLabel(value) {
  if (!value) return 'Pending'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}

export function originalPriceFromDiscount(currentValue, discountPercent) {
  const current = Number(currentValue || 0)
  const discount = Number(discountPercent || 0)
  if (!current || !discount || discount <= 0 || discount >= 100) return 0
  return current / (1 - discount / 100)
}

export function discountAmountFromItem(item) {
  const current = Number(item?.unitPrice || 0)
  const listPrice = Number(item?.listPrice || 0)
  const original = listPrice > current ? listPrice : originalPriceFromDiscount(current, item?.discountPercent)
  const quantity = Number(item?.quantity || 0)
  if (!original || original <= current || quantity <= 0) return 0
  return (original - current) * quantity
}

export function discountLabel(value, currency = 'USD') {
  const amount = Math.abs(Number(value || 0))
  return `-${money(amount, currency)}`
}

export function fulfillmentOf(itemId = '') {
  return itemId.endsWith('::PICKUP') ? 'PICKUP' : 'SHIPPING'
}
