import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL
const key = import.meta.env.VITE_SUPABASE_KEY

if (!url || !key) {
  throw new Error('Missing VITE_SUPABASE_URL / VITE_SUPABASE_KEY. Copy .env.example to .env.local.')
}

export const supabase = createClient(url, key)

/** The tablet app signs villas in as "<username>@myapp.local"; admins use the same scheme. */
export const USERNAME_DOMAIN = 'myapp.local'

export function usernameToEmail(username: string): string {
  const trimmed = username.trim()
  return trimmed.includes('@') ? trimmed : `${trimmed}@${USERNAME_DOMAIN}`
}

export function emailToUsername(email: string | undefined): string {
  return (email ?? '').split('@')[0]
}
