import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { money } from '../lib/format'
import { isSessionActive, signinUrl } from '../lib/session'
import { loadWishlist, saveWishlist } from '../lib/wishlist'

export function WishlistPage() {
  const { session } = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [wishlist, setWishlist] = useState(() => loadWishlist())
  const savedSkus = useMemo(() => [...new Set(wishlist)], [wishlist])
  const itemsQuery = useQuery({
    queryKey: ['catalog-wishlist', savedSkus],
    queryFn: async () => Promise.all(savedSkus.map((sku) => api.getItemBySku(sku))),
    enabled: Boolean(session.token && savedSkus.length),
    staleTime: 60_000,
  })
  const items = itemsQuery.data || []

  if (!isSessionActive(session)) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }

  async function addToCart(item) {
    await api.addCartItem(session.token, session.customerId, { itemId: `${item.sku}::SHIPPING`, sku: item.sku, quantity: 1 })
    await queryClient.invalidateQueries({ queryKey: ['cart', session.customerId] })
  }

  function remove(item) {
    if (!isSessionActive(session)) {
      navigate(signinUrl(`${window.location.pathname}${window.location.search}`))
      return
    }
    const next = wishlist.filter((sku) => sku !== item.sku)
    saveWishlist(next)
    setWishlist(next)
  }

  return (
    <section className="section-panel panel">
      <div className="section-head">
        <div>
          <p className="eyebrow">Lists</p>
          <h2>Saved items</h2>
        </div>
        <div className="muted" id="wishlist-meta">{items.length ? `${items.length} items` : 'Loading'}</div>
      </div>
      {!items.length ? <div className="message-box" id="wishlist-message">Loading your saved items.</div> : null}
      <div className="tile-grid" id="wishlist-grid" hidden={!items.length}>
        {items.map((item) => (
          <Link key={item.sku} className="catalog-tile" to={`/product.html?sku=${encodeURIComponent(item.sku)}`}>
            <div className="tile-image">
              {item.pictureUrls?.[0] ? <img src={item.pictureUrls[0]} alt={item.itemName} /> : <div className="tile-fallback">No image</div>}
            </div>
            <div className="tile-copy stack-tight">
              <p className="eyebrow">{item.brand}</p>
              <h3>{item.itemName}</h3>
              <strong className="tile-price">{money(item.unitPrice)}</strong>
              <div className="inline-actions">
                <button className="primary-button" onClick={(event) => { event.preventDefault(); addToCart(item) }}>Add to cart</button>
                <button className="secondary-button" onClick={(event) => { event.preventDefault(); remove(item) }}>Remove</button>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </section>
  )
}
