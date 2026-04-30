import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useSession } from '../app/SessionProvider'
import { api } from '../lib/api'
import { normalizeCategories } from '../lib/categories'
import { signinUrl } from '../lib/session'

const pageMeta = {
  '/index.html': {
    brandTitle: 'ShopSmart',
    brandSubtitle: 'Save money. Live better.',
    eyebrow: 'Pickup or delivery',
    title: 'Chicago, IL 60601',
    detail: 'Store hours 6AM-11PM',
    placeholder: 'Search everything at ShopSmart',
  },
  '/account.html': {
    brandTitle: 'ShopSmart Market',
    brandSubtitle: 'Account hub',
    eyebrow: 'Account',
    title: 'Addresses and payment methods',
    detail: 'Manage billing and membership',
    placeholder: 'Search everything at ShopSmart',
  },
  '/cart.html': {
    brandTitle: 'ShopSmart Market',
    brandSubtitle: 'Your cart',
    eyebrow: 'Cart',
    title: 'Your saved items',
    detail: 'Ready for checkout',
    placeholder: 'Search more items',
  },
  '/checkout.html': {
    brandTitle: 'ShopSmart Market',
    brandSubtitle: 'Checkout',
    eyebrow: 'Delivery',
    title: 'Address and payment',
    detail: 'Review your order',
    placeholder: 'Search before checkout',
  },
  '/order-status.html': {
    brandTitle: 'ShopSmart Market',
    brandSubtitle: 'Order details',
    eyebrow: 'Orders',
    title: 'Order details',
    detail: 'Check payment and fulfillment updates',
    placeholder: 'Search for another item',
  },
  '/confirmation.html': {
    brandTitle: 'ShopSmart Market',
    brandSubtitle: 'Order placed',
    eyebrow: 'Confirmation',
    title: 'Thanks for your order',
    detail: 'We are getting it ready',
    placeholder: 'Search for your next purchase',
  },
  '/order-failed.html': {
    brandTitle: 'ShopSmart Market',
    brandSubtitle: 'Order update',
    eyebrow: 'Order status',
    title: 'We could not complete your order',
    detail: 'Review what happened',
    placeholder: 'Search for another item',
  },
  '/wishlist.html': {
    brandTitle: 'ShopSmart',
    brandSubtitle: 'Lists',
    eyebrow: 'Saved items',
    title: 'Your favorites in one place',
    detail: 'Review and shop later',
    placeholder: 'Search products',
  },
  '/signin.html': {
    brandTitle: 'ShopSmart',
    brandSubtitle: 'Account',
    eyebrow: 'Store',
    title: 'Chicago, IL 60601',
    detail: 'Pickup available today',
    placeholder: 'Search and jump back to home',
  },
    '/signup.html': {
      brandTitle: 'ShopSmart',
      brandSubtitle: 'Create account',
      eyebrow: 'Welcome',
      title: 'Create account',
      detail: 'Shop faster.',
      placeholder: 'Search products',
    },
}

function categoryHref(category) {
  return `/index.html?category=${encodeURIComponent(category)}`
}

