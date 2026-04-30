import { useState, useEffect, useRef } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'

const SAVED_USERNAMES_KEY = 'shopsmart_saved_usernames'

function loadSavedUsernames() {
  try { return JSON.parse(localStorage.getItem(SAVED_USERNAMES_KEY) || '[]') } catch { return [] }
}

function saveUsername(username) {
  if (!username) return
  const existing = loadSavedUsernames().filter((u) => u !== username)
  localStorage.setItem(SAVED_USERNAMES_KEY, JSON.stringify([username, ...existing].slice(0, 8)))
}

export function SigninPage() {
  const { setSession } = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ accountOrEmail: '', password: '' })
  const [message, setMessage] = useState('Sign in to continue.')
  const [busy, setBusy] = useState(false)
  const [savedUsernames, setSavedUsernames] = useState([])
  const [showSuggestions, setShowSuggestions] = useState(false)
  const suggestRef = useRef(null)

  useEffect(() => {
    setSavedUsernames(loadSavedUsernames())
    function handleClick(e) {
      if (suggestRef.current && !suggestRef.current.contains(e.target)) setShowSuggestions(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const requestedReturnTo = new URLSearchParams(location.search).get('returnTo') || '/index.html'
  const returnTo = requestedReturnTo.startsWith('/') ? requestedReturnTo : '/index.html'

  const suggestions = savedUsernames.filter((u) =>
    form.accountOrEmail ? u.toLowerCase().startsWith(form.accountOrEmail.toLowerCase()) : true
  )

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    try {
      const auth = await api.signin(form)
      const me = await api.getMe(auth.accessToken)
      saveUsername(auth.username || form.accountOrEmail)
      setSavedUsernames(loadSavedUsernames())
      setSession({
        token: auth.accessToken,
        username: auth.username,
        expiresIn: auth.expiresIn,
        expiresAt: Date.now() + Number(auth.expiresIn || 0) * 1000,
        customerId: me.id,
        lastOrderNumber: '',
        lastPaymentNumber: '',
      })
      navigate(returnTo, { replace: true })
    } catch (error) {
      setMessage(error.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="signin-layout">
      <aside className="account-panel panel signin-card">
        <div className="section-head auth-card-head">
          <div>
            <p className="eyebrow">Account</p>
            <h2>Sign in</h2>
            <p className="auth-card-copy">Use your email or username to access your cart, orders, and saved details.</p>
          </div>
        </div>
        <form className="stack auth-form" id="signin-form" autoComplete="off" onSubmit={handleSubmit}>
          <div className="field username-field-wrapper" ref={suggestRef}>
            <label htmlFor="signin-account">Email or username</label>
            <input
              id="signin-account"
              type="text"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              placeholder="Enter email or username"
              value={form.accountOrEmail}
              onFocus={() => setShowSuggestions(true)}
              onChange={(event) => {
                setForm({ ...form, accountOrEmail: event.target.value })
                setShowSuggestions(true)
              }}
            />
            {showSuggestions && suggestions.length > 0 ? (
              <ul className="username-suggestions" role="listbox">
                {suggestions.map((u) => (
                  <li key={u} role="option" className="username-suggestion-item"
                    onMouseDown={() => {
                      setForm({ ...form, accountOrEmail: u })
                      setShowSuggestions(false)
                    }}
                  >
                    <span className="username-suggestion-icon">👤</span>
                    {u}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
          <label className="field">
            <span>Password</span>
            <input id="signin-password" type="password" autoComplete="new-password" autoCapitalize="none" autoCorrect="off" placeholder="Enter password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
          </label>
          <div className="stack-actions">
            <button className="primary-button" type="submit" disabled={busy}>Sign in</button>
          </div>
        </form>
        <div className="message-box auth-message" id="signin-message">{message}</div>
        <div className="signin-create">
          <span>New customer?</span>
          <Link className="secondary-button" to="/signup.html">Create account</Link>
        </div>
      </aside>
    </section>
  )
}
