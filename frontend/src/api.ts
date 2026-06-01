import type { Account, User } from './types'

/** Thrown for non-2xx responses, carrying the HTTP status for callers. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

// The auth cookie is httpOnly, so the browser attaches it automatically as
// long as every request opts into sending credentials.
const baseInit: RequestInit = {
  credentials: 'same-origin',
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
}

async function request<T>(input: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(input, {
    ...baseInit,
    ...init,
    headers: { ...baseInit.headers, ...init.headers },
  })

  if (!response.ok) {
    throw new ApiError(response.status, `Request failed (${response.status})`)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function login(username: string, password: string): Promise<User> {
  return request<User>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}

export function fetchCurrentUser(): Promise<User> {
  return request<User>('/api/auth/me')
}

export function fetchAccounts(): Promise<Account[]> {
  return request<Account[]>('/api/accounts')
}
