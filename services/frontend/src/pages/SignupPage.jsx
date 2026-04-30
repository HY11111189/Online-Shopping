import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'

const initial = {
  name: '',
  account: '',
  email: '',
  phoneNumber: '',
  password: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  country: '',
}

export function SignupPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(initial)
  const [message, setMessage] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()
    try {
      await api.signup(form)
      navigate('/signin.html')
    } catch (error) {
      setMessage(error.message)
    }
  }

  return (
    <section className="signup-layout signup-page">
      <article className="account-panel panel signup-card">
        <div className="section-head auth-card-head">
          <div>
            <p className="eyebrow">Create account</p>
            <h2>Set up your account</h2>
            <p className="auth-card-copy">Create an account to start shopping, save your address, and track orders.</p>
          </div>
        </div>
        <form className="stack" id="signup-form" autoComplete="off" onSubmit={handleSubmit}>
          <div className="field-row">
            <label className="field"><span>Full name</span><input id="signup-name" type="text" autoComplete="off" autoCapitalize="words" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
            <label className="field"><span>Username</span><input id="signup-account" type="text" autoComplete="off" autoCapitalize="none" autoCorrect="off" value={form.account} onChange={(e) => setForm({ ...form, account: e.target.value })} /></label>
          </div>
          <div className="field-row">
            <label className="field"><span>Email</span><input id="signup-email" type="email" autoComplete="off" autoCapitalize="none" autoCorrect="off" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
            <label className="field"><span>Phone number</span><input id="signup-phone" type="tel" autoComplete="off" value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} /></label>
          </div>
          <label className="field"><span>Password</span><input id="signup-password" type="password" autoComplete="new-password" autoCapitalize="none" autoCorrect="off" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
          <label className="field"><span>Address line 1</span><input id="signup-line1" type="text" autoComplete="off" value={form.addressLine1} onChange={(e) => setForm({ ...form, addressLine1: e.target.value })} /></label>
          <label className="field"><span>Address line 2</span><input id="signup-line2" type="text" autoComplete="off" value={form.addressLine2} onChange={(e) => setForm({ ...form, addressLine2: e.target.value })} /></label>
          <div className="field-row">
            <label className="field"><span>City</span><input id="signup-city" type="text" autoComplete="off" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></label>
            <label className="field"><span>State</span><input id="signup-state" type="text" autoComplete="off" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} /></label>
          </div>
          <div className="field-row">
            <label className="field"><span>Postal code</span><input id="signup-postal" type="text" autoComplete="off" value={form.postalCode} onChange={(e) => setForm({ ...form, postalCode: e.target.value })} /></label>
            <label className="field"><span>Country</span><input id="signup-country" type="text" autoComplete="off" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} /></label>
          </div>
          <div className="stack-actions">
            <button className="primary-button" type="submit">Create account</button>
            <Link className="secondary-button" to="/signin.html">Sign in</Link>
          </div>
        </form>
        {message ? <div className="message-box auth-message" id="signup-message">{message}</div> : null}
      </article>
    </section>
  )
}
