import { useEffect, useRef, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './lib/supabase'
import { isCurrentUserAdmin } from './lib/api'
import Login from './components/Login'
import SchedulePage from './components/SchedulePage'

type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut'; message?: string }
  | { status: 'admin'; session: Session }

export default function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' })
  // Message to show after we sign a non-admin out (the sign-out event would otherwise clear it).
  const pendingMessage = useRef<string | undefined>(undefined)

  useEffect(() => {
    let cancelled = false

    async function resolve(session: Session | null) {
      if (cancelled) return
      if (!session) {
        const message = pendingMessage.current
        pendingMessage.current = undefined
        setAuth({ status: 'signedOut', message })
        return
      }
      try {
        if (await isCurrentUserAdmin()) {
          if (!cancelled) setAuth({ status: 'admin', session })
          return
        }
        pendingMessage.current = 'This account isn’t an admin. Ask another admin to add it.'
      } catch {
        pendingMessage.current = 'Couldn’t check admin access. Has supabase/01_admin_portal.sql been run?'
      }
      await supabase.auth.signOut()
    }

    supabase.auth.getSession().then(({ data }) => resolve(data.session))
    const { data: sub } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'INITIAL_SESSION' || event === 'TOKEN_REFRESHED' || event === 'USER_UPDATED') return
      // Don't call Supabase inside the auth callback itself; defer it.
      setTimeout(() => resolve(session), 0)
    })
    return () => {
      cancelled = true
      sub.subscription.unsubscribe()
    }
  }, [])

  if (auth.status === 'loading') {
    return <div className="login"><p className="muted">Loading…</p></div>
  }
  if (auth.status === 'signedOut') {
    return <Login message={auth.message} />
  }
  return <SchedulePage session={auth.session} />
}
