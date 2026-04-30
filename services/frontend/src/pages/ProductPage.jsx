import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { money, originalPriceFromDiscount } from '../lib/format'
import { isSessionActive, signinUrl } from '../lib/session'
import { loadWishlist, saveWishlist } from '../lib/wishlist'

const pickupText = 'West Loop Pickup Hub · 401 W Lake St, Chicago, IL'

export function ProductPage() {
  const { sku: routeSku = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { session } = useSession()
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const [fulfillment, setFulfillment] = useState('shipping')
  const [wishlist, setWishlist] = useState(() => loadWishlist())
  const [addedToCart, setAddedToCart] = useState(false)
  const sku = useMemo(() => routeSku || new URLSearchParams(location.search).get('sku') || '', [location.search, routeSku])
  const requestedReturnTo = new URLSearchParams(location.search).get('returnTo')
  const signinReturnTo = requestedReturnTo && requestedReturnTo.startsWith('/')
    ? requestedReturnTo
    : `${location.pathname}${location.search}`

  const productQuery = useQuery({
    queryKey: ['product', sku],
    queryFn: () => api.getItemBySku(sku),
    staleTime: 120_000,
  })
  const cartQuery = useQuery({
    queryKey: ['cart', session.customerId],
    queryFn: () => api.getCart(session.token, session.customerId),
    enabled: Boolean(session.token && session.customerId),
  })

  const item = productQuery.data
  const cartUnits = (cartQuery.data?.items || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
  const originalPrice = originalPriceFromDiscount(item?.unitPrice, item?.discountPercent)

  async function addToCart() {
    if (!isSessionActive(session)) {
      navigate(signinUrl(signinReturnTo))
      return
    }
    if (!item) return
    const itemId = `${item.sku}::${fulfillment.toUpperCase()}`
    const cartKey = ['cart', session.customerId]
    const previousCart = queryClient.getQueryData(cartKey)
    queryClient.setQueryData(cartKey, (current) => {
      const base = current || { items: [] }
      const items = [...(base.items || [])]
      const existing = items.find((line) => line.itemId === itemId)
      if (existing) {
        existing.quantity = Number(existing.quantity || 0) + quantity
        existing.lineTotal = Number(existing.unitPrice || item.unitPrice || 0) * existing.quantity
      } else {
        items.push({
          itemId,
          itemName: item.itemName,
          sku: item.sku,
          quantity,
          unitPrice: item.unitPrice,
          lineTotal: Number(item.unitPrice || 0) * quantity,
        })
      }
      return { ...base, items }
    })
    setAddedToCart(true)
    try {
      await api.addCartItem(session.token, session.customerId, { itemId, sku: item.sku, quantity })
      await queryClient.invalidateQueries({ queryKey: cartKey })
    } catch (error) {
      queryClient.setQueryData(cartKey, previousCart)
      setAddedToCart(false)
      if (String(error.message || '').startsWith('401')) {
        navigate(signinUrl(signinReturnTo))
      }
    }
  }

  function toggleWishlist() {
    if (!isSessionActive(session)) {
      navigate(signinUrl(signinReturnTo))
      return
    }
    if (!item) return
    const next = wishlist.includes(item.sku) ? wishlist.filter((value) => value !== item.sku) : [...wishlist, item.sku]
    saveWishlist(next)
    setWishlist(next)
  }

  return (
    <section className="product-layout">
      <article className="section-panel panel" id="product-detail-panel">
        {!item ? <div className="message-box" id="product-state">Loading product details...</div> : null}
        <div id="product-detail-content" hidden={!item}>
          <div className="product-detail-grid">
            <div className="product-gallery">
              <div className="tile-image product-image-frame">
                {item?.pictureUrls?.[0] ? <img id="product-image" src={item.pictureUrls[0]} alt={item.itemName} /> : null}
                {!item?.pictureUrls?.[0] ? <div className="tile-fallback" id="product-image-fallback">No image</div> : null}
              </div>
            </div>
            <div className="stack product-copy">
              <div>
                <p className="eyebrow" id="product-brand">{item?.brand || 'Brand'}</p>
                <h1 className="product-title product-page-title" id="product-name">{item?.itemName}</h1>
                <p className="helper" id="product-meta">{item?.category}</p>
              </div>
              <div className="stack-tight">
                {(item?.discountPercent || 0) > 0 ? (
                  <div className="price-now-row">
                    <span className="price-now-label">Now</span>
                    <strong className="tile-price product-page-price sale-price" id="product-price">{money(item?.unitPrice, item?.currencyCode || 'USD')}</strong>
                    <span className="list-price">{money(originalPrice, item?.currencyCode || 'USD')}</span>
                  </div>
                ) : <strong className="tile-price product-page-price" id="product-price">{money(item?.unitPrice, item?.currencyCode || 'USD')}</strong>}
                <div className="inline-actions product-price-meta">
                  {(item?.discountPercent || 0) > 0 ? <span className="member-badge" id="product-discount-badge">Deal</span> : null}
                  <span className="tag" id="product-inventory-tag">{item?.inventory?.inStock ? 'In stock' : 'Out of stock'}</span>
                </div>
              </div>
              <p id="product-description">{item?.description}</p>
            </div>
          </div>
        </div>
      </article>
      <aside className="product-buy-box panel sticky-column">
        <div className="stack">
          <p className="eyebrow">Get it now</p>
          <div className="buy-box-card">
            <strong className="buy-box-price" id="product-buybox-price">{money(item?.unitPrice, item?.currencyCode || 'USD')}</strong>
            <div className="inline-actions product-price-meta">
              <span className="tag" id="product-buybox-inventory">{item?.inventory?.inStock ? 'In stock' : 'Out of stock'}</span>
            </div>
            <div className="buy-box-purchase-row">
              <div className="buy-box-purchase-inline">
                <div className="qty-group buy-box-qty">
                  <button className="qty-button" type="button" id="product-qty-minus" onClick={() => setQuantity((value) => Math.max(1, value - 1))}>-</button>
                  <strong id="product-qty-readout">{quantity}</strong>
                  <button className="qty-button" type="button" id="product-qty-plus" onClick={() => setQuantity((value) => value + 1)}>+</button>
                </div>
                <button className="primary-button" type="button" id="product-add-button" onClick={addToCart}>{addedToCart ? 'Added to cart' : 'Add to cart'}</button>
              </div>
              <button
                aria-label={wishlist.includes(item?.sku) ? 'Remove from wish list' : 'Save to wish list'}
                className="wishlist-heart product-heart"
                type="button"
                id="product-wishlist-button"
                onClick={toggleWishlist}
              >
                {wishlist.includes(item?.sku) ? '♥' : '♡'}
              </button>
            </div>
            <div className="buy-box-pickup-prompt">
              <h2>How do you want it?</h2>
            </div>
            <div className="delivery-choice-group">
              <button className={`delivery-choice${fulfillment === 'shipping' ? ' active' : ''}`} data-fulfillment="shipping" type="button" onClick={() => setFulfillment('shipping')}>Shipping</button>
              <button className={`delivery-choice${fulfillment === 'pickup' ? ' active' : ''}`} data-fulfillment="pickup" type="button" onClick={() => setFulfillment('pickup')}>Pickup</button>
            </div>
            <div className="buy-box-fulfillment" id="product-fulfillment-note">
              <strong id="product-fulfillment-title">{fulfillment === 'pickup' ? 'Pickup' : 'Shipping'}</strong>
              <span id="product-fulfillment-detail">{fulfillment === 'pickup' ? pickupText : 'Ships to your saved address at checkout.'}</span>
            </div>
            <Link className="buy-box-cart-row" to="/cart.html" id="product-view-cart">
              <span className="buy-box-cart-copy">
                <strong>{cartUnits ? `In your cart ${cartUnits} item${cartUnits === 1 ? '' : 's'}` : 'Review cart'}</strong>
              </span>
              <span className="secondary-button">View cart</span>
            </Link>
            <div className="buy-box-returns">
              <strong>Returns</strong>
              <span>Free 90-day returns on most items</span>
            </div>
          </div>
        </div>
      </aside>
    </section>
  )
}
