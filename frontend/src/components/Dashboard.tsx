import { useEffect, useState } from 'react'
import { ApiError, fetchAccounts } from '../api'
import { useAuth } from '../auth/AuthContext'
import type { Account } from '../types'

function formatBalance(account: Account): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: account.currency,
  }).format(account.balance)
}

export default function Dashboard() {
  const { user, logout } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchAccounts()
      .then(setAccounts)
      .catch((err: unknown) =>
        setError(
          err instanceof ApiError && err.status === 429
            ? 'You are making requests too quickly. Please wait a moment.'
            : 'Could not load your accounts.',
        ),
      )
      .finally(() => setLoading(false))
  }, [])

  const total = accounts.reduce((sum, account) => sum + account.balance, 0)

  return (
    <main className="app">
      <header className="app__header">
        <div>
          <h1>Account Dashboard</h1>
          <p className="app__subtitle">Signed in as {user?.displayName}</p>
        </div>
        <button className="app__logout" onClick={() => void logout()}>
          Sign out
        </button>
      </header>

      {loading && <p>Loading accounts…</p>}
      {error && <p className="app__error">{error}</p>}

      {!loading && !error && (
        <>
          <table className="accounts">
            <thead>
              <tr>
                <th>Account</th>
                <th>Type</th>
                <th>Status</th>
                <th className="accounts__amount">Balance</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>
                    <span className="accounts__name">{account.name}</span>
                    <span className="accounts__id">{account.id}</span>
                  </td>
                  <td>{account.type}</td>
                  <td>
                    <span className={`badge badge--${account.status.toLowerCase()}`}>
                      {account.status}
                    </span>
                  </td>
                  <td className="accounts__amount">{formatBalance(account)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <p className="accounts__total">
            Total (USD-equivalent, unconverted):{' '}
            <strong>
              {new Intl.NumberFormat('en-US', {
                style: 'currency',
                currency: 'USD',
              }).format(total)}
            </strong>
          </p>
        </>
      )}
    </main>
  )
}