export function Layout() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { session, signOut } = useSession()
  const [accountMenuOpen, setAccountMenuOpen] = useState(false)
  const [departmentMenuOpen, setDepartmentMenuOpen] = useState(false)
  const departmentMenuRef = useRef(null)
  const meta = useMemo(() => {
    if (pathname.startsWith('/product')) {
      return {
        brandTitle: 'ShopSmart Market',
        brandSubtitle: 'Product details',
        eyebrow: 'Fulfillment',
        title: 'Shipping or pickup',
        detail: 'Choose how you get it',
        placeholder: 'Search another product',
      }
    }
    return pageMeta[pathname] || pageMeta['/index.html']
  }, [pathname])

  const cartQuery = useQuery({
    queryKey: ['cart', session.customerId],
    queryFn: () => api.getCart(session.token, session.customerId),
    enabled: Boolean(session.token && session.customerId),
    staleTime: 0,
  })
  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.getCategories(),
    staleTime: 300_000,
  })
  const categories = useMemo(() => normalizeCategories(categoriesQuery.data || []), [categoriesQuery.data])
  const dropdownCategories = categories.length ? categories : ['Grocery & Essentials', 'Electronics', 'Home & Garden', 'Clothing & Shoes', 'Toys & Games']
  const cartUnits = (cartQuery.data?.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0)

  useEffect(() => {
    setAccountMenuOpen(false)
    setDepartmentMenuOpen(false)
  }, [pathname])

  useEffect(() => {
    function handleDocumentClick(event) {
      if (departmentMenuRef.current && !departmentMenuRef.current.contains(event.target)) {
        setDepartmentMenuOpen(false)
      }
    }
    function handleEscape(event) {
      if (event.key === 'Escape') {
        setDepartmentMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleDocumentClick)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleDocumentClick)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [])

  function handleSearch(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const q = String(form.get('q') || '').trim()
    navigate(q ? `/index.html?q=${encodeURIComponent(q)}` : '/index.html')
  }

  return (
    <div className={`page-shell${pathname === '/signup.html' ? ' signup-shell' : ''}`}>
      <header className="site-header">
        <div className="header-main">
          <NavLink className="brand-chip" to="/index.html">
            <span className="brand-burst">*</span>
            <span className="brand-copy">
              <strong>{meta.brandTitle}</strong>
              <span>{meta.brandSubtitle}</span>
            </span>
          </NavLink>
          <div className={`location-chip${pathname === '/signup.html' ? ' auth-chip' : ''}`}>
            <span className="eyebrow">{meta.eyebrow}</span>
            <strong>{meta.title}</strong>
            <span>{meta.detail}</span>
          </div>
          <form className="search-shell" id="global-search-form" onSubmit={handleSearch}>
            <label className="sr-only" htmlFor="global-search">Search catalog</label>
            <input id="global-search" name="q" type="search" placeholder={meta.placeholder} />
            <button aria-label="Search" className="primary-button search-button" type="submit">
              <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
                <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2" />
                <path d="M16.2 16.2 21 21" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
              </svg>
            </button>
          </form>
          <div className="header-actions">
            {session.token ? (
              <div className="account-menu-wrapper">
                <button
                  aria-expanded={accountMenuOpen}
                  aria-haspopup="menu"
                  className="account-action account-menu-toggle"
                  type="button"
                  onClick={() => setAccountMenuOpen((value) => !value)}
                >
                  <span className="cart-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" width="20" height="20" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
                  </span>
                  <span className="cart-copy">
                    <strong>Account</strong>
                    <span>{session.username || 'My account'}</span>
                  </span>
                </button>
                <div className="account-menu" hidden={!accountMenuOpen} role="menu">
                  <NavLink role="menuitem" to="/account.html">Account</NavLink>
                  <button
                    role="menuitem"
                    type="button"
                    onClick={() => {
                      signOut()
                      navigate('/signin.html')
                    }}
                  >
                    Log out
                  </button>
                </div>
              </div>
            ) : (
              <NavLink className="account-action" to={signinUrl(`${window.location.pathname}${window.location.search}`)}>
                <span className="cart-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" width="20" height="20" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
                </span>
                <span className="cart-copy">
                  <strong>Sign in</strong>
                  <span>Account</span>
                </span>
              </NavLink>
            )}
            <NavLink className="account-action" to="/wishlist.html">
              <span className="cart-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" width="20" height="20" aria-hidden="true"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
              </span>
              <span className="cart-copy">
                <strong>Lists</strong>
                <span>Saved items</span>
              </span>
            </NavLink>
            <NavLink className="account-action cart-action" to="/cart.html">
              <span className="cart-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" width="20" height="20" aria-hidden="true"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/></svg>
              </span>
              <span className="cart-copy">
                <strong>Cart</strong>
                <span id="header-cart-pill">{cartUnits} item{cartUnits === 1 ? '' : 's'}</span>
              </span>
            </NavLink>
          </div>
        </div>
        <nav className="category-jump-bar" id="home-category-nav" aria-label="Departments and popular categories">
          <div className="account-menu-wrapper department-menu-wrapper" ref={departmentMenuRef}>
            <button
              aria-expanded={departmentMenuOpen}
              aria-haspopup="menu"
              className="category-jump-link department-toggle"
              type="button"
              onClick={() => setDepartmentMenuOpen((value) => !value)}
            >
              <svg aria-hidden="true" width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ marginRight: 6 }}>
                <rect y="2" width="14" height="1.8" rx="1" fill="currentColor"/>
                <rect y="6.1" width="14" height="1.8" rx="1" fill="currentColor"/>
                <rect y="10.2" width="14" height="1.8" rx="1" fill="currentColor"/>
              </svg>
              Departments
              <svg aria-hidden="true" width="12" height="12" viewBox="0 0 12 12" fill="none" style={{ marginLeft: 6 }}>
                <path d="M2 4l4 4 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
            <div className="department-dropdown" hidden={!departmentMenuOpen} role="menu">
              <a href="/index.html" role="menuitem" className="department-dropdown-all">All departments</a>
              {dropdownCategories.map((category) => (
                <a key={category} href={categoryHref(category)} role="menuitem">{category}</a>
              ))}
            </div>
          </div>
          {categories.slice(0, 8).map((category) => (
            <a key={category} className="category-jump-link" href={categoryHref(category)}>{category}</a>
          ))}
        </nav>
      </header>
      <main className="page-main">
        <Outlet />
      </main>
    </div>
  )
}
