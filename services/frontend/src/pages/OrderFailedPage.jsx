import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { fulfillmentOf, money } from '../lib/format'
import { signinUrl } from '../lib/session'

export function OrderFailedPage() {
  const { session } = useSession()
  if (!session.token) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }
  const location = useLocation()
  const { orderNumber: routeOrderNumber = '' } = useParams()
  const orderNumber = routeOrderNumber || new URLSearchParams(location.search).get('orderNumber') || ''
  const orderQuery = useQuery({
    queryKey: ['failed-order', orderNumber],
    queryFn: () => api.getOrder(session.token, orderNumber),
    enabled: Boolean(session.token && orderNumber),
  })
  const order = orderQuery.data
  const items = order?.items || []

  return (
    <section className="confirmation-panel panel">
      <div className="section-head">
        <div>
          <p className="eyebrow">Order unavailable</p>
          <h2>We could not complete this order</h2>
        </div>
      </div>
      <div className="status-grid">
        <article className="status-card"><span className="eyebrow">Order number</span><strong>{order?.orderNumber || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Order</span><strong>{order?.status || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Payment</span><strong>{order?.paymentReference || '-'}</strong></article>
        <article className="status-card"><span className="eyebrow">Total</span><strong>{money(order?.totalAmount)}</strong></article>
      </div>
      <div className="message-box" style={{ marginTop: 18 }}>
        {order?.statusReason || 'Order failure details appear here.'}
      </div>
      <div className="timeline" style={{ marginTop: 18 }}>
        <div className="summary-line"><span>Order created</span><strong>{order?.createdAt || '-'}</strong></div>
        <div className="summary-line"><span>Current status</span><strong>{order?.status || '-'}</strong></div>
      </div>
      <div className="order-detail-grid" style={{ marginTop: 18 }}>
        {items.filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING').length ? (
          <article className="status-card">
            <span className="eyebrow">Shipping</span>
            <strong>{items.filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING').length} item(s)</strong>
          </article>
        ) : null}
        {items.filter((item) => fulfillmentOf(item.itemId) === 'PICKUP').length ? (
          <article className="status-card">
            <span className="eyebrow">Pickup</span>
            <strong>{items.filter((item) => fulfillmentOf(item.itemId) === 'PICKUP').length} item(s)</strong>
          </article>
        ) : null}
      </div>
      <div className="stack" style={{ marginTop: 18 }}>
        {items.map((item) => (
          <article key={item.itemId} className="order-history-item">
            <div className="order-summary-line">
              <strong>{item.itemName || item.sku}</strong>
              <span className="muted">{item.quantity} × {money(item.unitPrice || 0, order?.currencyCode || 'USD')}</span>
            </div>
            <div className="fulfillment-inline-meta">
              <span className={`fulfillment-badge ${fulfillmentOf(item.itemId).toLowerCase()}`}>{fulfillmentOf(item.itemId) === 'PICKUP' ? 'Pickup' : 'Shipping'}</span>
            </div>
          </article>
        ))}
      </div>
      <div className="inline-actions" style={{ marginTop: 18 }}>
        <Link className="primary-button" to="/cart.html">Return to cart</Link>
        <Link className="secondary-button" to="/index.html">Keep shopping</Link>
      </div>
    </section>
  )
}
