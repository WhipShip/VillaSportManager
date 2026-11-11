import type { PostgrestError } from '@supabase/supabase-js'
import { supabase } from './supabase'
import { addDays, clubIso } from './time'

export interface Court {
  id: string
  name: string
  sport_id: string
}

export interface Sport {
  id: string
  name: string
  slot_duration_minutes: number
  open_time: string
  close_time: string
  courts: Court[]
}

/** One row of the `admin_schedule` view (see supabase/01_admin_portal.sql). */
export interface ScheduleRow {
  id: string
  start_time: string
  end_time: string
  status: string
  kind: 'booking' | 'block'
  note: string | null
  court_id: string
  court_name: string
  sport_id: string
  sport_name: string
  user_id: string
  villa_name: string | null
  created_by: string | null
}

export interface Villa {
  id: string
  villa_name: string
}

/** Turns Postgres errors into something an admin can act on. */
export function friendlyError(error: PostgrestError | Error | null | undefined): string {
  if (!error) return 'Something went wrong. Please try again.'
  const code = 'code' in error ? error.code : undefined
  if (code === '23P01' || code === '23505') return 'That time was just taken on this court. Pick another slot.'
  if (code === '42501') return 'Your account isn’t allowed to do that. Is it in the admins table?'
  if (code === '42P01') return 'The admin tables are missing. Run supabase/01_admin_portal.sql first.'
  return error.message || 'Something went wrong. Please try again.'
}

export async function isCurrentUserAdmin(): Promise<boolean> {
  const { data, error } = await supabase.rpc('is_admin')
  if (error) throw error
  return data === true
}

export async function fetchSports(): Promise<Sport[]> {
  const { data, error } = await supabase
    .from('sports')
    .select('id, name, slot_duration_minutes, open_time, close_time, courts(id, name, sport_id)')
    .order('name')
  if (error) throw error
  const sports = (data ?? []) as Sport[]
  return sports.map((s) => ({
    ...s,
    courts: [...(s.courts ?? [])].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true })),
  }))
}

/** Confirmed bookings and blocks that start on the given club day. */
export async function fetchSchedule(ymd: string): Promise<ScheduleRow[]> {
  const { data, error } = await supabase
    .from('admin_schedule')
    .select('*')
    .eq('status', 'confirmed')
    .gte('start_time', clubIso(ymd, 0))
    .lt('start_time', clubIso(addDays(ymd, 1), 0))
    .order('start_time')
  if (error) throw error
  return (data ?? []) as ScheduleRow[]
}

/** Villa accounts (admin accounts are left out). */
export async function fetchVillas(): Promise<Villa[]> {
  const [profiles, admins] = await Promise.all([
    supabase.from('profiles').select('id, villa_name'),
    supabase.from('admins').select('user_id'),
  ])
  if (profiles.error) throw profiles.error
  if (admins.error) throw admins.error
  const adminIds = new Set(((admins.data ?? []) as { user_id: string }[]).map((a) => a.user_id))
  return ((profiles.data ?? []) as Villa[])
    .filter((v) => !adminIds.has(v.id))
    .sort((a, b) => a.villa_name.localeCompare(b.villa_name, undefined, { numeric: true }))
}

export async function cancelBookings(ids: string[]): Promise<void> {
  const { error } = await supabase.from('bookings').update({ status: 'cancelled' }).in('id', ids)
  if (error) throw error
}

export async function createBookingForVilla(args: {
  courtId: string
  villaId: string
  ymd: string
  startMinutes: number
  durationMinutes: number
  adminId: string
}): Promise<void> {
  const { error } = await supabase.from('bookings').insert({
    court_id: args.courtId,
    user_id: args.villaId,
    start_time: clubIso(args.ymd, args.startMinutes),
    end_time: clubIso(args.ymd, args.startMinutes + args.durationMinutes),
    status: 'confirmed',
    kind: 'booking',
    created_by: args.adminId,
  })
  if (error) throw error
}

/**
 * Blocks a court by inserting one confirmed row per slot (kind = 'block').
 * One insert statement, so it is all-or-nothing: if any slot is taken, nothing is blocked.
 */
export async function blockCourt(args: {
  courtId: string
  ymd: string
  slotStarts: number[]
  durationMinutes: number
  reason: string
  adminId: string
}): Promise<void> {
  const rows = args.slotStarts.map((start) => ({
    court_id: args.courtId,
    user_id: args.adminId,
    start_time: clubIso(args.ymd, start),
    end_time: clubIso(args.ymd, start + args.durationMinutes),
    status: 'confirmed',
    kind: 'block',
    note: args.reason,
    created_by: args.adminId,
  }))
  const { error } = await supabase.from('bookings').insert(rows)
  if (error) throw error
}
