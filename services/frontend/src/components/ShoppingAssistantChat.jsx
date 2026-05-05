import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { dateLabel, money } from '../lib/format'
import { isSessionActive } from '../lib/session'

const quickPrompts = [
  'Show me all products',
  'Find toys for me',
  'Add a coffee maker to my cart',
  'Show my recent orders',
]

const HISTORY_KEY = 'shopping.assistant.history'
const MAX_HISTORY = 15

const DEFAULT_MESSAGES = [
  {
    role: 'assistant',
    text: 'I can browse the full catalog, look up products, add items to your cart, place an order with your saved checkout details, and show your recent orders.',
    actions: [{ label: 'Browse all products', href: '/index.html' }],
  },
]

// Module-level cache — survives React component remounts within the same page load.
// On a fresh page load it is null, so we fall back to sessionStorage (which survives
// full-page navigations within the same browser tab).
let _cache = null

function readMessages() {
  if (_cache) return _cache
  try {
    const saved = JSON.parse(sessionStorage.getItem(HISTORY_KEY))
    if (Array.isArray(saved) && saved.length) {
      _cache = saved
      return saved
    }
  } catch {}
  _cache = DEFAULT_MESSAGES
  return DEFAULT_MESSAGES
}

function writeMessages(msgs) {
  _cache = msgs
  try { sessionStorage.setItem(HISTORY_KEY, JSON.stringify(msgs)) } catch {}
}

function ChatItemCard({ item, message, onAdd, onChoose }) {
  const chooseMode = message?.state === 'clarification' || message?.state === 'choose_product'
  const buttonLabel = chooseMode
    ? message?.intent === 'PLACE_ORDER'
      ? 'Buy this'
      : 'Choose this'
    : 'Add to cart'
  return (
    <article className="assistant-item-card">
      <div className="assistant-item-image">
        {item.imageUrl ? <img src={item.imageUrl} alt={item.itemName} /> : <div className="assistant-item-fallback">No image</div>}
      </div>
      <div className="assistant-item-copy">
        <strong>{item.itemName}</strong>
        <span className="muted">{item.brand || item.category || item.sku}</span>
        <div className="assistant-item-meta">
          <span>{money(item.unitPrice, item.currencyCode || 'USD')}</span>
          {item.discountPercent ? <span className="assistant-chip sale-chip">-{item.discountPercent}%</span> : null}
        </div>
        <div className="assistant-item-actions">
          <Link className="secondary-button" to={item.productUrl || `/product.html?sku=${encodeURIComponent(item.sku)}`}>View</Link>
          <button className="primary-button" type="button" onClick={() => (chooseMode ? onChoose(item, message) : onAdd(item))}>{buttonLabel}</button>
        </div>
      </div>
    </article>
  )
}

function ChatOrderCard({ order }) {
  return (
    <article className="assistant-item-card assistant-order-card">
      <div className="assistant-item-copy">
        <strong>{order.orderNumber}</strong>
        <span className="muted">{dateLabel(order.createdAt)}</span>
        <div className="assistant-item-meta">
          <span>{order.status}</span>
          <span>{money(order.totalAmount, order.currencyCode || 'USD')}</span>
        </div>
        {order.statusReason ? <span className="muted">{order.statusReason}</span> : null}
        <div className="assistant-item-actions">
          <Link className="primary-button" to={order.orderUrl || `/order-status.html?orderNumber=${encodeURIComponent(order.orderNumber)}`}>Open order</Link>
        </div>
      </div>
    </article>
  )
}

