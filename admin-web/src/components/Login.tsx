import { useState, type FormEvent } from 'react'
import { supabase, usernameToEmail } from '../lib/supabase'

export default function Login({ message }: { message?: string }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | undefined>(undefined)
  const shown = error ?? message

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username and password.')
      return
    }
    setBusy(true)
    setError(undefined)
    const { error: signInError } = await supabase.auth.signInWithPassword({
      email: usernameToEmail(username),
      password,
    })
    setBusy(false)
    if (signInError) setError('Invalid username or password.')
    // On success, App re-renders once the admin check passes.
  }

  return (
    <main className="login">
      <div className="brand">
        <img src="/kayan-crest.png" alt="Kayan Club crest" />
        <span>Kayan Club</span>
        <span className="tag">ADMIN</span>
      </div>
      <form className="login-card" onSubmit={submit} noValidate>
        <div>
          <h1>Sign in to the schedule</h1>
          <p className="muted">Use an admin account. Villa accounts are refused.</p>
        </div>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input id="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        {shown && <div role="alert" className="alert">{shown}</div>}
        <button type="submit" className="btn btn--dark" style={{ height: 48, fontSize: 16 }} disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
