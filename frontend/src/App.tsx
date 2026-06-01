import { useEffect, useState } from 'react'
import { fetchAccounts } from './api'
import type { Account } from './types'

function formatBalance(account: Account): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: account.currency,
  }).format(account.balance)
}

export default function App() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchAccounts()
      .then(setAccounts)
      .catch((err: unknown) =>
        setError(err instanceof Error ? err.message : 'Unknown error'),
      )
      .finally(() => setLoading(false))
  }, [])

  return (
    <main className="app">
      <header className="app__header">
        <h1>Account Dashboard</h1>
        <p className="app__subtitle">Proof of concept</p>
      </header>

      {loading && <p>Loading accounts…</p>}
      {error && <p className="app__error">Could not load accounts: {error}</p>}

      {!loading && !error && (
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
      )}
    </main>
  )
}
