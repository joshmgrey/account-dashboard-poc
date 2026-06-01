import { useState, type FormEvent } from 'react'
import { ApiError } from '../api'
import { useAuth } from '../auth/AuthContext'

function messageForStatus(status: number): string {
  switch (status) {
    case 401:
      return 'Invalid username or password.'
    case 429:
      return 'Too many attempts. Please wait a moment and try again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username, password)
    } catch (err: unknown) {
      setError(
        err instanceof ApiError
          ? messageForStatus(err.status)
          : 'Unable to reach the server.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login">
      <form className="login__card" onSubmit={handleSubmit}>
        <h1 className="login__title">Account Dashboard</h1>
        <p className="login__subtitle">Sign in to view your accounts</p>

        <label className="login__field">
          <span>Username</span>
          <input
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>

        <label className="login__field">
          <span>Password</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        {error && <p className="login__error">{error}</p>}

        <button className="login__submit" type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="login__hint">
          Demo: <code>alice</code> / <code>Password123!</code>
        </p>
      </form>
    </main>
  )
}
