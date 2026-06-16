import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  ApiError,
  createTransfer,
  fetchAccountDirectory,
  type TransferResponse,
} from '../api'
import type { AccountDirectoryEntry } from '../types'

interface TransferFormProps {
  sourceAccountId: string
  onSuccess?: (response: TransferResponse) => void
}

export default function TransferForm({ sourceAccountId, onSuccess }: TransferFormProps) {
  const [directory, setDirectory] = useState<AccountDirectoryEntry[]>([])
  const [destination, setDestination] = useState('')
  const [amount, setAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Stable for the lifetime of this form so retries of the same submission
  // reuse one idempotency key rather than creating duplicate transfers.
  const idempotencyKey = useMemo(() => crypto.randomUUID(), [])

  useEffect(() => {
    fetchAccountDirectory()
      .then(setDirectory)
      .catch((err: unknown) =>
        setError(err instanceof ApiError ? err.message : 'Could not load accounts.'),
      )
  }, [])

  const destinations = useMemo(
    () => directory.filter((entry) => entry.id !== sourceAccountId),
    [directory, sourceAccountId],
  )

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const response = await createTransfer(sourceAccountId, idempotencyKey, {
        destination,
        amount,
      })
      onSuccess?.(response)
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : 'Unable to reach the server.')
    } finally {
      setSubmitting(false)
    }
  }

  const disabled = !destination || !amount || submitting

  return (
    <form className="transfer-form" onSubmit={handleSubmit}>
      <label className="transfer-form__field">
        <span>Destination account</span>
        <select
          className="transfer-form__select"
          value={destination}
          onChange={(e) => setDestination(e.target.value)}
          required
        >
          <option value="" disabled>
            Select an account…
          </option>
          {destinations.map((entry) => (
            <option key={entry.id} value={entry.id}>
              {entry.id} ({entry.currency}) — {entry.owner}
            </option>
          ))}
        </select>
      </label>

      <label className="transfer-form__field">
        <span>Amount</span>
        <input
          className="transfer-form__input"
          type="text"
          inputMode="decimal"
          pattern="[0-9]+(\.[0-9]{1,2})?"
          placeholder="0.00"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          required
        />
      </label>

      {error && <p className="transfer-form__error">{error}</p>}

      <button className="transfer-form__submit" type="submit" disabled={disabled}>
        {submitting ? 'Sending…' : 'Send transfer'}
      </button>
    </form>
  )
}
