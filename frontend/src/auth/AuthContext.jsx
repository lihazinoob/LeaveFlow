import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { loginRequest, logoutRequest, refreshSession, setSessionExpiredHandler } from '../api/client'

const AuthContext = createContext(null)

/**
 * Holds the logged-in user for the whole app.
 *
 * status:
 *  - "loading":       on start-up, while we try to restore the session from the refresh cookie
 *  - "authenticated": user is set
 *  - "anonymous":     not logged in; protected routes redirect to /login
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState('loading')

  useEffect(() => {
    // Page reload: the in-memory access token is gone, but the httpOnly refresh cookie may still be valid.
    refreshSession()
      .then((data) => {
        setUser(data.user)
        setStatus('authenticated')
      })
      .catch(() => setStatus('anonymous'))

    // If a later refresh fails (session expired, revoked, account deactivated), sign out locally.
    setSessionExpiredHandler(() => {
      setUser(null)
      setStatus('anonymous')
    })
  }, [])

  const login = useCallback(async (email, password) => {
    const data = await loginRequest(email.trim(), password)
    setUser(data.user)
    setStatus('authenticated')
    return data.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  const value = useMemo(() => ({
    user,
    status,
    login,
    logout,
    hasRole: (...roles) => !!user && roles.includes(user.role),
  }), [user, status, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
