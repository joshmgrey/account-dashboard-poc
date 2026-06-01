import { useAuth } from './auth/AuthContext'
import Dashboard from './components/Dashboard'
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

  return user ? <Dashboard /> : <LoginPage />
}
