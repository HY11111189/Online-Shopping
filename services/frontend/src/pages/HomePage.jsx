import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { normalizeCategories, normalizeCategoryName } from '../lib/categories'
import { money, originalPriceFromDiscount } from '../lib/format'
import { isSessionActive } from '../lib/session'
import { loadWishlist, saveWishlist } from '../lib/wishlist'
import { signinUrl } from '../lib/session'

function CatalogTile({ addedToCart, addToCart, addedToWishlist, item, saved, toggleWishlist, dealLabel = '' }) {
  const originalPrice = originalPriceFromDiscount(item.unitPrice, item.discountPercent)
  return (
    <Link key={item.sku} className={`tile-card${dealLabel ? ' deal-tile' : ''}`} to={`/product.html?sku=${encodeURIComponent(item.sku)}`}>
      {dealLabel ? <span className="deal-ribbon">{dealLabel}</span> : null}
      <div className="tile-image">
        <button
          aria-label={saved ? `Remove ${item.itemName} from saved items` : `Save ${item.itemName}`}
          className="wishlist-heart"
          type="button"
          onClick={(event) => toggleWishlist(item, event)}
        >
          {saved ? '♥' : '♡'}
        </button>
        {item.pictureUrls?.[0] ? <img src={item.pictureUrls[0]} alt={item.itemName} /> : <div className="tile-fallback">No image</div>}
      </div>
      <div className="tile-body">
        <span className="eyebrow">{item.brand || item.category || 'ShopSmart'}</span>
        <h3 className="product-title">{item.itemName}</h3>
        <div className="price-stack">
          {Number(item.discountPercent || 0) > 0 ? (
            <div className="price-now-row">
              <span className="price-now-label">Now</span>
              <strong className="tile-price sale-price">{money(item.unitPrice, item.currencyCode || 'USD')}</strong>
              <span className="list-price">{money(originalPrice, item.currencyCode || 'USD')}</span>
            </div>
          ) : (
            <strong className="tile-price">{money(item.unitPrice, item.currencyCode || 'USD')}</strong>
          )}
        </div>
        <div className="tile-footer tile-footer-actions">
          <button className="tile-cta primary" type="button" onClick={(event) => addToCart(item, event)}>{addedToCart ? 'Added to cart' : 'Add'}</button>
        </div>
      </div>
    </Link>
  )
}

