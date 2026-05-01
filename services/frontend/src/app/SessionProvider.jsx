import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api'
import { clearSession, loadSession, saveSession } from '../lib/session'

const SessionContext = createContext(null)

export function SessionProvider({ children }) {
  const [session, setSessionState] = useState(loadSession())

  useEffect(() => {
    let cancelled = false
    async function validateSession() {
      if (!session?.token) return
      try {
        await api.getMe(session.token)
      } catch (error) {
        if (cancelled) return
        clearSession()
        setSessionState(loadSession())
      }
    }
    validateSession()
    return () => {
      cancelled = true
    }
  }, [session?.token])

  const setSession = (next) => {
    const value = typeof next === 'function' ? next(session) : next
    setSessionState(value)
    saveSession(value)
  }

  const signOut = () => {
    clearSession()
    setSessionState(loadSession())
  }

  const value = useMemo(() => ({ session, setSession, signOut }), [session])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession() {
  const context = useContext(SessionContext)
  if (!context) throw new Error('useSession must be used inside SessionProvider')
  return context
}
