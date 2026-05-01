import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { compactAddress, discountLabel, fulfillmentOf, money, originalPriceFromDiscount } from '../lib/format'
import { clearSelectedCartItems, loadSelectedCartItems } from '../lib/cartSelection'
import { useProductLookupBySku } from '../lib/useProductLookup'
import { signinUrl } from '../lib/session'

const DEMO_PICKUP_LOCATION = 'ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL'
const ADD_ADDRESS_VALUE = '__add_address__'
const ADD_PAYMENT_VALUE = '__add_payment__'
const EMPTY_NEW_ADDRESS = {
  label: '',
  recipientName: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'US',
  addressType: 'SHIPPING',
  defaultAddress: false,
}
const EMPTY_NEW_PAYMENT = {
  paymentMethodType: 'CREDIT_CARD',
  provider: '',
  accountToken: '',
  maskedNumber: '',
  cardholderName: '',
  expiryMonth: '',
  expiryYear: '',
  defaultMethod: false,
  active: true,
}

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

function sortAddresses(addresses = []) {
  return [...addresses].sort((left, right) => Number(Boolean(right.defaultAddress)) - Number(Boolean(left.defaultAddress)))
}

function sortPaymentMethods(paymentMethods = []) {
  return [...paymentMethods].sort((left, right) => Number(Boolean(right.defaultMethod)) - Number(Boolean(left.defaultMethod)))
}

