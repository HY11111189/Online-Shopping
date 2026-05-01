import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { money } from '../lib/format'
import { isSessionActive } from '../lib/session'

const quickPrompts = [
  'Show me trending electronics',
  'Find deals on home essentials',
  'Add a coffee maker to my cart',
  'Place my order',
]

function ChatItemCard({ item, onAdd }) {
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
          <button className="primary-button" type="button" onClick={() => onAdd(item)}>Add</button>
        </div>
      </div>
    </article>
  )
}

export function ShoppingAssistantChat() {
  const { session } = useSession()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      text: 'I can search products, add items to your cart, and place an order when you are signed in.',
      actions: [{ label: 'Find electronics', href: '/index.html?category=Electronics' }],
    },
  ])
  const [isSending, setIsSending] = useState(false)
  const [status, setStatus] = useState('')
  const listRef = useRef(null)

  const signedIn = isSessionActive(session)

  useEffect(() => {
    if (!open || !listRef.current) return
    listRef.current.scrollTop = listRef.current.scrollHeight
  }, [messages, open])

  const title = useMemo(() => (open ? 'ShopSmart Assistant' : 'Ask ShopSmart'), [open])

  async function sendMessage(message) {
    const text = String(message || '').trim()
    if (!text || isSending) return
    setOpen(true)
    setStatus('')
    setDraft('')
    setMessages((current) => [...current, { role: 'user', text }])
    setIsSending(true)
    try {
      const response = await api.chatAssistant(signedIn ? session.token : '', { message: text })
      setMessages((current) => [
        ...current,
        {
          role: 'assistant',
          text: response.reply || 'I found something useful.',
          items: response.items || [],
          actions: response.actions || [],
          requiresSignIn: Boolean(response.requiresSignIn),
          orderNumber: response.orderNumber || '',
          checkoutUrl: response.checkoutUrl || '',
        },
      ])
      if (response.requiresSignIn) {
        setStatus('Sign in to add items to your cart or place an order.')
      }
    } catch (error) {
      setMessages((current) => [...current, { role: 'assistant', text: error.message || 'The assistant could not respond right now.' }])
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
          <small>{open ? 'Search products or place orders' : 'Ask for products or checkout help'}</small>
        </span>
      </button>

      {open ? (
        <article className="assistant-chat-panel panel">
          <div className="assistant-chat-head">
            <div>
              <p className="eyebrow">Shopping assistant</p>
              <h3>Tell me what you need</h3>
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
                  {message.orderNumber ? (
                    <Link className="primary-button assistant-inline-action" to={message.checkoutUrl || `/order-status.html?orderNumber=${encodeURIComponent(message.orderNumber)}`}>
                      View order {message.orderNumber}
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
                      <ChatItemCard key={item.sku} item={item} onAdd={addItem} />
                    ))}
                  </div>
                ) : null}
              </div>
            ))}
          </div>

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
