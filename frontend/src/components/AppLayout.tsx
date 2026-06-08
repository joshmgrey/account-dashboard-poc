import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function AppLayout() {
  const { user, logout } = useAuth()

  return (
    <main className="app">
      <header className="app__header">
        <div>
          <h1>
            <Link className="app__title-link" to="/">
              Account Dashboard
            </Link>
          </h1>
          <p className="app__subtitle">Signed in as {user?.displayName}</p>
        </div>
        <button className="app__logout" onClick={() => void logout()}>
          Sign out
        </button>
      </header>

      <Outlet />
    </main>
  )
}
