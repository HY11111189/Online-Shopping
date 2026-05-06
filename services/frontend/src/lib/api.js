const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function apiRequest(path, { token, method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (body) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    signal,
  })
  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const parsed = JSON.parse(text)
      message = parsed.message || parsed.error || text
    } catch {}
    throw new Error(`${response.status} ${message || `${method} ${path} failed`}`)
  }
  if (response.status === 204) return null
  return response.json()
}

export const api = {
  signin: (payload) => apiRequest('/api/v1/auth/signin', { method: 'POST', body: payload }),
  signup: (payload) => apiRequest('/api/v1/auth/signup', { method: 'POST', body: payload }),
  getMe: (token) => apiRequest('/api/v1/shopping/accounts/me', { token }),
  updateAccount: (token, accountId, payload) => apiRequest(`/api/v1/shopping/accounts/${encodeURIComponent(accountId)}`, { token, method: 'PUT', body: payload }),
  getItems: ({ page, size = 24 } = {}) => {
    const params = new URLSearchParams()
    if (page != null) { params.set('page', String(page)); params.set('size', String(size)) }
    const qs = params.toString()
    return apiRequest(`/api/v1/shopping/items${qs ? `?${qs}` : ''}`)
  },
  getCategories: () => apiRequest('/api/v1/shopping/items/categories'),
  searchItems: ({ q, limit = 24, page, size = 24 }) => {
    const params = new URLSearchParams()
    if (q) params.set('q', q)
    if (page != null) { params.set('page', String(page)); params.set('size', String(size)) }
    else if (limit) params.set('limit', String(limit))
    return apiRequest(`/api/v1/shopping/items/search?${params.toString()}`)
  },
  getItemsByCategory: (category, { limit = 48, page, size = 24 } = {}) => {
    const params = new URLSearchParams()
    if (page != null) { params.set('page', String(page)); params.set('size', String(size)) }
    else params.set('limit', String(limit))
    return apiRequest(`/api/v1/shopping/items/category/${encodeURIComponent(category)}?${params.toString()}`)
  },
  getItemBySku: (sku) => apiRequest(`/api/v1/shopping/items/sku/${encodeURIComponent(sku)}`),
  getCart: (token, customerId) => apiRequest(`/api/v1/shopping/carts/${customerId}`, { token }),
  addCartItem: (token, customerId, payload) => apiRequest(`/api/v1/shopping/carts/${customerId}/items`, { token, method: 'POST', body: payload }),
  updateCartItem: (token, customerId, itemId, payload) => apiRequest(`/api/v1/shopping/carts/${customerId}/items/${encodeURIComponent(itemId)}`, { token, method: 'PUT', body: payload }),
  removeCartItem: (token, customerId, itemId) => apiRequest(`/api/v1/shopping/carts/${customerId}/items/${encodeURIComponent(itemId)}`, { token, method: 'DELETE' }),
  createOrder: (token, payload) => apiRequest('/api/v1/shopping/orders', { token, method: 'POST', body: payload }),
  placeOrder: (token, orderNumber) => apiRequest(`/api/v1/shopping/orders/${encodeURIComponent(orderNumber)}/place`, { token, method: 'POST' }),
  getOrder: (token, orderNumber) => apiRequest(`/api/v1/shopping/orders/${encodeURIComponent(orderNumber)}`, { token }),
  getOrdersByCustomer: (token, customerId) => apiRequest(`/api/v1/shopping/orders/customers/${customerId}`, { token }),
  cancelOrder: (token, orderNumber, payload) => apiRequest(`/api/v1/shopping/orders/${encodeURIComponent(orderNumber)}/cancel`, { token, method: 'POST', body: payload }),
  getPayment: (token, paymentNumber) => apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}`, { token }),
  cancelPayment: (token, paymentNumber, payload) => apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}/cancel`, { token, method: 'POST', body: payload }),
  refundPayment: (token, paymentNumber, payload) => apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}/refund`, { token, method: 'POST', body: payload }),
  chatAssistant: (token, payload) => apiRequest('/api/v1/shopping/assistant/chat', { token, method: 'POST', body: payload }),
  chatAgentAssistant: (token, payload) => apiRequest('/api/v1/shopping/assistant/agent/chat', { token, method: 'POST', body: payload }),
}
