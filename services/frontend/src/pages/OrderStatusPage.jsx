import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { dateLabel, fulfillmentOf, fullAddress, money } from '../lib/format'
import { orderAction, orderActionLabel } from '../lib/orderRules'
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
    mutationFn: () => {
      const action = orderAction(order)
      if (!action) {
        throw new Error('This order is already finalized.')
      }
      if (order?.status === 'PAID' && order?.paymentReference) {
        if (action === 'cancel') {
          return api.cancelPayment(session.token, order.paymentReference, {
            idempotencyKey: `cancel-${Date.now()}`,
            amount: order.totalAmount,
            externalReference: order.orderNumber,
          })
        }
        return api.refundPayment(session.token, order.paymentReference, {
          idempotencyKey: `refund-${Date.now()}`,
          amount: order.totalAmount,
          externalReference: order.orderNumber,
        })
      }
      return api.cancelOrder(session.token, orderNumber, {
        cancelRequestId: `cancel-${Date.now()}`,
        statusReason: 'Cancelled by customer',
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['order', orderNumber] })
      await queryClient.invalidateQueries({ queryKey: ['orders', session.customerId] })
    },
    onError: (error) => {
      console.error(error)
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

  const statusLabel = order?.status === 'CANCELLED' ? 'Order cancelled' : order?.status || 'Processing'
  const statusClass = { PAID: 'order-status-paid', CANCELLED: 'order-status-cancelled', REFUNDED: 'order-status-refunded', FAILED: 'order-status-failed' }[order?.status] || 'order-status-pending'
  const orderActionValue = orderAction(order)
  const orderButtonLabel = orderActionLabel(order)

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
          <p className="eyebrow">Order details</p>
          <h2>{statusLabel}</h2>
          <p>{order?.statusReason || 'We are processing your order.'}</p>
        </div>
        <span className={`order-status-badge ${statusClass}`} style={{ marginLeft: 'auto', alignSelf: 'flex-start' }}>{order?.status || 'Processing'}</span>
      </div>

      {/* Key info grid */}
      <div className="order-info-grid">
        <div className="order-info-cell"><span className="eyebrow">Order number</span><strong>{order?.orderNumber || orderNumber}</strong></div>
        <div className="order-info-cell"><span className="eyebrow">Date placed</span><strong>{dateLabel(order?.createdAt)}</strong></div>
        <div className="order-info-cell"><span className="eyebrow">Payment ref</span><strong>{order?.paymentReference || '—'}</strong></div>
        <div className="order-info-cell"><span className="eyebrow">Items</span><strong>{items.length} item{items.length !== 1 ? 's' : ''} · {shippingItems.length} ship · {pickupItems.length} pickup</strong></div>
      </div>

      {/* Order summary */}
      <div className="order-receipt">
        <p className="order-receipt-title">Order summary</p>
        <div className="order-receipt-rows">
          <div className="order-receipt-row"><span className="receipt-label">Subtotal</span><span className="receipt-value">{money(order?.subtotalAmount)}</span></div>
          <div className="order-receipt-row"><span className="receipt-label">Shipping</span><span className="receipt-value">{order?.shippingAmount ? money(order.shippingAmount, order?.currencyCode || 'USD') : 'Free'}</span></div>
          {orderDiscount > 0 && <div className="order-receipt-row receipt-discount"><span className="receipt-label">Discount</span><span className="receipt-value">-{money(orderDiscount, order?.currencyCode || 'USD')}</span></div>}
          <div className="order-receipt-row receipt-total"><span className="receipt-label">Total</span><span className="receipt-value">{money(order?.totalAmount)}</span></div>
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
          <div className="order-fulfillment-address">📍 ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL</div>
          {renderItems(pickupItems)}
        </div>
      ) : null}

      <div className="inline-actions" style={{ marginTop: 20 }}>
        {orderButtonLabel ? (
          <button
            className="secondary-button"
            type="button"
            onClick={() => cancelOrderMutation.mutate()}
            disabled={!orderActionValue || cancelOrderMutation.isPending}
          >
            {orderButtonLabel}
          </button>
        ) : null}
        <Link className="primary-button" to="/index.html">Continue shopping</Link>
        <Link className="secondary-button" to="/account.html">View account</Link>
      </div>
    </section>
  )
}
