import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { dateLabel, fulfillmentOf, fullAddress, money, originalPriceFromDiscount } from '../lib/format'
import { canCancel } from '../lib/orderRules'
import { useProductLookupBySku } from '../lib/useProductLookup'
import { signinUrl } from '../lib/session'

const PICKUP_LOCATION = 'ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL'

export function ConfirmationPage() {
  const { session } = useSession()
  if (!session.token) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }
  const queryClient = useQueryClient()
  const { orderNumber: routeOrderNumber = '' } = useParams()
  const location = useLocation()
  const orderNumber = routeOrderNumber || new URLSearchParams(location.search).get('orderNumber') || ''
  const orderQuery = useQuery({
    queryKey: ['confirmation-order', orderNumber],
    queryFn: () => api.getOrder(session.token, orderNumber),
    enabled: Boolean(session.token && orderNumber),
  })
  const order = orderQuery.data
  const items = order?.items || []
  const shippingItems = items.filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING')
  const pickupItems = items.filter((item) => fulfillmentOf(item.itemId) === 'PICKUP')
  const lookupSkus = items.map((item) => item.sku || item.itemId?.split('::')?.[0] || '')
  const productLookup = useProductLookupBySku(lookupSkus)
  const originalSubtotal = items.reduce((sum, item) => {
    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
    const preview = productLookup[lookupSku] || item
    const current = Number(preview.unitPrice || 0)
    const percent = Number(preview.discountPercent || 0)
    const listed = Number(preview.listPrice || 0)
    const original = listed > current ? listed : (current && percent > 0 ? originalPriceFromDiscount(current, percent) : current)
    const quantity = Number(item.quantity || 0)
    return sum + Number(original || 0) * quantity
  }, 0)
  const discountedSubtotal = Number(order?.subtotalAmount || items.reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0))
  const orderDiscount = Math.max(0, originalSubtotal - discountedSubtotal)
  const amountPaid = Number(order?.totalAmount || 0)
  const cancelMutation = useMutation({
    mutationFn: () => (order?.status === 'PAID' && order?.paymentReference
      ? api.cancelPayment(session.token, order.paymentReference, {
          idempotencyKey: `cancel-${Date.now()}`,
          amount: order.totalAmount,
          externalReference: order.orderNumber,
        })
      : api.cancelOrder(session.token, orderNumber, {
          cancelRequestId: `cancel-${Date.now()}`,
          statusReason: 'Cancelled by customer',
        })),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['confirmation-order', orderNumber] })
      await queryClient.invalidateQueries({ queryKey: ['orders', session.customerId] })
    },
  })

  const isCancelled = order?.status === 'CANCELLED'
  const statusClass = isCancelled ? 'order-status-cancelled' : 'order-status-paid'
  const statusLabel = isCancelled ? 'Order cancelled' : order?.status || 'PAID'

  function renderItems(itemList) {
    return itemList.map((item) => {
      const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
      const preview = productLookup[lookupSku] || item
      const imageUrl = preview.pictureUrls?.[0] || item.pictureUrls?.[0] || ''
      return (
        <div key={item.itemId} className="order-item-enhanced">
          <div className="order-item-thumb">
            {imageUrl ? <img src={imageUrl} alt={preview.itemName || item.itemName} /> : <span style={{ fontSize: '1.4rem' }}>📦</span>}
          </div>
          <div className="order-item-details">
            <strong>{item.itemName}</strong>
            <span>Qty: {item.quantity}</span>
          </div>
          <span className="order-item-price">{money(item.lineTotal || item.unitPrice * item.quantity, order?.currencyCode || 'USD')}</span>
        </div>
      )
    })
  }

  return (
    <section className="confirmation-panel panel">
      {/* Status banner */}
      <div className="order-status-banner">
        <div className="order-status-banner-copy">
          <p className="eyebrow">{isCancelled ? 'Order details' : 'Order confirmation'}</p>
          <h2>{isCancelled ? 'Order cancelled' : 'Your order is confirmed'}</h2>
          <p>{isCancelled ? 'This order has been cancelled.' : 'Thanks for your purchase! We\'re getting your items ready.'}</p>
        </div>
        <span className={`order-status-badge ${statusClass}`} style={{ marginLeft: 'auto', alignSelf: 'flex-start' }}>{statusLabel}</span>
      </div>

      {/* Key info grid */}
      <div className="order-info-grid">
        <div className="order-info-cell"><span className="eyebrow">Order number</span><strong id="confirmation-order-number">{order?.orderNumber || '—'}</strong></div>
        <div className="order-info-cell"><span className="eyebrow">Date placed</span><strong>{dateLabel(order?.createdAt)}</strong></div>
        <div className="order-info-cell"><span className="eyebrow">Status</span><strong id="confirmation-order-status">{order?.status || '—'}</strong></div>
        <div className="order-info-cell"><span className="eyebrow">Amount paid</span><strong id="confirmation-total">{money(amountPaid, order?.currencyCode || 'USD')}</strong></div>
      </div>

      {/* Order summary */}
      <div className="order-receipt">
        <p className="order-receipt-title">Order summary</p>
        <div className="order-receipt-rows">
          <div className="order-receipt-row"><span className="receipt-label">Original price</span><span className="receipt-value">{money(originalSubtotal, order?.currencyCode || 'USD')}</span></div>
          {orderDiscount > 0 && <div className="order-receipt-row receipt-discount"><span className="receipt-label">Discount</span><span className="receipt-value">-{money(orderDiscount, order?.currencyCode || 'USD')}</span></div>}
          <div className="order-receipt-row"><span className="receipt-label">Shipping</span><span className="receipt-value">{order?.shippingAmount ? money(order.shippingAmount, order?.currencyCode || 'USD') : 'Free'}</span></div>
          <div className="order-receipt-row receipt-total"><span className="receipt-label">Amount you paid</span><span className="receipt-value">{money(amountPaid, order?.currencyCode || 'USD')}</span></div>
        </div>
      </div>

      {/* Shipping items */}
      {shippingItems.length ? (
        <div className="order-fulfillment-block">
          <div className="order-fulfillment-header">
            <span className="order-fulfillment-badge">🚚 Shipping — {shippingItems.length} item{shippingItems.length !== 1 ? 's' : ''}</span>
          </div>
          {order?.shippingAddress && (
            <div className="order-fulfillment-address">📍 {fullAddress(order.shippingAddress)}</div>
          )}
          {renderItems(shippingItems)}
        </div>
      ) : null}

      {/* Pickup items */}
      {pickupItems.length ? (
        <div className="order-fulfillment-block">
          <div className="order-fulfillment-header">
            <span className="order-fulfillment-badge">🏪 In-store pickup — {pickupItems.length} item{pickupItems.length !== 1 ? 's' : ''}</span>
          </div>
          <div className="order-fulfillment-address">📍 {PICKUP_LOCATION}</div>
          {renderItems(pickupItems)}
        </div>
      ) : null}

      <div className="inline-actions" style={{ marginTop: 20 }}>
        <button
          className="secondary-button"
          type="button"
          onClick={() => cancelMutation.mutate()}
          disabled={!canCancel(order) || cancelMutation.isPending}
        >
          {isCancelled ? 'Order cancelled' : 'Cancel order'}
        </button>
        <Link className="primary-button" to="/index.html">Continue shopping</Link>
        <Link className="secondary-button" to="/account.html">View account</Link>
      </div>
    </section>
  )
}
