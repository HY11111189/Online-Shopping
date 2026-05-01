import { useEffect, useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { discountAmountFromItem, discountLabel, fulfillmentOf, money, originalPriceFromDiscount } from '../lib/format'
import { loadSelectedCartItems, saveSelectedCartItems } from '../lib/cartSelection'
import { useProductLookupBySku } from '../lib/useProductLookup'
import { signinUrl } from '../lib/session'

export function CartPage() {
  const { session } = useSession()
  const queryClient = useQueryClient()
  const [selectedItemIds, setSelectedItemIds] = useState(() => loadSelectedCartItems())
  const accountQuery = useQuery({
    queryKey: ['account', session.username],
    queryFn: () => api.getMe(session.token),
    enabled: Boolean(session.token),
    staleTime: 300_000,
  })
  const cartQuery = useQuery({
    queryKey: ['cart', session.customerId],
    queryFn: () => api.getCart(session.token, session.customerId),
    enabled: Boolean(session.token && session.customerId),
    staleTime: 0,
  })
  const items = cartQuery.data?.items || []

  if (!session.token) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }
  useEffect(() => {
    const validIds = new Set(items.map((item) => item.itemId))
    const next = selectedItemIds.filter((id) => validIds.has(id))
    if (!items.length) {
      if (selectedItemIds.length) {
        setSelectedItemIds([])
        saveSelectedCartItems([])
      }
      return
    }
    if (next.length !== selectedItemIds.length) {
      setSelectedItemIds(next)
      saveSelectedCartItems(next)
    }
  }, [items, selectedItemIds])

  const selectedItems = useMemo(
    () => items.filter((item) => selectedItemIds.includes(item.itemId)),
    [items, selectedItemIds],
  )
  const shippingItems = items.filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING')
  const pickupItems = items.filter((item) => fulfillmentOf(item.itemId) === 'PICKUP')
  const subtotal = selectedItems.reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0)
  const shippingSubtotal = selectedItems
    .filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING')
    .reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0)
  const isPremium = accountQuery.data?.membershipLevel === 'PREMIUM'
  const shippingCost = shippingSubtotal && !isPremium && shippingSubtotal < 35 ? 6 : 0
  const lookupSkus = items.map((item) => item.sku || item.itemId?.split('::')?.[0] || '')
  const productLookup = useProductLookupBySku(lookupSkus)
  const originalSubtotal = selectedItems.reduce((sum, item) => {
    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
    const preview = productLookup[lookupSku] || item
    const current = Number(preview.unitPrice || item.unitPrice || 0)
    const listed = Number(preview.listPrice || 0)
    const percent = Number(preview.discountPercent || 0)
    const originalUnit = listed > current ? listed : (current && percent > 0 ? originalPriceFromDiscount(current, percent) : current)
    return sum + Number(originalUnit || 0) * Number(item.quantity || 0)
  }, 0)
  const discountTotal = selectedItems.reduce((sum, item) => {
    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
    return sum + discountAmountFromItem({ ...item, ...(productLookup[lookupSku] || {}) })
  }, 0)
  const total = subtotal + shippingCost
  const unitCount = selectedItems.reduce((sum, item) => sum + Number(item.quantity || 0), 0)

  function toggleItem(itemId) {
    const next = selectedItemIds.includes(itemId)
      ? selectedItemIds.filter((id) => id !== itemId)
      : [...selectedItemIds, itemId]
    setSelectedItemIds(next)
    saveSelectedCartItems(next)
  }

  function toggleAll(checked) {
    const next = checked ? items.map((item) => item.itemId) : []
    setSelectedItemIds(next)
    saveSelectedCartItems(next)
  }

  async function changeQuantity(item, quantity) {
    if (quantity <= 0) {
      await api.removeCartItem(session.token, session.customerId, item.itemId)
    } else {
      await api.updateCartItem(session.token, session.customerId, item.itemId, {
        itemId: item.itemId,
        sku: item.sku || item.itemId?.split('::')?.[0] || '',
        itemName: item.itemName,
        upc: item.upc,
        quantity,
        unitPrice: item.unitPrice,
      })
    }
    await queryClient.invalidateQueries({ queryKey: ['cart', session.customerId] })
  }

  function renderCartGroup(title, groupItems) {
    if (!groupItems.length) return null
    return (
      <section className="cart-group" key={title}>
        <div className="section-head compact-head">
          <div>
            <p className="eyebrow">{title === 'Delivery' ? 'Ships to address' : 'Pickup at store'}</p>
            <h3>{title}</h3>
          </div>
        </div>
        <div className="stack">
          {groupItems.map((item) => {
            const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
            const preview = productLookup[lookupSku] || item
            const imageUrl = preview.pictureUrls?.[0] || item.pictureUrls?.[0] || ''
            return (
              <article key={item.itemId} className="cart-row panel">
                <div className="cart-row-main">
                  <label className="cart-select">
                    <input checked={selectedItemIds.includes(item.itemId)} type="checkbox" onChange={() => toggleItem(item.itemId)} />
                  </label>
                  <div className="cart-item-media">
                    {imageUrl ? <img src={imageUrl} alt={preview.itemName || item.itemName} /> : <div className="cart-item-fallback">No image</div>}
                  </div>
                  <div className="cart-item-copy">
                    <h3>{item.itemName}</h3>
                    <p className="helper">{lookupSku}</p>
                    <span className={`tag cart-fulfillment-tag${title === 'Pickup' ? ' low' : ''}`}>{title}</span>
                  </div>
                </div>
                <div className="cart-row-actions">
                  <div className="qty-group cart-qty-group">
                    <button className="qty-button" type="button" onClick={() => changeQuantity(item, item.quantity - 1)}>-</button>
                    <strong>{item.quantity}</strong>
                    <button className="qty-button" type="button" onClick={() => changeQuantity(item, item.quantity + 1)}>+</button>
                  </div>
                  <strong className="cart-row-price">{money(item.lineTotal || item.unitPrice * item.quantity)}</strong>
                </div>
              </article>
          )})}
        </div>
      </section>
    )
  }

  return (
    <section className="layout-two">
      <article className="section-panel panel">
        <div className="section-head">
          <div>
            <p className="eyebrow">Cart review</p>
            <h2>Your cart</h2>
          </div>
          <button className="secondary-button" type="button" onClick={() => queryClient.invalidateQueries({ queryKey: ['cart', session.customerId] })}>Reload cart</button>
        </div>
        <label className="cart-select" style={{ marginBottom: 16 }}>
          <input
            checked={items.length > 0 && selectedItemIds.length === items.length}
            type="checkbox"
            onChange={(event) => toggleAll(event.target.checked)}
          />
          <span>Select all items for this checkout</span>
        </label>
        <div className="stack" id="cart-page-items">
          {renderCartGroup('Delivery', shippingItems)}
          {renderCartGroup('Pickup', pickupItems)}
        </div>
      </article>
      <aside className="side-panel panel sticky-column">
        <div className="section-head">
          <div>
            <p className="eyebrow">Subtotal</p>
            <h2>Review and checkout</h2>
          </div>
        </div>
        <div className="summary-list">
          <div className="summary-line"><span>Selected items</span><strong id="cart-page-unit-count">{unitCount}</strong></div>
          <div className="summary-line"><span>Original price</span><strong>{money(originalSubtotal)}</strong></div>
          <div className="summary-line"><span>Discount</span><strong>{discountTotal > 0 ? discountLabel(discountTotal) : '$0.00'}</strong></div>
          <div className="summary-line"><span>Shipping</span><strong>{shippingCost ? money(shippingCost) : shippingSubtotal ? (isPremium ? 'Free with ShopSmart+' : 'Free over $35') : '$0.00'}</strong></div>
          <div className="summary-line"><span>Membership</span><strong>{isPremium ? 'ShopSmart+ member' : 'Regular member'}</strong></div>
          <div className="summary-line total"><span>Estimated total</span><strong id="cart-page-total">{money(total)}</strong></div>
        </div>
        <div className="stack">
          <Link className="primary-button" to="/checkout.html">Continue to checkout</Link>
          <Link className="secondary-button" to="/index.html">Keep shopping</Link>
        </div>
      </aside>
    </section>
  )
}
