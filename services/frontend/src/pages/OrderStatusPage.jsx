import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { dateLabel, fulfillmentOf, fullAddress, money } from '../lib/format'
import { canCancel } from '../lib/orderRules'
import { useProductLookupBySku } from '../lib/useProductLookup'
import { signinUrl } from '../lib/session'

export function OrderStatusPage() {
  const { session } = useSession()
  if (!session.token) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const location = useLocation()
  const { orderNumber: routeOrderNumber = '' } = useParams()
  const orderNumber = routeOrderNumber || new URLSearchParams(location.search).get('orderNumber') || ''
  const orderQuery = useQuery({
    queryKey: ['order', orderNumber],
    queryFn: () => api.getOrder(session.token, orderNumber),
    enabled: Boolean(session.token && orderNumber),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return ['PAID', 'FAILED', 'CANCELLED', 'REFUNDED'].includes(status) ? false : 2000
    },
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
    const discount = Number(preview.discountPercent || 0)
    const original = current && discount > 0 ? current / (1 - discount / 100) : 0
    const quantity = Number(item.quantity || 0)
    return sum + (original > current ? (original - current) * quantity : 0)
  }, 0)
  const orderDiscount = Number(order?.discountAmount || discountTotal || 0)
  const cancelOrderMutation = useMutation({
    mutationFn: () => order?.status === 'PAID' && order?.paymentReference
      ? api.cancelPayment(session.token, order.paymentReference, {
          idempotencyKey: `cancel-${Date.now()}`,
          amount: order.totalAmount,
          externalReference: order.orderNumber,
        })
      : api.cancelOrder(session.token, orderNumber, {
          cancelRequestId: `cancel-${Date.now()}`,
          statusReason: 'Cancelled by customer',
        }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['order', orderNumber] })
      await queryClient.invalidateQueries({ queryKey: ['orders', session.customerId] })
    },
  })
  useEffect(() => {
    if (!order?.orderNumber) return
    if (order.status === 'PAID') {
      navigate(`/confirmation.html?orderNumber=${encodeURIComponent(order.orderNumber)}`, { replace: true })
      return
    }
    if (['FAILED'].includes(order.status)) {
      navigate(`/order-failed.html?orderNumber=${encodeURIComponent(order.orderNumber)}`, { replace: true })
    }
  }, [navigate, order])

  return (
    <section className="confirmation-panel panel">
      <div className="section-head">
        <div>
          <p className="eyebrow">Order details</p>
          <h2>{order?.status === 'CANCELLED' ? 'Order cancelled' : order?.status || 'Processing order'}</h2>
        </div>
      </div>
      <div className="status-grid">
        <article className="status-card"><span className="eyebrow">Order number</span><strong>{order?.orderNumber || orderNumber}</strong></article>
        <article className="status-card"><span className="eyebrow">Status</span><strong>{order?.status || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Payment</span><strong>{order?.paymentReference || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Total</span><strong>{money(order?.totalAmount)}</strong></article>
      </div>
      <div className="message-box" style={{ marginTop: 18 }}>{order?.statusReason || 'We are processing your order.'}</div>
      <div className="status-grid" style={{ marginTop: 18 }}>
        <article className="status-card"><span className="eyebrow">Placed</span><strong>{dateLabel(order?.createdAt)}</strong></article>
        <article className="status-card"><span className="eyebrow">Shipping</span><strong>{shippingItems.length} item(s)</strong></article>
        <article className="status-card"><span className="eyebrow">Pickup</span><strong>{pickupItems.length} item(s)</strong></article>
      </div>
      <div className="summary-list" style={{ marginTop: 18 }}>
        <div className="summary-line"><span>Subtotal</span><strong>{money(order?.subtotalAmount)}</strong></div>
        <div className="summary-line"><span>Shipping</span><strong>{order?.shippingAmount ? money(order.shippingAmount, order?.currencyCode || 'USD') : 'Free'}</strong></div>
        <div className="summary-line"><span>Discount</span><strong>{orderDiscount > 0 ? `-${money(orderDiscount, order?.currencyCode || 'USD')}` : '$0.00'}</strong></div>
        <div className="summary-line total"><span>Total</span><strong>{money(order?.totalAmount)}</strong></div>
      </div>
      <div className="order-history-addresses" style={{ marginTop: 18 }}>
        {shippingItems.length ? (
          <article className="order-history-item">
            <span className="eyebrow">Delivery address</span>
            <strong>{fullAddress(order?.shippingAddress)}</strong>
          </article>
        ) : null}
        {pickupItems.length ? (
          <article className="order-history-item">
            <span className="eyebrow">Pickup location</span>
            <strong>ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL</strong>
          </article>
        ) : null}
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
            Ready at: <strong>ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL</strong>
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
          onClick={() => cancelOrderMutation.mutate()}
          disabled={!canCancel(order) || cancelOrderMutation.isPending}
        >
          {order?.status === 'CANCELLED' ? 'Order cancelled' : 'Cancel order'}
        </button>
        <Link className="primary-button" to="/index.html">Continue shopping</Link>
        <Link className="secondary-button" to="/account.html">View account</Link>
      </div>
    </section>
  )
}
