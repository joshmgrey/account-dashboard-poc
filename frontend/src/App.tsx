import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import AccountDetail from './components/AccountDetail'
import AccountsList from './components/AccountsList'
import AppLayout from './components/AppLayout'
import LoginPage from './components/LoginPage'

export default function App() {
  const { user, initializing } = useAuth()

  if (initializing) {
    return (
      <main className="app">
        <p>Loading…</p>
      </main>
    )
  }

  if (!user) {
    return <LoginPage />
  }

  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<AccountsList />} />
        <Route path="accounts/:id" element={<AccountDetail />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
