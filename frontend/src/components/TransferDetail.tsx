import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, fetchTransfer } from '../api'
import type { TransferDetailView } from '../types'

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(amount)
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(iso))
}

export default function TransferDetail() {
  const { transferId } = useParams<{ transferId: string }>()
  const [transfer, setTransfer] = useState<TransferDetailView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!transferId) {
      return
    }
    setLoading(true)
    setError(null)
    fetchTransfer(transferId)
      .then(setTransfer)
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 404) {
          setError('That transfer was not found.')
        } else if (err instanceof ApiError) {
          setError(err.message)
        } else {
          setError('Could not load this transfer.')
        }
      })
      .finally(() => setLoading(false))
  }, [transferId])

  return (
    <section>
      <p>
        <Link className="back-link" to="/">
          ← Back to accounts
        </Link>
      </p>

      {loading && <p>Loading transfer…</p>}
      {error && <p className="app__error">{error}</p>}

      {!loading && !error && transfer && (
        <div className="detail">
          <div className="detail__heading">
            <h2 className="detail__name">Transfer</h2>
            <span className={`badge badge--${transfer.status.toLowerCase()}`}>
              {transfer.status}
            </span>
          </div>

          <dl className="detail__grid">
            <div className="detail__row">
              <dt>Amount</dt>
              <dd>{formatAmount(transfer.amount, transfer.currency)}</dd>
            </div>
            <div className="detail__row">
              <dt>From</dt>
              <dd>
                {transfer.source.owner} — {transfer.source.id} ({transfer.source.currency})
              </dd>
            </div>
            <div className="detail__row">
              <dt>To</dt>
              <dd>
                {transfer.destination.owner} — {transfer.destination.id} ({transfer.destination.currency})
              </dd>
            </div>
            <div className="detail__row">
              <dt>Created</dt>
              <dd>{formatDate(transfer.createdAt)}</dd>
            </div>
            <div className="detail__row">
              <dt>Transfer ID</dt>
              <dd>{transfer.id}</dd>
            </div>
          </dl>
        </div>
      )}
    </section>
  )
}
