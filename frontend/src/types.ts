export interface Account {
  id: string
  name: string
  type: string
  currency: string
  balance: number
  status: string
}

export interface User {
  username: string
  displayName: string
}

export interface AccountDirectoryEntry {
  id: string
  currency: string
  owner: string
}

export interface Transfer {
  id: string
  sourceAccountId: string
  destinationAccountId: string
  amount: string
  currency: string
  status: 'COMPLETED' | 'FAILED' | 'PENDING'
  createdAt: string
  idempotencyKey: string
}
