import type { Account } from './types'

export async function fetchAccounts(): Promise<Account[]> {
  const response = await fetch('/api/accounts')
  if (!response.ok) {
    throw new Error(`Failed to load accounts (${response.status})`)
  }
  return response.json() as Promise<Account[]>
}
