import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { SigninPage } from './pages/SigninPage'
import { SignupPage } from './pages/SignupPage'
import { WishlistPage } from './pages/WishlistPage'
import { ProductPage } from './pages/ProductPage'
import { CartPage } from './pages/CartPage'
import { AccountPage } from './pages/AccountPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrderStatusPage } from './pages/OrderStatusPage'
import { ConfirmationPage } from './pages/ConfirmationPage'
import { OrderFailedPage } from './pages/OrderFailedPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Navigate to="/index.html" replace />} />
        <Route path="/index.html" element={<HomePage />} />
        <Route path="/signin.html" element={<SigninPage />} />
        <Route path="/signup.html" element={<SignupPage />} />
        <Route path="/wishlist.html" element={<WishlistPage />} />
        <Route path="/product/:sku" element={<ProductPage />} />
        <Route path="/product.html" element={<ProductPage />} />
        <Route path="/cart.html" element={<CartPage />} />
        <Route path="/account.html" element={<AccountPage />} />
        <Route path="/checkout.html" element={<CheckoutPage />} />
        <Route path="/order-status.html" element={<OrderStatusPage />} />
        <Route path="/orders/:orderNumber" element={<OrderStatusPage />} />
        <Route path="/confirmation/:orderNumber" element={<ConfirmationPage />} />
        <Route path="/confirmation.html" element={<ConfirmationPage />} />
        <Route path="/order-failed/:orderNumber" element={<OrderFailedPage />} />
        <Route path="/order-failed.html" element={<OrderFailedPage />} />
        <Route path="*" element={<Navigate to="/index.html" replace />} />
      </Route>
    </Routes>
  )
}