export function HomePage() {
  const { session } = useSession()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [wishlist, setWishlist] = useState(() => loadWishlist())
  const [addedCartSkus, setAddedCartSkus] = useState({})
  const [addedWishlistSkus, setAddedWishlistSkus] = useState({})
  const searchParams = new URLSearchParams(location.search)
  const q = searchParams.get('q') || ''
  const category = searchParams.get('category') || ''
  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.getCategories(),
    staleTime: 300_000,
  })

  const itemsQuery = useQuery({
    queryKey: ['catalog-home', q, category],
    queryFn: () => api.searchItems({ q, category, limit: 36 }),
    staleTime: 60_000,
  })

  const items = useMemo(() => {
    const seen = new Set()
    return (itemsQuery.data || []).filter((item) => {
      if (!item?.sku || seen.has(item.sku)) return false
      seen.add(item.sku)
      return true
    })
  }, [itemsQuery.data])

  const featured = items.slice(0, 8)
  const deals = [...items]
    .filter((item) => Number(item.discountPercent || 0) >= 10)
    .sort((a, b) => Number(b.discountPercent || 0) - Number(a.discountPercent || 0))
    .slice(0, 4)
  const searchMode = Boolean(q)
  const visibleItems = (searchMode || category) ? items : featured
  const categoryList = normalizeCategories(categoriesQuery.data || [])

  async function addToCart(item, event) {
    event.preventDefault()
    event.stopPropagation()
    if (!isSessionActive(session)) {
      navigate(signinUrl(`${window.location.pathname}${window.location.search}`))
      return
    }
    const itemId = `${item.sku}::SHIPPING`
    const cartKey = ['cart', session.customerId]
    const previousCart = queryClient.getQueryData(cartKey)
    queryClient.setQueryData(cartKey, (current) => {
      const base = current || { items: [] }
      const items = [...(base.items || [])]
      const existing = items.find((line) => line.itemId === itemId)
      if (existing) {
        existing.quantity = Number(existing.quantity || 0) + 1
        existing.lineTotal = Number(existing.unitPrice || item.unitPrice || 0) * existing.quantity
      } else {
        items.push({ itemId, itemName: item.itemName, sku: item.sku, quantity: 1, unitPrice: item.unitPrice, lineTotal: item.unitPrice })
      }
      return { ...base, items }
    })
    setAddedCartSkus((current) => ({ ...current, [item.sku]: true }))
    try {
      await api.addCartItem(session.token, session.customerId, { itemId, sku: item.sku, quantity: 1 })
      await queryClient.invalidateQueries({ queryKey: cartKey })
    } catch (error) {
      queryClient.setQueryData(cartKey, previousCart)
      setAddedCartSkus((current) => ({ ...current, [item.sku]: false }))
      if (String(error.message || '').startsWith('401')) navigate(signinUrl(`${window.location.pathname}${window.location.search}`))
    }
  }

  function toggleWishlist(item, event) {
    event.preventDefault()
    event.stopPropagation()
    if (!isSessionActive(session)) {
      navigate(signinUrl(`${window.location.pathname}${window.location.search}`))
      return
    }
    const next = wishlist.includes(item.sku) ? wishlist.filter((sku) => sku !== item.sku) : [...wishlist, item.sku]
    saveWishlist(next)
    setWishlist(next)
    setAddedWishlistSkus((current) => ({ ...current, [item.sku]: !wishlist.includes(item.sku) }))
  }

  return (
    <>
      {/* Hero — only on home, not in search/browse mode */}
      {!searchMode && !category ? (
        <section className="hero-ribbon panel" id="home-hero">
          <div className="hero-copy-block">
            <p className="eyebrow">Spring savings</p>
            <h1>Deals on everyday essentials, tech, and home favorites</h1>
            <p>Shop thousands of items for pickup, delivery, or shipping.</p>
            <div className="hero-actions">
              <a className="primary-button" href="#home-featured-shelf">Shop products</a>
            </div>
          </div>
          <div className="hero-visual" aria-hidden="true">
            <div className="hero-visual-card">
              <img src="https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=80" alt="Spring savings display with home goods and shopping bags" />
            </div>
          </div>
        </section>
      ) : null}

      {/* Product grid */}
      <section className="section-panel panel" id="home-featured-shelf" style={{ marginTop: 18 }}>
        <div className="section-head">
          <div>
            <p className="eyebrow">{searchMode ? 'Search results' : category ? 'Department' : "Today's picks"}</p>
            <h2>{searchMode ? `"${q}"` : category ? category : 'Trending now'}</h2>
            {!searchMode && !category ? <p className="helper">Top items across grocery, electronics, home, and more.</p> : null}
          </div>
          <div className="muted" id="home-catalog-meta">
            {itemsQuery.isLoading ? 'Loading…' : searchMode || category ? `${items.length} results` : ''}
          </div>
        </div>
        {itemsQuery.isError ? (
          <div className="message-box" id="home-catalog-error">Catalog failed to load: {itemsQuery.error?.message || 'Unknown error'}</div>
        ) : null}
        {itemsQuery.isLoading ? (
          <div className="message-box" id="home-catalog-state">Loading products…</div>
        ) : null}
        {!itemsQuery.isLoading && itemsQuery.isSuccess && !visibleItems.length ? (
          <div className="message-box no-results-box" id="home-catalog-state">
            No results found{q ? ` for "${q}"` : category ? ` in "${category}"` : ''}. Try a different search or browse a department.
          </div>
        ) : null}
        <div className="tile-grid" id="home-product-grid" hidden={!visibleItems.length}>
          {visibleItems.map((item) => (
            <CatalogTile
              addedToCart={Boolean(addedCartSkus[item.sku])}
              key={item.sku}
              addToCart={addToCart}
              addedToWishlist={Boolean(addedWishlistSkus[item.sku])}
              item={item}
              saved={wishlist.includes(item.sku)}
              toggleWishlist={toggleWishlist}
            />
          ))}
        </div>
      </section>

      {/* Deals — only on home page */}
      {!searchMode && !category && deals.length ? (
        <section className="section-panel panel" id="home-deals-shelf" style={{ marginTop: 18 }}>
          <div className="section-head">
            <div>
              <p className="eyebrow">Deals</p>
              <h2>Featured savings</h2>
              <p className="helper">Current price drops across the store.</p>
            </div>
          </div>
          <div className="tile-grid" id="home-deals-grid">
            {deals.map((item) => (
              <CatalogTile
                addedToCart={Boolean(addedCartSkus[item.sku])}
                key={item.sku}
                addToCart={addToCart}
                dealLabel="Deal"
                addedToWishlist={Boolean(addedWishlistSkus[item.sku])}
                item={item}
                saved={wishlist.includes(item.sku)}
                toggleWishlist={toggleWishlist}
              />
            ))}
          </div>
        </section>
      ) : null}

      {!searchMode ? (
        <section className="home-category-blocks" aria-label="Popular product picks" style={{ marginTop: 18 }}>
          {categoryList.slice(0, 7).map((dept) => {
            const deptItems = items.filter((item) => normalizeCategoryName(item.category) === dept).slice(0, 4)
            if (!deptItems.length) return null
            return (
              <section className="section-panel panel home-category-panel" key={dept}>
                <div className="section-head">
                  <div>
                    <p className="eyebrow">Popular products</p>
                    <h2>{dept}</h2>
                  </div>
                  <a className="see-all-link" href={`/index.html?category=${encodeURIComponent(dept)}`}>See all</a>
                </div>
                <div className="tile-grid home-category-grid">
                  {deptItems.map((item) => (
                    <CatalogTile
                      addedToCart={Boolean(addedCartSkus[item.sku])}
                      key={`${dept}-${item.sku}`}
                      addToCart={addToCart}
                      addedToWishlist={Boolean(addedWishlistSkus[item.sku])}
                      item={item}
                      saved={wishlist.includes(item.sku)}
                      toggleWishlist={toggleWishlist}
                    />
                  ))}
                </div>
              </section>
            )
          })}
        </section>
      ) : null}

      <footer className="page-footer">
        <div className="page-footer-inner">
          <span>ShopSmart</span>
          <nav className="footer-links" aria-label="Footer links">
            <a href="#home-hero">Top of page</a>
            <Link to="/account.html">Help</Link>
            <a href="mailto:support@shopsmart.local">Contact us</a>
          </nav>
        </div>
      </footer>
    </>
  )
}
