import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, fetchAccount } from '../api'
import type { Account } from '../types'

function formatBalance(account: Account): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: account.currency,
  }).format(account.balance)
}

export default function AccountDetail() {
  const { id } = useParams<{ id: string }>()
  const [account, setAccount] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) {
      return
    }
    setLoading(true)
    setError(null)
    fetchAccount(id)
      .then(setAccount)
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 404) {
          setError('That account was not found.')
        } else if (err instanceof ApiError && err.status === 429) {
          setError('You are making requests too quickly. Please wait a moment.')
        } else {
          setError('Could not load this account.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  return (
    <section>
      <p>
        <Link className="back-link" to="/">
          ← Back to accounts
        </Link>
      </p>

      {loading && <p>Loading account…</p>}
      {error && <p className="app__error">{error}</p>}

      {!loading && !error && account && (
        <div className="detail">
          <div className="detail__heading">
            <h2 className="detail__name">{account.name}</h2>
            <span className={`badge badge--${account.status.toLowerCase()}`}>
              {account.status}
            </span>
          </div>

          <dl className="detail__grid">
            <div className="detail__row">
              <dt>Account ID</dt>
              <dd>{account.id}</dd>
            </div>
            <div className="detail__row">
              <dt>Type</dt>
              <dd>{account.type}</dd>
            </div>
            <div className="detail__row">
              <dt>Currency</dt>
              <dd>{account.currency}</dd>
            </div>
            <div className="detail__row">
              <dt>Balance</dt>
              <dd className="detail__balance">{formatBalance(account)}</dd>
            </div>
          </dl>
        </div>
      )}
    </section>
  )
}
