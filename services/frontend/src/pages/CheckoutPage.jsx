import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { compactAddress, discountAmountFromItem, discountLabel, fulfillmentOf, money } from '../lib/format'
import { clearSelectedCartItems, loadSelectedCartItems } from '../lib/cartSelection'
import { useProductLookupBySku } from '../lib/useProductLookup'
import { signinUrl } from '../lib/session'

const DEMO_PICKUP_LOCATION = 'ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL'
const EMPTY_NEW_ADDRESS = { recipientName: '', addressLine1: '', addressLine2: '', city: '', state: '', postalCode: '', country: 'US' }

function addressFromAccount(address, phoneNumber) {
  if (!address) return null
  return {
    recipientName: address.recipientName,
    addressLine1: address.addressLine1,
    addressLine2: address.addressLine2,
    city: address.city,
    state: address.state,
    postalCode: address.postalCode,
    country: address.country,
    phoneNumber,
  }
}

export function CheckoutPage() {
  const { session, setSession } = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [shippingAddressId, setShippingAddressId] = useState('')
  const [billingAddressId, setBillingAddressId] = useState('')
  const [paymentId, setPaymentId] = useState('')
  const [billingSameAsShipping, setBillingSameAsShipping] = useState(true)
  const [selectedItemIds, setSelectedItemIds] = useState(() => loadSelectedCartItems())
  const [useNewAddress, setUseNewAddress] = useState(false)
  const [newAddress, setNewAddress] = useState(EMPTY_NEW_ADDRESS)

  if (!session.token) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }

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

  const createOrderMutation = useMutation({
    mutationFn: (payload) => api.createOrder(session.token, payload),
    onSuccess: async (order) => {
      for (const itemId of selectedItemIds) {
        await api.removeCartItem(session.token, session.customerId, itemId)
      }
      clearSelectedCartItems()
      setSession({ ...session, lastOrderNumber: order.orderNumber })
      navigate(`/order-status.html?orderNumber=${encodeURIComponent(order.orderNumber)}`)
      await queryClient.invalidateQueries({ queryKey: ['orders', session.customerId] })
      await queryClient.invalidateQueries({ queryKey: ['cart', session.customerId] })
    },
  })

  const account = accountQuery.data
  const cart = cartQuery.data
  const cartItems = cart?.items || []
  useEffect(() => {
    if (!cartItems.length) return
    if (!selectedItemIds.length) {
      setSelectedItemIds(cartItems.map((item) => item.itemId))
    }
  }, [cartItems, selectedItemIds.length])
  const items = useMemo(
    () => cartItems.filter((item) => selectedItemIds.includes(item.itemId)),
    [cartItems, selectedItemIds],
  )
  const lookupSkus = items.map((item) => item.sku || item.itemId?.split('::')?.[0] || '')
  const productLookup = useProductLookupBySku(lookupSkus)
  const addresses = account?.addresses || []
  const paymentMethods = account?.paymentMethods || []
  const subtotal = items.reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0)
  const shippingItems = items.filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING')
  const pickupItems = items.filter((item) => fulfillmentOf(item.itemId) === 'PICKUP')
  const isPremium = account?.membershipLevel === 'PREMIUM'
  const shippingSubtotal = shippingItems.reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0)
  const discountTotal = items.reduce((sum, item) => {
    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
    return sum + discountAmountFromItem({ ...item, ...(productLookup[lookupSku] || {}) })
  }, 0)
  const shippingCost = shippingItems.length ? (isPremium || shippingSubtotal >= 35 ? 0 : 6) : 0
  const total = subtotal + shippingCost
  const savedShippingAddress = useMemo(
    () => addresses.find((address) => String(address.id) === String(shippingAddressId)) || null,
    [addresses, shippingAddressId],
  )
  const selectedShippingAddress = useNewAddress ? newAddress : savedShippingAddress
  const selectedBillingAddress = useMemo(() => {
    if (billingSameAsShipping) return selectedShippingAddress
    return addresses.find((address) => String(address.id) === String(billingAddressId)) || null
  }, [addresses, billingAddressId, billingSameAsShipping, selectedShippingAddress])
  const selectedPayment = useMemo(
    () => paymentMethods.find((payment) => String(payment.id) === String(paymentId)) || null,
    [paymentId, paymentMethods],
  )

  useEffect(() => {
    if (!addresses.length) return
    const defaultAddress = addresses.find((item) => item.defaultAddress) || addresses[0]
    setShippingAddressId(String(defaultAddress.id))
    setBillingAddressId(String(defaultAddress.id))
  }, [addresses])

  useEffect(() => {
    if (!paymentMethods.length) return
    const defaultPayment = paymentMethods.find((item) => item.defaultMethod) || paymentMethods[0]
    setPaymentId(String(defaultPayment.id))
  }, [paymentMethods])

  function placeOrder() {
    createOrderMutation.mutate({
      customerId: session.customerId,
      currencyCode: 'USD',
      taxAmount: '0.00',
      shippingAmount: shippingCost.toFixed(2),
      shippingAddress: shippingItems.length ? addressFromAccount(selectedShippingAddress, account?.phoneNumber) : null,
      billingAddress: addressFromAccount(selectedBillingAddress, account?.phoneNumber),
      paymentMethod: selectedPayment?.paymentMethodType || 'CREDIT_CARD',
      discountAmount: discountTotal.toFixed(2),
      items: items.map((item) => ({
        itemId: item.itemId,
        sku: item.sku,
        itemName: item.itemName,
        upc: item.upc,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        lineTotal: item.lineTotal,
      })),
      createRequestId: `react-checkout-${Date.now()}`,
    })
  }

  return (
    <section className="layout-two checkout-layout">
      <article className="checkout-main stack">
        <section className="checkout-panel panel">
          <div className="section-head">
            <div>
              <p className="eyebrow">Review items</p>
              <h2>Your items</h2>
              <p className="section-note">Review shipping and pickup items separately before placing the order.</p>
            </div>
          </div>
          <div className="order-detail-grid" id="checkout-fulfillment-grid">
            {shippingItems.length ? (
              <article className="status-card">
                <span className="eyebrow">Shipping items</span>
                <strong>{shippingItems.length}</strong>
              </article>
            ) : null}
            {pickupItems.length ? (
              <article className="status-card">
                <span className="eyebrow">Pickup items</span>
                <strong>{pickupItems.length}</strong>
              </article>
            ) : null}
          </div>
          <div className="checkout-review-list" id="checkout-review-items">
            {!items.length ? <div className="message-box">Select items in your cart to review them here.</div> : null}
            {shippingItems.length ? (
              <div className="stack" style={{ marginBottom: 18 }}>
                <h3>Shipping items</h3>
                {shippingItems.map((item) => (
                  <div key={item.itemId} className="review-item-row">
                    <div className="review-item-media">
                      {(() => {
                        const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
                        const preview = productLookup[lookupSku] || item
                        const imageUrl = preview.pictureUrls?.[0] || item.pictureUrls?.[0] || ''
                        return imageUrl ? <img src={imageUrl} alt={preview.itemName || item.itemName} /> : <div className="review-item-fallback">No image</div>
                      })()}
                    </div>
                    <div className="review-item-copy">
                      <strong>{item.itemName}</strong>
                      <span className="muted">{item.quantity} · {money(item.lineTotal || item.unitPrice * item.quantity)}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
            {pickupItems.length ? (
              <div className="stack">
                <h3>Pickup items</h3>
                {pickupItems.map((item) => (
                  <div key={item.itemId} className="review-item-row">
                    <div className="review-item-media">
                      {(() => {
                        const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
                        const preview = productLookup[lookupSku] || item
                        const imageUrl = preview.pictureUrls?.[0] || item.pictureUrls?.[0] || ''
                        return imageUrl ? <img src={imageUrl} alt={preview.itemName || item.itemName} /> : <div className="review-item-fallback">No image</div>
                      })()}
                    </div>
                    <div className="review-item-copy">
                      <strong>{item.itemName}</strong>
                      <span className="muted">{item.quantity} · {money(item.lineTotal || item.unitPrice * item.quantity)}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        </section>

        <section className="checkout-panel panel">
          <div className="section-head">
            <div>
              <p className="eyebrow">Fulfillment</p>
              <h2>Delivery &amp; Pickup</h2>
            </div>
            <Link className="secondary-button" to="/account.html">Manage in account</Link>
          </div>
          {shippingItems.length ? (
            <div className="stack" id="checkout-shipping-panel" style={{ marginBottom: pickupItems.length ? 18 : 0 }}>
              <h3 style={{ margin: 0 }}>Delivery address</h3>
              <div className="checkout-address-tabs">
                <button
                  type="button"
                  className={`checkout-addr-tab${!useNewAddress ? ' active' : ''}`}
                  onClick={() => setUseNewAddress(false)}
                >Saved addresses</button>
                <button
                  type="button"
                  className={`checkout-addr-tab${useNewAddress ? ' active' : ''}`}
                  onClick={() => setUseNewAddress(true)}
                >+ Enter new address</button>
              </div>
              {!useNewAddress ? (
                <label className="field">
                  <span>Select an address</span>
                  <select id="checkout-shipping-address" value={shippingAddressId} onChange={(e) => setShippingAddressId(e.target.value)}>
                    <option value="">Choose address</option>
                    {addresses.map((address) => (
                      <option key={address.id} value={address.id}>{compactAddress(address)}</option>
                    ))}
                  </select>
                </label>
              ) : (
                <div className="stack checkout-new-address-form">
                  <label className="field"><span>Full name</span><input type="text" placeholder="Recipient name" value={newAddress.recipientName} onChange={(e) => setNewAddress({ ...newAddress, recipientName: e.target.value })} /></label>
                  <label className="field"><span>Address line 1</span><input type="text" placeholder="Street address" value={newAddress.addressLine1} onChange={(e) => setNewAddress({ ...newAddress, addressLine1: e.target.value })} /></label>
                  <label className="field"><span>Address line 2 (optional)</span><input type="text" placeholder="Apt, suite, etc." value={newAddress.addressLine2} onChange={(e) => setNewAddress({ ...newAddress, addressLine2: e.target.value })} /></label>
                  <div className="field-row">
                    <label className="field"><span>City</span><input type="text" value={newAddress.city} onChange={(e) => setNewAddress({ ...newAddress, city: e.target.value })} /></label>
                    <label className="field"><span>State</span><input type="text" maxLength="2" placeholder="IL" value={newAddress.state} onChange={(e) => setNewAddress({ ...newAddress, state: e.target.value })} /></label>
                    <label className="field"><span>ZIP</span><input type="text" maxLength="10" value={newAddress.postalCode} onChange={(e) => setNewAddress({ ...newAddress, postalCode: e.target.value })} /></label>
                  </div>
                </div>
              )}
            </div>
          ) : null}
          {pickupItems.length ? (
            <div className="stack" id="checkout-pickup-panel">
              <h3 style={{ margin: 0 }}>Pickup location</h3>
              <div className="message-box">
                <strong id="checkout-pickup-location">{DEMO_PICKUP_LOCATION}</strong>
              </div>
            </div>
          ) : null}
          {!shippingItems.length && !pickupItems.length ? (
            <div className="message-box">Add items to your cart to select fulfillment options.</div>
          ) : null}
        </section>

        <section className="checkout-panel panel">
          <div className="section-head">
            <div>
              <p className="eyebrow">Payment</p>
              <h2>Billing and payment method</h2>
            </div>
          </div>
          <div className="stack">
            <div className="field-row">
              <label className="field">
                <span>Payment method</span>
                <select id="checkout-payment-method" value={paymentId} onChange={(e) => setPaymentId(e.target.value)}>
                  <option value="">Choose a payment method</option>
                  {paymentMethods.map((payment) => (
                    <option key={payment.id} value={payment.id}>{payment.provider} — {payment.maskedNumber}</option>
                  ))}
                </select>
              </label>
              <label className="field checkout-link-field">
                <span>&nbsp;</span>
                <Link className="secondary-button" id="checkout-add-payment-link" to="/account.html">Add payment</Link>
              </label>
            </div>
            <label className="field checkbox-field checkout-same-address">
              <input id="checkout-billing-same" type="checkbox" checked={billingSameAsShipping} onChange={(e) => setBillingSameAsShipping(e.target.checked)} />
              <span>Billing address is the same as shipping</span>
            </label>
            <label className="field">
              <span>Select billing address</span>
              <select id="checkout-billing-address" value={billingSameAsShipping ? shippingAddressId : billingAddressId} onChange={(e) => setBillingAddressId(e.target.value)} disabled={billingSameAsShipping}>
                <option value="">Choose address</option>
                {addresses.map((address) => (
                  <option key={address.id} value={address.id}>{compactAddress(address)}</option>
                ))}
              </select>
            </label>
            <div className="stack" id="checkout-billing-fields" hidden={billingSameAsShipping}>
              <label className="field"><span>Billing recipient</span><input id="bill-recipient" type="text" readOnly value={selectedBillingAddress?.recipientName || ''} /></label>
              <label className="field"><span>Billing line 1</span><input id="bill-line1" type="text" readOnly value={selectedBillingAddress?.addressLine1 || ''} /></label>
              <label className="field"><span>Billing line 2</span><input id="bill-line2" type="text" readOnly value={selectedBillingAddress?.addressLine2 || ''} /></label>
              <div className="field-row">
                <label className="field"><span>City</span><input id="bill-city" type="text" readOnly value={selectedBillingAddress?.city || ''} /></label>
                <label className="field"><span>State</span><input id="bill-state" type="text" readOnly value={selectedBillingAddress?.state || ''} /></label>
              </div>
              <div className="field-row">
                <label className="field"><span>Postal code</span><input id="bill-postal" type="text" readOnly value={selectedBillingAddress?.postalCode || ''} /></label>
                <label className="field"><span>Country</span><input id="bill-country" type="text" readOnly value={selectedBillingAddress?.country || 'US'} /></label>
              </div>
              <label className="field"><span>Phone number</span><input id="bill-phone" type="text" readOnly value={account?.phoneNumber || ''} /></label>
            </div>
            <div className="message-box" id="checkout-page-message" hidden={!createOrderMutation.isError}>
              {createOrderMutation.error?.message || 'Choose your address and payment method, then place your order.'}
            </div>
          </div>
        </section>
      </article>

      <aside className="checkout-panel panel sticky-column checkout-summary-panel">
        <div className="section-head">
          <div>
            <p className="eyebrow">Order summary</p>
            <h2>Final total</h2>
          </div>
        </div>
        <div className="summary-list checkout-summary-list">
          <div className="summary-line"><span>Items</span><strong id="checkout-order-items">{items.length}</strong></div>
          <div className="summary-line"><span>Subtotal</span><strong id="checkout-order-subtotal">{money(subtotal)}</strong></div>
          <div className="summary-line"><span>Shipping</span><strong id="checkout-order-shipping">{shippingCost ? money(shippingCost) : (shippingItems.length ? (isPremium ? 'Free with ShopSmart+' : 'Free over $35') : '$0.00')}</strong></div>
          <div className="summary-line"><span>Tax</span><strong id="checkout-order-tax">$0.00</strong></div>
          <div className="summary-line"><span>Discount</span><strong id="checkout-order-discount">{discountTotal > 0 ? discountLabel(discountTotal) : '$0.00'}</strong></div>
          <div className="summary-line"><span>Membership</span><strong>{isPremium ? 'ShopSmart+ member' : 'Regular member'}</strong></div>
          <div className="summary-line"><span>Payment</span><strong id="checkout-payment-status">{selectedPayment ? 'Ready' : 'Choose a method'}</strong></div>
          <div className="summary-line total"><span>Total</span><strong id="checkout-order-total">{money(total)}</strong></div>
        </div>
        <div className="stack" style={{ marginTop: 14 }}>
          <button
            className="primary-button"
            type="button"
            id="checkout-pay-now"
            onClick={placeOrder}
            disabled={createOrderMutation.isPending || !items.length || !selectedPayment || !selectedBillingAddress || (shippingItems.length > 0 && !selectedShippingAddress)}
          >
            {createOrderMutation.isPending ? 'Placing order...' : 'Place order'}
          </button>
          <Link className="secondary-button" to="/cart.html">Back to cart</Link>
        </div>
      </aside>
    </section>
  )
}
