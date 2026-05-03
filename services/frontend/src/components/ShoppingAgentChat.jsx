import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { dateLabel, money } from '../lib/format'
import { isSessionActive } from '../lib/session'

const quickPrompts = [
  'Find me some fruits',
  'Buy me some medicine',
  'Show me orders I placed this week',
  'Add a coffee maker to my cart',
]

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

export function ShoppingAgentChat() {
  const { session } = useSession()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      text: 'I can plan shopping tasks, remember recent questions, search the catalog, place orders, and look up orders with fewer repeated API calls.',
      actions: [{ label: 'Browse all products', href: '/index.html' }],
    },
  ])
  const [isSending, setIsSending] = useState(false)
  const [status, setStatus] = useState('')
  const [pendingSelection, setPendingSelection] = useState(null)
  const listRef = useRef(null)

  const signedIn = isSessionActive(session)

  useEffect(() => {
    if (!open || !listRef.current) return
    listRef.current.scrollTop = listRef.current.scrollHeight
  }, [messages, open])

  const title = useMemo(() => (open ? 'ShopSmart Agent' : 'Ask ShopSmart Agent'), [open])

  async function sendMessage(message) {
    const text = String(message || '').trim()
    if (!text || isSending) return
    setOpen(true)
    setStatus('')
    setDraft('')
    setPendingSelection(null)
    setMessages((current) => [...current, { role: 'user', text }])
    setIsSending(true)
    try {
      const response = await api.chatAgentAssistant(signedIn ? session.token : '', { message: text })
      setMessages((current) => [
        ...current,
        {
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
        },
      ])
      if (response.requiresSignIn) {
        setStatus('Sign in to add items to your cart or place an order.')
      }
    } catch (error) {
      setMessages((current) => [...current, { role: 'assistant', text: error.message || 'The agent could not respond right now.' }])
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
      setStatus(`${item.itemName} added to your cart.`)
    } catch (error) {
      setStatus(error.message || 'Could not add the item to your cart.')
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
      const response = await api.chatAgentAssistant(session.token, {
        message: '',
        selectedAction,
        selectedSku: item.sku,
        selectedItemName: item.itemName,
      })
      setMessages((current) => [
        ...current,
        {
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
        },
      ])
    } catch (error) {
      setMessages((current) => [...current, { role: 'assistant', text: error.message || 'The agent could not respond right now.' }])
    } finally {
      setIsSending(false)
    }
  }

  function handleSubmit(event) {
    event.preventDefault()
    sendMessage(draft)
  }

  return (
    <section className={`assistant-chat-shell assistant-chat-shell-agent${open ? ' is-open' : ''}`}>
      <button className="assistant-chat-toggle" type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        <span className="assistant-chat-toggle-icon">AG</span>
        <span>
          <strong>{title}</strong>
          <small>{open ? 'Agentic shopping and order planning' : 'Ask for shopping planning help'}</small>
        </span>
      </button>

      {open ? (
        <article className="assistant-chat-panel panel">
          <div className="assistant-chat-head">
            <div>
              <p className="eyebrow">Shopping agent</p>
              <h3>Tell me what you want to do</h3>
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
              placeholder={signedIn ? 'Ask the agent to plan a purchase or look up orders' : 'Ask for product help or sign in for cart and order planning'}
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
