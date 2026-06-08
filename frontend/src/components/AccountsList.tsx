import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, fetchAccounts } from '../api'
import type { Account } from '../types'

function formatBalance(account: Account): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: account.currency,
  }).format(account.balance)
}

export default function AccountsList() {
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

  if (loading) {
    return <p>Loading accounts…</p>
  }
  if (error) {
    return <p className="app__error">{error}</p>
  }

  return (
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
                <Link className="accounts__link" to={`/accounts/${account.id}`}>
                  <span className="accounts__name">{account.name}</span>
                  <span className="accounts__id">{account.id}</span>
                </Link>
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
  )
}
