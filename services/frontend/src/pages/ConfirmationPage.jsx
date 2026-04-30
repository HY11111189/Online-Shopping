import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { dateLabel, fulfillmentOf, fullAddress, money } from '../lib/format'
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
  const discountTotal = items.reduce((sum, item) => {
    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
    const preview = productLookup[lookupSku] || item
    const current = Number(preview.unitPrice || 0)
    const percent = Number(preview.discountPercent || 0)
    const original = current && percent > 0 ? current / (1 - percent / 100) : 0
    const quantity = Number(item.quantity || 0)
    return sum + (original > current ? (original - current) * quantity : 0)
  }, 0)
  const orderDiscount = Number(order?.discountAmount || discountTotal || 0)
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
  const cancelLabel = isCancelled ? 'Cancelled' : 'Cancel order'

  return (
    <section className="confirmation-panel panel">
      <div className="section-head">
        <div>
          <p className="eyebrow">{isCancelled ? 'Order details' : 'Order confirmation'}</p>
          <h2>{isCancelled ? 'Order cancelled' : 'Your order is confirmed'}</h2>
        </div>
      </div>
      <div className="status-grid">
        <article className="status-card"><span className="eyebrow">Order number</span><strong id="confirmation-order-number">{order?.orderNumber || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Status</span><strong id="confirmation-order-status">{order?.status || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Placed</span><strong>{dateLabel(order?.createdAt)}</strong></article>
        <article className="status-card"><span className="eyebrow">Total</span><strong id="confirmation-total">{money(order?.totalAmount)}</strong></article>
      </div>
      <div className="summary-list" style={{ marginTop: 18 }}>
        <div className="summary-line"><span>Subtotal</span><strong>{money(order?.subtotalAmount, order?.currencyCode || 'USD')}</strong></div>
        <div className="summary-line"><span>Shipping</span><strong>{order?.shippingAmount ? money(order.shippingAmount, order?.currencyCode || 'USD') : 'Free'}</strong></div>
        <div className="summary-line"><span>Discount</span><strong>{orderDiscount > 0 ? `-${money(orderDiscount, order?.currencyCode || 'USD')}` : '$0.00'}</strong></div>
        <div className="summary-line total"><span>Total</span><strong>{money(order?.totalAmount, order?.currencyCode || 'USD')}</strong></div>
      </div>

      {shippingItems.length ? (
        <div className="confirmation-fulfillment-block" style={{ marginTop: 18 }}>
          <div className="section-head" style={{ marginBottom: 10 }}>
            <div>
              <p className="eyebrow">Shipping</p>
              <h3 style={{ margin: '4px 0 0' }}>Delivery items</h3>
            </div>
            <span className="order-status-badge order-status-paid">
              {shippingItems.length} item{shippingItems.length !== 1 ? 's' : ''}
            </span>
          </div>
          <div className="message-box" style={{ marginBottom: 10 }}>
            Shipping to: <strong>{fullAddress(order?.shippingAddress)}</strong>
          </div>
          <div className="confirmation-item-list">
            {shippingItems.map((item) => (
              <div key={item.itemId} className="confirmation-item-row">
                <div className="review-item-media">
                  {(() => {
                    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
                    const preview = productLookup[lookupSku] || item
                    const imageUrl = preview.pictureUrls?.[0] || item.pictureUrls?.[0] || ''
                    return imageUrl ? <img src={imageUrl} alt={preview.itemName || item.itemName} /> : <div className="review-item-fallback">No image</div>
                  })()}
                </div>
                <span>{item.itemName}</span>
                <strong>{money(item.lineTotal || item.unitPrice * item.quantity, order?.currencyCode || 'USD')}</strong>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {pickupItems.length ? (
        <div className="confirmation-fulfillment-block" style={{ marginTop: 18 }}>
          <div className="section-head" style={{ marginBottom: 10 }}>
            <div>
              <p className="eyebrow">In-store pickup</p>
              <h3 style={{ margin: '4px 0 0' }}>Pickup items</h3>
            </div>
            <span className="order-status-badge order-status-paid">
              {pickupItems.length} item{pickupItems.length !== 1 ? 's' : ''}
            </span>
          </div>
          <div className="message-box" style={{ marginBottom: 10 }}>
            Ready at: <strong>{PICKUP_LOCATION}</strong>
          </div>
          <div className="confirmation-item-list">
            {pickupItems.map((item) => (
              <div key={item.itemId} className="confirmation-item-row">
                <div className="review-item-media">
                  {(() => {
                    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
                    const preview = productLookup[lookupSku] || item
                    const imageUrl = preview.pictureUrls?.[0] || item.pictureUrls?.[0] || ''
                    return imageUrl ? <img src={imageUrl} alt={preview.itemName || item.itemName} /> : <div className="review-item-fallback">No image</div>
                  })()}
                </div>
                <span>{item.itemName}</span>
                <strong>{money(item.lineTotal || item.unitPrice * item.quantity, order?.currencyCode || 'USD')}</strong>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="inline-actions" style={{ marginTop: 18 }}>
        <button
          className="secondary-button"
          type="button"
          onClick={() => cancelMutation.mutate()}
          disabled={!canCancel(order) || cancelMutation.isPending}
        >
          {isCancelled ? 'Order cancelled' : cancelLabel}
        </button>
        <Link className="primary-button" to="/index.html">Continue shopping</Link>
        <Link className="secondary-button" to="/account.html">View account</Link>
      </div>
    </section>
  )
}
