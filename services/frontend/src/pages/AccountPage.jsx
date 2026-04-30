import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { compactAddress, dateLabel, money } from '../lib/format'
import { isSessionActive, signinUrl } from '../lib/session'

const emptyAddress = {
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

const emptyPayment = {
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

export function AccountPage() {
  const { session } = useSession()
  const navigate = useNavigate()
  if (!isSessionActive(session)) {
    return <Navigate replace to={signinUrl(`${window.location.pathname}${window.location.search}`)} />
  }
  const queryClient = useQueryClient()
  const [addressEditorOpen, setAddressEditorOpen] = useState(false)
  const [paymentEditorOpen, setPaymentEditorOpen] = useState(false)
  const [editingAddressId, setEditingAddressId] = useState(null)
  const [editingPaymentId, setEditingPaymentId] = useState(null)
  const [addressForm, setAddressForm] = useState(emptyAddress)
  const [paymentForm, setPaymentForm] = useState(emptyPayment)
  const [message, setMessage] = useState('')

  const accountQuery = useQuery({
    queryKey: ['account', session.username],
    queryFn: () => api.getMe(session.token),
    enabled: Boolean(session.token),
    staleTime: 300_000,
  })
  const ordersQuery = useQuery({
    queryKey: ['orders', session.customerId],
    queryFn: () => api.getOrdersByCustomer(session.token, session.customerId),
    enabled: Boolean(session.token && session.customerId),
    staleTime: 30_000,
  })

  const saveAccountMutation = useMutation({
    mutationFn: (payload) => api.updateAccount(session.token, session.customerId, payload),
    onSuccess: async (updatedAccount) => {
      setAddressEditorOpen(false)
      setPaymentEditorOpen(false)
      setEditingAddressId(null)
      setEditingPaymentId(null)
      queryClient.setQueryData(['account', session.username], updatedAccount)
      await queryClient.invalidateQueries({ queryKey: ['account', session.username] })
    },
    onError: (error) => setMessage(error.message),
  })

  const account = accountQuery.data
  const orders = ordersQuery.data || []
  const addresses = useMemo(() => sortAddresses(account?.addresses || []), [account?.addresses])
  const payments = useMemo(() => sortPaymentMethods(account?.paymentMethods || []), [account?.paymentMethods])
  const defaultAddress = addresses.find((address) => address.defaultAddress) || addresses[0]
  const defaultPayment = payments.find((payment) => payment.defaultMethod) || payments[0]

  useEffect(() => {
    const errorMessage = `${accountQuery.error?.message || ''} ${ordersQuery.error?.message || ''}`
    if (errorMessage.includes('401')) {
      setMessage('Your session expired. Please sign in again.')
      navigate(signinUrl(`${window.location.pathname}${window.location.search}`))
    }
  }, [accountQuery.error, ordersQuery.error, navigate])

  function openAddressEditor(address = null) {
    setEditingAddressId(address?.id || null)
    setAddressForm(address ? {
      id: address.id,
      label: address.label || '',
      recipientName: address.recipientName || '',
      addressLine1: address.addressLine1 || '',
      addressLine2: address.addressLine2 || '',
      city: address.city || '',
      state: address.state || '',
      postalCode: address.postalCode || '',
      country: address.country || 'US',
      addressType: address.addressType || 'SHIPPING',
      defaultAddress: Boolean(address.defaultAddress),
    } : emptyAddress)
    setAddressEditorOpen(true)
  }

  function openPaymentEditor(payment = null) {
    setEditingPaymentId(payment?.id || null)
    setPaymentForm(payment ? {
      id: payment.id,
      paymentMethodType: payment.paymentMethodType || 'CREDIT_CARD',
      provider: payment.provider || '',
      accountToken: payment.accountToken || '',
      maskedNumber: payment.maskedNumber || '',
      cardholderName: payment.cardholderName || '',
      expiryMonth: payment.expiryMonth || '',
      expiryYear: payment.expiryYear || '',
      defaultMethod: Boolean(payment.defaultMethod),
      active: payment.active !== false,
    } : emptyPayment)
    setPaymentEditorOpen(true)
  }

  function saveAddress() {
    if (!account) return
    const nextAddresses = addresses
      .filter((address) => address.id !== editingAddressId)
      .map((address) => ({ ...address, defaultAddress: addressForm.defaultAddress ? false : address.defaultAddress }))
    nextAddresses.push({
      ...addressForm,
      id: editingAddressId || addressForm.id,
    })
    saveAccountMutation.mutate(normalizeAccountPayload(account, nextAddresses, payments))
  }

  function deleteAddress(addressId) {
    if (!account) return
    const nextAddresses = addresses.filter((address) => address.id !== addressId)
    saveAccountMutation.mutate(normalizeAccountPayload(account, nextAddresses, payments))
  }

  function savePayment() {
    if (!account) return
    const normalizedPayment = {
      ...paymentForm,
      expiryMonth: paymentForm.expiryMonth ? Number(paymentForm.expiryMonth) : null,
      expiryYear: paymentForm.expiryYear ? Number(paymentForm.expiryYear) : null,
    }
    const nextPayments = payments
      .filter((payment) => payment.id !== editingPaymentId)
      .map((payment) => ({ ...payment, defaultMethod: paymentForm.defaultMethod ? false : payment.defaultMethod }))
    nextPayments.push({
      ...normalizedPayment,
      id: editingPaymentId || paymentForm.id,
    })
    saveAccountMutation.mutate(normalizeAccountPayload(account, addresses, nextPayments))
  }

  function deletePayment(paymentId) {
    if (!account) return
    const nextPayments = payments.filter((payment) => payment.id !== paymentId)
    saveAccountMutation.mutate(normalizeAccountPayload(account, addresses, nextPayments))
  }

  async function changeOrderStatus(order, action) {
    if (action === 'cancel') {
      if (order.status === 'PAID' && order.paymentReference) {
        await api.cancelPayment(session.token, order.paymentReference, {
          idempotencyKey: `cancel-${Date.now()}`,
          amount: order.totalAmount,
          externalReference: order.orderNumber,
        })
      } else {
        await api.cancelOrder(session.token, order.orderNumber, {
          cancelRequestId: `cancel-${Date.now()}`,
          statusReason: 'Cancelled by customer',
        })
      }
    }
    if (action === 'refund') {
      await api.refundPayment(session.token, order.paymentReference, {
        idempotencyKey: `refund-${Date.now()}`,
        amount: order.totalAmount,
        externalReference: order.orderNumber,
      })
    }
    await queryClient.invalidateQueries({ queryKey: ['orders', session.customerId] })
  }

  function updateMembership(nextLevel) {
    if (!account) return
    saveAccountMutation.mutate({
      ...normalizeAccountPayload(account, addresses, payments),
      membershipLevel: nextLevel,
    })
  }

  return (
    <section className="account-dashboard">
      <article className="account-panel panel account-hero-panel">
        <div className="section-head">
          <div>
            <p className="eyebrow">Account</p>
            <h2 id="account-title">Customer account</h2>
            <p className="account-panel-note">Manage purchase history, shipping addresses, and payment methods from one place.</p>
          </div>
          <div className="inline-actions">
            <button className="secondary-button" type="button" onClick={() => {
              queryClient.invalidateQueries({ queryKey: ['account', session.username] })
              queryClient.invalidateQueries({ queryKey: ['orders', session.customerId] })
            }}>Reload</button>
            {account?.membershipLevel === 'PREMIUM' ? (
              <button className="secondary-button" type="button" onClick={() => updateMembership('REGULAR')} disabled={saveAccountMutation.isPending}>
                Cancel membership
              </button>
            ) : (
              <button className="primary-button" type="button" onClick={() => updateMembership('PREMIUM')} disabled={saveAccountMutation.isPending}>
                Join ShopSmart+
              </button>
            )}
          </div>
        </div>
        <div className="membership-banner">
          <div>
            <p className="eyebrow">ShopSmart+</p>
            <strong>{account?.membershipLevel === 'PREMIUM'
              ? 'You are getting free delivery shipping on qualifying orders.'
              : 'Join ShopSmart+ for free delivery shipping on qualifying orders, faster checkout, and easier returns.'}</strong>
          </div>
          <span className="membership-banner-price">{account?.membershipLevel === 'PREMIUM' ? 'Active' : 'Free to join'}</span>
        </div>
        {message ? <div className="message-box" id="account-message">{message}</div> : null}
        {accountQuery.isError ? (
          <div className="message-box" id="account-load-error">Account failed to load: {accountQuery.error?.message || 'Unknown error'}</div>
        ) : null}
        {ordersQuery.isError ? (
          <div className="message-box" id="orders-load-error">Orders failed to load: {ordersQuery.error?.message || 'Unknown error'}</div>
        ) : null}
        <div className="summary-grid account-summary-grid" style={{ marginTop: 18 }}>
          <article className="summary-card"><span className="eyebrow">Username</span><strong id="account-username">{account?.username || '-'}</strong></article>
          <article className="summary-card"><span className="eyebrow">Email</span><strong id="account-email">{account?.email || '-'}</strong></article>
          <article className="summary-card"><span className="eyebrow">Phone</span><strong id="account-phone">{account?.phoneNumber || '-'}</strong></article>
          <article className="summary-card"><span className="eyebrow">Membership</span><strong id="account-membership">{account?.membershipLevel || '-'}</strong></article>
          <article className="summary-card"><span className="eyebrow">Default address</span><strong>{defaultAddress ? compactAddress(defaultAddress) : 'Add an address'}</strong></article>
          <article className="summary-card"><span className="eyebrow">Default payment</span><strong>{defaultPayment?.maskedNumber || defaultPayment?.provider || 'Add a payment method'}</strong></article>
        </div>
      </article>

      <div className="account-workspace">
        <article className="section-panel panel account-orders-panel">
          <div className="section-head">
            <div>
              <p className="eyebrow">Orders</p>
              <h2>Purchase history</h2>
              <p className="section-note">Recent orders with delivery and payment info.</p>
            </div>
          </div>
          <div className="account-list account-order-list" id="account-orders">
            {!orders.length ? <div className="message-box">No orders yet for this account.</div> : null}
            {orders.map((order) => {
              const orderItems = order.items || []
              const itemCount = orderItems.length
              const previewNames = orderItems.map((item) => item.itemName).slice(0, 2).join(', ') + (itemCount > 2 ? ` +${itemCount - 2} more` : '')
              const statusClass = {
                PAID: 'order-status-paid',
                CANCELLED: 'order-status-cancelled',
                REFUNDED: 'order-status-refunded',
                FAILED: 'order-status-failed',
              }[order.status] || 'order-status-pending'
              return (
                <article key={order.orderNumber} className="account-list-card order-history-card">
                  <div className="order-history-toggle">
                    <div className="stack-tight order-history-main">
                      <h3 className="order-item-names">{previewNames || 'Order'}</h3>
                      <div className="order-meta-row">
                        <span className={`order-status-badge ${statusClass}`}>{order.status || 'Processing'}</span>
                        <span className="muted">{dateLabel(order.createdAt)}</span>
                        <span className="muted">{itemCount} item{itemCount === 1 ? '' : 's'}</span>
                      </div>
                      <span className="muted order-number-label">{order.orderNumber}</span>
                    </div>
                    <strong className="order-total-amount order-total-inline">{money(order.totalAmount, order.currencyCode || 'USD')}</strong>
                  </div>
                  <div className="inline-actions order-history-actions">
                    <Link className="secondary-button" to={`/order-status.html?orderNumber=${encodeURIComponent(order.orderNumber)}`}>Order details</Link>
                  </div>
                </article>
              )
            })}
          </div>
        </article>

        <div className="account-side-stack">
          <article className="section-panel panel">
            <div className="section-head">
              <div>
                <p className="eyebrow">Saved addresses</p>
                <h2>Addresses</h2>
                <p className="section-note">Keep delivery and billing locations ready for checkout.</p>
              </div>
              <button className="secondary-button" type="button" onClick={() => openAddressEditor()}>Add address</button>
            </div>
            {addressEditorOpen ? (
              <div className="account-editor panel" id="account-address-editor">
                <div className="section-head">
                  <div>
                    <p className="eyebrow">Address</p>
                    <h2 id="address-editor-title">{editingAddressId ? 'Edit address' : 'Add address'}</h2>
                  </div>
                </div>
                <div className="stack">
                  <label className="field"><span>Label</span><input id="address-label" type="text" value={addressForm.label} onChange={(e) => setAddressForm({ ...addressForm, label: e.target.value })} /></label>
                  <label className="field"><span>Recipient</span><input id="address-recipient" type="text" value={addressForm.recipientName} onChange={(e) => setAddressForm({ ...addressForm, recipientName: e.target.value })} /></label>
                  <label className="field"><span>Address line 1</span><input id="address-line1" type="text" value={addressForm.addressLine1} onChange={(e) => setAddressForm({ ...addressForm, addressLine1: e.target.value })} /></label>
                  <label className="field"><span>Address line 2</span><input id="address-line2" type="text" value={addressForm.addressLine2} onChange={(e) => setAddressForm({ ...addressForm, addressLine2: e.target.value })} /></label>
                  <div className="field-row">
                    <label className="field"><span>City</span><input id="address-city" type="text" value={addressForm.city} onChange={(e) => setAddressForm({ ...addressForm, city: e.target.value })} /></label>
                    <label className="field"><span>State</span><input id="address-state" type="text" value={addressForm.state} onChange={(e) => setAddressForm({ ...addressForm, state: e.target.value })} /></label>
                  </div>
                  <div className="field-row">
                    <label className="field"><span>Postal code</span><input id="address-postal" type="text" value={addressForm.postalCode} onChange={(e) => setAddressForm({ ...addressForm, postalCode: e.target.value })} /></label>
                    <label className="field"><span>Country</span><input id="address-country" type="text" value={addressForm.country} onChange={(e) => setAddressForm({ ...addressForm, country: e.target.value })} /></label>
                  </div>
                  <div className="field-row">
                    <label className="field">
                      <span>Type</span>
                      <select id="address-type" value={addressForm.addressType} onChange={(e) => setAddressForm({ ...addressForm, addressType: e.target.value })}>
                        <option value="SHIPPING">Shipping</option>
                        <option value="BILLING">Billing</option>
                        <option value="HOME">Home</option>
                        <option value="WORK">Work</option>
                      </select>
                    </label>
                    <label className="field checkbox-field"><input id="address-default" type="checkbox" checked={addressForm.defaultAddress} onChange={(e) => setAddressForm({ ...addressForm, defaultAddress: e.target.checked })} /><span>Default address</span></label>
                  </div>
                  <div className="inline-actions">
                    <button className="primary-button" type="button" onClick={saveAddress} disabled={saveAccountMutation.isPending}>Save address</button>
                    <button className="secondary-button" type="button" onClick={() => setAddressEditorOpen(false)}>Cancel</button>
                  </div>
                </div>
              </div>
            ) : null}
            <div className="account-list account-address-list" id="account-addresses">
              {!addresses.length ? <div className="message-box">No saved addresses for this account.</div> : null}
              {addresses.map((address) => (
                <article key={address.id || `${address.label}-${address.addressLine1}`} className="account-list-card">
                  <div className="section-head compact-head">
                    <div>
                      <p className="eyebrow">{address.label || address.addressType || 'Address'}</p>
                      <h3>{address.recipientName || 'Saved address'}</h3>
                    </div>
                    {address.defaultAddress ? <strong>Default</strong> : null}
                  </div>
                  <p>{compactAddress(address)}</p>
                  <div className="inline-actions">
                    <button className="secondary-button" type="button" onClick={() => openAddressEditor(address)}>Edit</button>
                    <button className="secondary-button" type="button" onClick={() => deleteAddress(address.id)}>Delete</button>
                  </div>
                </article>
              ))}
            </div>
          </article>

          <article className="section-panel panel">
            <div className="section-head">
              <div>
                <p className="eyebrow">Wallet</p>
                <h2>Ways to pay</h2>
                <p className="section-note">Store cards and payment choices in a compact wallet view.</p>
              </div>
              <button className="secondary-button" type="button" onClick={() => openPaymentEditor()}>Add payment</button>
            </div>
            {paymentEditorOpen ? (
              <div className="account-editor panel" id="account-payment-editor">
                <div className="section-head">
                  <div>
                    <p className="eyebrow">Payment</p>
                    <h2 id="payment-editor-title">{editingPaymentId ? 'Edit payment method' : 'Add payment method'}</h2>
                  </div>
                </div>
                <div className="stack">
                  <div className="field-row">
                    <label className="field">
                      <span>Method</span>
                      <select id="payment-type" value={paymentForm.paymentMethodType} onChange={(e) => setPaymentForm({ ...paymentForm, paymentMethodType: e.target.value })}>
                        <option value="CREDIT_CARD">Credit card</option>
                        <option value="DEBIT_CARD">Debit card</option>
                        <option value="WALLET">Wallet</option>
                        <option value="GIFT_CARD">Gift card</option>
                      </select>
                    </label>
                    <label className="field"><span>Provider</span><input id="payment-provider" type="text" value={paymentForm.provider} onChange={(e) => setPaymentForm({ ...paymentForm, provider: e.target.value })} /></label>
                  </div>
                  <label className="field"><span>Cardholder</span><input id="payment-cardholder" type="text" value={paymentForm.cardholderName} onChange={(e) => setPaymentForm({ ...paymentForm, cardholderName: e.target.value })} /></label>
                  <div className="field-row">
                    <label className="field"><span>Card number</span><input id="payment-masked" type="text" inputMode="numeric" value={paymentForm.maskedNumber} onChange={(e) => setPaymentForm({ ...paymentForm, maskedNumber: e.target.value })} /></label>
                    <label className="field"><span>CVV</span><input id="payment-token" type="text" inputMode="numeric" maxLength="4" value={paymentForm.accountToken} onChange={(e) => setPaymentForm({ ...paymentForm, accountToken: e.target.value })} /></label>
                  </div>
                  <div className="field-row">
                    <label className="field"><span>Expiry month</span><input id="payment-expiry-month" type="number" min="1" max="12" value={paymentForm.expiryMonth} onChange={(e) => setPaymentForm({ ...paymentForm, expiryMonth: e.target.value })} /></label>
                    <label className="field"><span>Expiry year</span><input id="payment-expiry-year" type="number" min="2026" max="2040" value={paymentForm.expiryYear} onChange={(e) => setPaymentForm({ ...paymentForm, expiryYear: e.target.value })} /></label>
                  </div>
                  <div className="field-row">
                    <label className="field checkbox-field"><input id="payment-default" type="checkbox" checked={paymentForm.defaultMethod} onChange={(e) => setPaymentForm({ ...paymentForm, defaultMethod: e.target.checked })} /><span>Default payment method</span></label>
                  </div>
                  <div className="inline-actions">
                    <button className="primary-button" type="button" onClick={savePayment} disabled={saveAccountMutation.isPending}>Save payment</button>
                    <button className="secondary-button" type="button" onClick={() => setPaymentEditorOpen(false)}>Cancel</button>
                  </div>
                </div>
              </div>
            ) : null}
            <div className="account-list account-payment-list" id="account-payments">
              {!payments.length ? <div className="message-box">No saved payment methods for this account.</div> : null}
              {payments.map((payment) => (
                <article key={payment.id || `${payment.provider}-${payment.maskedNumber}`} className="account-list-card">
                  <div className="section-head compact-head">
                    <div>
                      <p className="eyebrow">{payment.paymentMethodType}</p>
                      <h3>{payment.provider || 'Saved payment method'}</h3>
                    </div>
                    {payment.defaultMethod ? <strong>Default</strong> : null}
                  </div>
                  <p>{payment.maskedNumber || payment.accountToken || 'Saved payment method'}</p>
                  <div className="inline-actions">
                    <button className="secondary-button" type="button" onClick={() => openPaymentEditor(payment)}>Edit</button>
                    <button className="secondary-button" type="button" onClick={() => deletePayment(payment.id)}>Delete</button>
                  </div>
                </article>
              ))}
            </div>
          </article>
        </div>
      </div>
    </section>
  )
}
