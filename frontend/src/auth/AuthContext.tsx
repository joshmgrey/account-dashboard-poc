import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  ApiError,
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
} from '../api'
import type { User } from '../types'

interface AuthContextValue {
  user: User | null
  initializing: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [initializing, setInitializing] = useState(true)

  // On load, ask the backend whether the existing cookie is a valid session.
  useEffect(() => {
    fetchCurrentUser()
      .then(setUser)
      .catch((err: unknown) => {
        if (!(err instanceof ApiError) || err.status !== 401) {
          // 401 just means "not logged in"; anything else is unexpected.
          console.error('Failed to restore session', err)
        }
        setUser(null)
      })
      .finally(() => setInitializing(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const loggedIn = await loginRequest(username, password)
    setUser(loggedIn)
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({ user, initializing, login, logout }),
    [user, initializing, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