function normalizeAccountPayload(account, nextAddresses, nextPaymentMethods) {
  return {
    username: account?.username || '',
    fullName: account?.fullName || '',
    email: account?.email || '',
    password: '',
    phoneNumber: account?.phoneNumber || '',
    membershipLevel: account?.membershipLevel || 'REGULAR',
    addresses: nextAddresses,
    paymentMethods: nextPaymentMethods,
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
  const [paymentEditorOpen, setPaymentEditorOpen] = useState(false)
  const [newPayment, setNewPayment] = useState(EMPTY_NEW_PAYMENT)
  const [checkoutMessage, setCheckoutMessage] = useState('')

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
  const addresses = useMemo(() => sortAddresses(account?.addresses || []), [account?.addresses])
  const paymentMethods = useMemo(() => sortPaymentMethods(account?.paymentMethods || []), [account?.paymentMethods])
  const subtotal = items.reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0)
  const originalSubtotal = items.reduce((sum, item) => {
    const lookupSku = item.sku || item.itemId?.split('::')?.[0] || ''
    const preview = productLookup[lookupSku] || item
    const current = Number(preview.unitPrice || item.unitPrice || 0)
    const listed = Number(preview.listPrice || 0)
    const percent = Number(preview.discountPercent || 0)
    const originalUnit = listed > current ? listed : (current && percent > 0 ? originalPriceFromDiscount(current, percent) : current)
    return sum + Number(originalUnit || 0) * Number(item.quantity || 0)
  }, 0)
  const shippingItems = items.filter((item) => fulfillmentOf(item.itemId) === 'SHIPPING')
  const pickupItems = items.filter((item) => fulfillmentOf(item.itemId) === 'PICKUP')
  const isPremium = account?.membershipLevel === 'PREMIUM'
  const shippingSubtotal = shippingItems.reduce((sum, item) => sum + Number(item.lineTotal || item.unitPrice * item.quantity), 0)
  const shippingCost = shippingItems.length ? (isPremium || shippingSubtotal >= 35 ? 0 : 6) : 0
  const discountAmount = Math.max(0, originalSubtotal - subtotal)
  const amountDue = subtotal + shippingCost
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

  const saveAccountMutation = useMutation({
    mutationFn: ({ payload, kind }) => api.updateAccount(session.token, session.customerId, payload).then((updatedAccount) => ({ updatedAccount, kind })),
    onSuccess: async ({ updatedAccount, kind }) => {
      queryClient.setQueryData(['account', session.username], updatedAccount)
      await queryClient.invalidateQueries({ queryKey: ['account', session.username] })
      const updatedAddresses = sortAddresses(updatedAccount?.addresses || [])
      const updatedPayments = sortPaymentMethods(updatedAccount?.paymentMethods || [])
      if (kind === 'address') {
        const newSavedAddress = updatedAddresses.find((address) => {
          return String(address.label || '') === String(newAddress.label || '')
            && String(address.recipientName || '') === String(newAddress.recipientName || '')
            && String(address.addressLine1 || '') === String(newAddress.addressLine1 || '')
            && String(address.city || '') === String(newAddress.city || '')
            && String(address.postalCode || '') === String(newAddress.postalCode || '')
        }) || updatedAddresses[0]
        if (newSavedAddress) {
          setShippingAddressId(String(newSavedAddress.id))
          setBillingAddressId(String(newSavedAddress.id))
        }
        setUseNewAddress(false)
        setNewAddress(EMPTY_NEW_ADDRESS)
      }
      if (kind === 'payment') {
        const newSavedPayment = updatedPayments.find((payment) => {
          return String(payment.provider || '') === String(newPayment.provider || '')
            && String(payment.maskedNumber || '') === String(newPayment.maskedNumber || '')
            && String(payment.cardholderName || '') === String(newPayment.cardholderName || '')
        }) || updatedPayments[0]
        if (newSavedPayment) {
          setPaymentId(String(newSavedPayment.id))
        }
        setPaymentEditorOpen(false)
        setNewPayment(EMPTY_NEW_PAYMENT)
      }
      setCheckoutMessage(kind === 'address' ? 'Address saved.' : 'Payment saved.')
    },
    onError: (error) => {
      setCheckoutMessage(error.message)
    },
  })

  useEffect(() => {
    if (!addresses.length) {
      setUseNewAddress(true)
      return
    }
    const defaultAddress = addresses.find((item) => item.defaultAddress) || addresses[0]
    setShippingAddressId(String(defaultAddress.id))
    setBillingAddressId(String(defaultAddress.id))
    setUseNewAddress(false)
  }, [addresses])

  useEffect(() => {
    if (!paymentMethods.length) {
      setPaymentEditorOpen(true)
      return
    }
    const defaultPayment = paymentMethods.find((item) => item.defaultMethod) || paymentMethods[0]
    setPaymentId(String(defaultPayment.id))
    setPaymentEditorOpen(false)
  }, [paymentMethods])

  useEffect(() => {
    if (!selectedShippingAddress) return
    if (!billingSameAsShipping) return
    setBillingAddressId(String(selectedShippingAddress.id || ''))
  }, [billingSameAsShipping, selectedShippingAddress])

  function saveAddress() {
    if (!account) return
    if (!newAddress.recipientName || !newAddress.addressLine1 || !newAddress.city || !newAddress.state || !newAddress.postalCode) {
      setCheckoutMessage('Enter the recipient name, street, city, state, and ZIP code.')
      return
    }
    const nextAddresses = addresses
      .filter((address) => String(address.id) !== '')
      .map((address) => ({ ...address, defaultAddress: newAddress.defaultAddress ? false : address.defaultAddress }))
    nextAddresses.push({
      ...newAddress,
      id: undefined,
      country: newAddress.country || 'US',
    })
    saveAccountMutation.mutate({ kind: 'address', payload: normalizeAccountPayload(account, nextAddresses, paymentMethods) })
  }

  function savePayment() {
    if (!account) return
    if (!newPayment.provider || !newPayment.maskedNumber) {
      setCheckoutMessage('Enter a provider name and card number to save the payment method.')
      return
    }
    const normalizedPayment = {
      ...newPayment,
      expiryMonth: newPayment.expiryMonth ? Number(newPayment.expiryMonth) : null,
      expiryYear: newPayment.expiryYear ? Number(newPayment.expiryYear) : null,
    }
    const nextPayments = paymentMethods.map((payment) => ({ ...payment, defaultMethod: newPayment.defaultMethod ? false : payment.defaultMethod }))
    nextPayments.push({
      ...normalizedPayment,
      id: undefined,
    })
    saveAccountMutation.mutate({ kind: 'payment', payload: normalizeAccountPayload(account, addresses, nextPayments) })
  }

  function handleShippingSelection(value) {
    if (value === ADD_ADDRESS_VALUE) {
      setUseNewAddress(true)
      return
    }
    setUseNewAddress(false)
    setShippingAddressId(value)
    if (billingSameAsShipping) {
      setBillingAddressId(value)
    }
  }

  function handlePaymentSelection(value) {
    if (value === ADD_PAYMENT_VALUE) {
      setPaymentEditorOpen(true)
      return
    }
    setPaymentEditorOpen(false)
    setPaymentId(value)
  }

  function placeOrder() {
    createOrderMutation.mutate({
      customerId: session.customerId,
      currencyCode: 'USD',
      taxAmount: '0.00',
      shippingAddress: shippingItems.length ? addressFromAccount(selectedShippingAddress, account?.phoneNumber) : null,
      billingAddress: addressFromAccount(selectedBillingAddress, account?.phoneNumber),
      paymentMethod: selectedPayment?.paymentMethodType || 'CREDIT_CARD',
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
          </div>
          {shippingItems.length ? (
            <div className="stack" id="checkout-shipping-panel" style={{ marginBottom: pickupItems.length ? 18 : 0 }}>
              <div className="section-head compact-head">
                <div>
                  <p className="eyebrow">Delivery address</p>
                  <h3 style={{ margin: '4px 0 0' }}>Select or add</h3>
                </div>
              </div>
              <div className="checkout-picker-shell">
                <label className="field checkout-picker-select">
                  <span>Choose delivery address</span>
                  <select
                    id="checkout-shipping-address"
                    className="checkout-select"
                    value={shippingAddressId}
                    onChange={(e) => handleShippingSelection(e.target.value)}
                  >
                    <option value="">Choose address</option>
                    {addresses.map((address) => (
                      <option key={address.id} value={address.id}>{compactAddress(address)}</option>
                    ))}
                  </select>
                </label>
                <button className="secondary-button checkout-picker-action" type="button" onClick={() => setUseNewAddress(true)}>
                  + Add new address
                </button>
              </div>
              <div className="checkout-picker-preview">
                <span className="eyebrow">{useNewAddress ? 'Adding new address' : 'Selected address'}</span>
                <strong>{useNewAddress ? 'Enter a new delivery address below' : (selectedShippingAddress ? compactAddress(selectedShippingAddress) : 'Choose an address')}</strong>
                <small>{useNewAddress ? 'New address' : (selectedShippingAddress ? `${selectedShippingAddress.addressLine1}${selectedShippingAddress.addressLine2 ? `, ${selectedShippingAddress.addressLine2}` : ''}` : 'Saved address')}</small>
              </div>
              {useNewAddress ? (
                <div className="stack checkout-new-address-form">
                  <label className="field"><span>Label</span><input type="text" placeholder="Home, work, etc." value={newAddress.label} onChange={(e) => setNewAddress({ ...newAddress, label: e.target.value })} /></label>
                  <label className="field"><span>Recipient</span><input type="text" placeholder="Recipient name" value={newAddress.recipientName} onChange={(e) => setNewAddress({ ...newAddress, recipientName: e.target.value })} /></label>
                  <label className="field"><span>Address line 1</span><input type="text" placeholder="Street address" value={newAddress.addressLine1} onChange={(e) => setNewAddress({ ...newAddress, addressLine1: e.target.value })} /></label>
                  <label className="field"><span>Address line 2 (optional)</span><input type="text" placeholder="Apt, suite, etc." value={newAddress.addressLine2} onChange={(e) => setNewAddress({ ...newAddress, addressLine2: e.target.value })} /></label>
                  <div className="field-row">
                    <label className="field"><span>City</span><input type="text" value={newAddress.city} onChange={(e) => setNewAddress({ ...newAddress, city: e.target.value })} /></label>
                    <label className="field"><span>State</span><input type="text" maxLength="2" placeholder="IL" value={newAddress.state} onChange={(e) => setNewAddress({ ...newAddress, state: e.target.value.toUpperCase() })} /></label>
                    <label className="field"><span>ZIP</span><input type="text" maxLength="10" value={newAddress.postalCode} onChange={(e) => setNewAddress({ ...newAddress, postalCode: e.target.value })} /></label>
                  </div>
                  <div className="field-row">
                    <label className="field"><span>Country</span><input type="text" value={newAddress.country} onChange={(e) => setNewAddress({ ...newAddress, country: e.target.value })} /></label>
                    <label className="field"><span>Type</span>
                      <select value={newAddress.addressType} onChange={(e) => setNewAddress({ ...newAddress, addressType: e.target.value })}>
                        <option value="SHIPPING">Shipping</option>
                        <option value="BILLING">Billing</option>
                        <option value="HOME">Home</option>
                        <option value="WORK">Work</option>
                      </select>
                    </label>
                    <label className="field checkbox-field"><input type="checkbox" checked={newAddress.defaultAddress} onChange={(e) => setNewAddress({ ...newAddress, defaultAddress: e.target.checked })} /><span>Default address</span></label>
                  </div>
                  <div className="inline-actions">
                    <button className="primary-button" type="button" onClick={saveAddress} disabled={saveAccountMutation.isPending}>Save address</button>
                    <button className="secondary-button" type="button" onClick={() => setUseNewAddress(false)}>Cancel</button>
                  </div>
                </div>
              ) : null}
              {useNewAddress && checkoutMessage ? <div className="message-box">{checkoutMessage}</div> : null}
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
            <div className="section-head compact-head">
              <div>
                <p className="eyebrow">Payment method</p>
                <h3 style={{ margin: '4px 0 0' }}>Select or add</h3>
              </div>
            </div>
            <div className="checkout-picker-shell">
              <label className="field checkout-picker-select">
                <span>Choose payment method</span>
                <select
                  id="checkout-payment-method"
                  className="checkout-select"
                  value={paymentId}
                  onChange={(e) => handlePaymentSelection(e.target.value)}
                >
                  <option value="">Choose a payment method</option>
                  {paymentMethods.map((payment) => (
                    <option key={payment.id} value={payment.id}>{payment.provider} — {payment.maskedNumber}</option>
                  ))}
                </select>
              </label>
              <button className="secondary-button checkout-picker-action" type="button" onClick={() => setPaymentEditorOpen(true)}>
                + Add new payment method
              </button>
            </div>
            <div className="checkout-picker-preview">
              <span className="eyebrow">{paymentEditorOpen ? 'Adding new payment' : 'Selected payment'}</span>
              <strong>{paymentEditorOpen ? 'Enter a new payment method below' : (selectedPayment ? `${selectedPayment.provider} • ${selectedPayment.maskedNumber}` : 'Choose a payment method')}</strong>
              <small>{paymentEditorOpen ? 'New payment method' : (selectedPayment ? `${selectedPayment.paymentMethodType || 'Payment method'}${selectedPayment.defaultMethod ? ' • Default' : ''}` : 'Saved payment method')}</small>
            </div>
            {paymentEditorOpen ? (
              <div className="stack checkout-new-address-form">
                <div className="field-row">
                  <label className="field">
                    <span>Method</span>
                    <select value={newPayment.paymentMethodType} onChange={(e) => setNewPayment({ ...newPayment, paymentMethodType: e.target.value })}>
                      <option value="CREDIT_CARD">Credit card</option>
                      <option value="DEBIT_CARD">Debit card</option>
                      <option value="WALLET">Wallet</option>
                      <option value="GIFT_CARD">Gift card</option>
                    </select>
                  </label>
                  <label className="field"><span>Provider</span><input type="text" placeholder="Visa, Mastercard, etc." value={newPayment.provider} onChange={(e) => setNewPayment({ ...newPayment, provider: e.target.value })} /></label>
                </div>
                <label className="field"><span>Cardholder</span><input type="text" value={newPayment.cardholderName} onChange={(e) => setNewPayment({ ...newPayment, cardholderName: e.target.value })} /></label>
                <div className="field-row">
                  <label className="field"><span>Card number</span><input type="text" inputMode="numeric" value={newPayment.maskedNumber} onChange={(e) => setNewPayment({ ...newPayment, maskedNumber: e.target.value })} /></label>
                  <label className="field"><span>CVV</span><input type="text" inputMode="numeric" maxLength="4" value={newPayment.accountToken} onChange={(e) => setNewPayment({ ...newPayment, accountToken: e.target.value })} /></label>
                </div>
                <div className="field-row">
                  <label className="field"><span>Expiry month</span><input type="number" min="1" max="12" value={newPayment.expiryMonth} onChange={(e) => setNewPayment({ ...newPayment, expiryMonth: e.target.value })} /></label>
                  <label className="field"><span>Expiry year</span><input type="number" min="2026" max="2040" value={newPayment.expiryYear} onChange={(e) => setNewPayment({ ...newPayment, expiryYear: e.target.value })} /></label>
                </div>
                <label className="field checkbox-field"><input type="checkbox" checked={newPayment.defaultMethod} onChange={(e) => setNewPayment({ ...newPayment, defaultMethod: e.target.checked })} /><span>Default payment method</span></label>
                <div className="inline-actions">
                  <button className="primary-button" type="button" onClick={savePayment} disabled={saveAccountMutation.isPending}>Save payment</button>
                  <button className="secondary-button" type="button" onClick={() => setPaymentEditorOpen(false)}>Cancel</button>
                </div>
              </div>
            ) : null}
            {paymentEditorOpen && checkoutMessage ? <div className="message-box">{checkoutMessage}</div> : null}
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
          <div className="summary-line"><span>Original price</span><strong id="checkout-order-original">{money(originalSubtotal)}</strong></div>
          <div className="summary-line"><span>Discount</span><strong id="checkout-order-discount">{discountAmount > 0 ? discountLabel(discountAmount) : '$0.00'}</strong></div>
          <div className="summary-line"><span>Shipping</span><strong id="checkout-order-shipping">{shippingCost ? money(shippingCost) : (shippingItems.length ? (isPremium ? 'Free with ShopSmart+' : 'Free over $35') : '$0.00')}</strong></div>
          <div className="summary-line"><span>Tax</span><strong id="checkout-order-tax">$0.00</strong></div>
          <div className="summary-line total"><span>Amount you pay</span><strong id="checkout-order-total">{money(amountDue)}</strong></div>
          <div className="summary-line"><span>Payment</span><strong id="checkout-payment-status">{selectedPayment ? 'Ready' : 'Choose a method'}</strong></div>
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