export function ShoppingAssistantChat() {
  const { session } = useSession()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState(readMessages)
  const [isSending, setIsSending] = useState(false)
  const [status, setStatus] = useState('')
  const [pendingSelection, setPendingSelection] = useState(null)
  const listRef = useRef(null)

  const signedIn = isSessionActive(session)

  useEffect(() => {
    if (!open || !listRef.current) return
    listRef.current.scrollTop = listRef.current.scrollHeight
  }, [messages, open])

  function update(msgs) {
    writeMessages(msgs)
    setMessages(msgs)
  }

  const title = useMemo(() => (open ? 'ShopSmart Assistant' : 'Ask ShopSmart'), [open])

  async function sendMessage(message) {
    const text = String(message || '').trim()
    if (!text || isSending) return
    setOpen(true)
    setStatus('')
    setDraft('')
    setPendingSelection(null)
    const withUser = [..._cache || messages, { role: 'user', text }].slice(-MAX_HISTORY)
    update(withUser)
    setIsSending(true)
    try {
      const response = await api.chatAssistant(signedIn ? session.token : '', { message: text })
      const withReply = [...withUser, {
        role: 'assistant',
        text: response.reply || 'I found something useful.',
        items: response.items || [],
        orders: response.orders || [],
        actions: response.actions || [],
        requiresSignIn: Boolean(response.requiresSignIn),
        intent: response.intent || '',
        state: response.state || '',
        resolvedQuery: response.resolvedQuery || '',
        orderNumber: response.orderNumber || '',
        orderStatus: response.orderStatus || '',
        cartItemCount: response.cartItemCount,
        checkoutUrl: response.checkoutUrl || '',
      }].slice(-MAX_HISTORY)
      update(withReply)
      if (response.requiresSignIn) {
        setStatus('Sign in to add items to your cart or place an order.')
      }
    } catch (error) {
      update([...withUser, { role: 'assistant', text: error.message || 'The assistant could not respond right now.' }].slice(-MAX_HISTORY))
    } finally {
      setIsSending(false)
    }
  }

  async function addItem(item) {
    if (!signedIn) {
      setStatus('Sign in to add items to your cart.')
      return
    }
    try {
      await api.addCartItem(session.token, session.customerId, {
        itemId: `${item.sku}::SHIPPING`,
        sku: item.sku,
        quantity: 1,
      })
      update([..._cache || messages, { role: 'assistant', text: `${item.itemName} has been added to your cart.` }].slice(-MAX_HISTORY))
    } catch (error) {
      update([..._cache || messages, { role: 'assistant', text: error.message || 'Could not add the item to your cart.' }].slice(-MAX_HISTORY))
    }
  }

  function chooseItem(item, message) {
    if (message?.intent === 'PLACE_ORDER') {
      setPendingSelection(null)
      sendSelectionAction('PLACE_ORDER', item)
      return
    }
    if (message?.intent === 'SEARCH_PRODUCTS' || message?.state === 'choose_product') {
      setPendingSelection({ item, mode: 'search_choice' })
      return
    }
    setPendingSelection({ item, mode: 'search_choice' })
  }

  function addSelectedToCart() {
    if (!pendingSelection?.item) return
    addItem(pendingSelection.item)
    setPendingSelection(null)
  }

  function placeSelectedOrder() {
    if (!pendingSelection?.item) return
    const item = pendingSelection.item
    setPendingSelection(null)
    sendSelectionAction('PLACE_ORDER', item)
  }

  async function sendSelectionAction(selectedAction, item) {
    if (!signedIn) {
      setStatus('Sign in to place an order.')
      return
    }
    setOpen(true)
    setStatus('')
    setDraft('')
    setIsSending(true)
    try {
      const response = await api.chatAssistant(session.token, {
        message: '',
        selectedAction,
        selectedSku: item.sku,
        selectedItemName: item.itemName,
      })
      update([..._cache || messages, {
        role: 'assistant',
        text: response.reply || 'I found something useful.',
        items: response.items || [],
        orders: response.orders || [],
        actions: response.actions || [],
        requiresSignIn: Boolean(response.requiresSignIn),
        intent: response.intent || '',
        state: response.state || '',
        resolvedQuery: response.resolvedQuery || '',
        orderNumber: response.orderNumber || '',
        orderStatus: response.orderStatus || '',
        cartItemCount: response.cartItemCount,
        checkoutUrl: response.checkoutUrl || '',
      }].slice(-MAX_HISTORY))
    } catch (error) {
      update([..._cache || messages, { role: 'assistant', text: error.message || 'The assistant could not respond right now.' }].slice(-MAX_HISTORY))
    } finally {
      setIsSending(false)
    }
  }

  function handleSubmit(event) {
    event.preventDefault()
    sendMessage(draft)
  }

  return (
    <section className={`assistant-chat-shell${open ? ' is-open' : ''}`}>
      <button className="assistant-chat-toggle" type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        <span className="assistant-chat-toggle-icon">AI</span>
        <span>
          <strong>{title}</strong>
          <small>{open ? 'Search products or check orders' : 'Ask for products or order help'}</small>
        </span>
      </button>

      {open ? (
        <article className="assistant-chat-panel panel">
          <div className="assistant-chat-head">
            <div>
              <p className="eyebrow">Shopping assistant</p>
              <h3>Tell me what you want to shop for</h3>
            </div>
            <button className="secondary-button assistant-close" type="button" onClick={() => setOpen(false)}>Close</button>
          </div>

          <div className="assistant-quick-prompts">
            {quickPrompts.map((prompt) => (
              <button key={prompt} className="assistant-chip" type="button" onClick={() => sendMessage(prompt)}>
                {prompt}
              </button>
            ))}
          </div>

          <div className="assistant-message-stream" ref={listRef}>
            {messages.map((message, index) => (
              <div key={`${message.role}-${index}`} className={`assistant-message ${message.role === 'user' ? 'from-user' : 'from-assistant'}`}>
                <div className="assistant-bubble">
                  <p>{message.text}</p>
                  {message.requiresSignIn ? <p className="assistant-note">Sign in to finish actions like add to cart and place order.</p> : null}
                  {typeof message.cartItemCount === 'number' ? <p className="assistant-note">Cart items: {message.cartItemCount}</p> : null}
                  {message.orderNumber ? (
                    <Link className="primary-button assistant-inline-action" to={message.checkoutUrl || `/order-status.html?orderNumber=${encodeURIComponent(message.orderNumber)}`}>
                      {message.orderStatus ? `View ${message.orderStatus.toLowerCase()} order ${message.orderNumber}` : `View order ${message.orderNumber}`}
                    </Link>
                  ) : null}
                  {message.actions?.length ? (
                    <div className="assistant-message-actions">
                      {message.actions.map((action) => (
                        <Link key={`${action.label}-${action.href}`} className="secondary-button" to={action.href}>
                          {action.label}
                        </Link>
                      ))}
                    </div>
                  ) : null}
                </div>
                {message.items?.length ? (
                  <div className="assistant-results-grid">
                    {message.items.map((item) => (
                      <ChatItemCard key={item.sku} item={item} message={message} onAdd={addItem} onChoose={chooseItem} />
                    ))}
                  </div>
                ) : null}
                {message.orders?.length ? (
                  <div className="assistant-results-grid">
                    {message.orders.map((order) => (
                      <ChatOrderCard key={order.orderNumber} order={order} />
                    ))}
                  </div>
                ) : null}
              </div>
            ))}
          </div>

          {pendingSelection?.item ? (
            <div className="assistant-selection-panel assistant-selection-panel-bottom">
              <div className="assistant-selection-copy">
                <p className="eyebrow">Selected product</p>
                <strong>{pendingSelection.item.itemName}</strong>
                <span className="muted">{pendingSelection.item.brand || pendingSelection.item.category || pendingSelection.item.sku}</span>
              </div>
              <div className="assistant-quick-prompts assistant-choice-row">
                <button className="assistant-chip" type="button" onClick={addSelectedToCart}>
                  Add to cart
                </button>
                <button className="assistant-chip" type="button" onClick={placeSelectedOrder}>
                  Place order
                </button>
              </div>
            </div>
          ) : null}

          <form className="assistant-chat-form" onSubmit={handleSubmit}>
            <input
              type="text"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={signedIn ? 'Ask about products, orders, or cart updates' : 'Ask a product question or sign in for cart and order help'}
            />
            <button className="primary-button" type="submit" disabled={isSending}>
              {isSending ? 'Sending…' : 'Send'}
            </button>
          </form>
          {status ? <div className="assistant-status">{status}</div> : null}
        </article>
      ) : null}
    </section>
  )
}