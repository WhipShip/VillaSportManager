/** All schedule maths happens in the club's time zone, whatever the admin's computer is set to. */
export const CLUB_TZ = 'Africa/Cairo'

const ymdFormat = new Intl.DateTimeFormat('en-CA', { timeZone: CLUB_TZ, year: 'numeric', month: '2-digit', day: '2-digit' })
const hmFormat = new Intl.DateTimeFormat('en-GB', { timeZone: CLUB_TZ, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
const offsetFormat = new Intl.DateTimeFormat('en-US', { timeZone: CLUB_TZ, timeZoneName: 'longOffset' })
const dayLabelFormat = new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })

/** "2026-10-23" for the given instant, in club time. */
export function ymdInClub(d: Date): string {
  return ymdFormat.format(d)
}

export function clubToday(): string {
  return ymdInClub(new Date())
}

/** Minutes since midnight, in club time. */
export function minutesInClub(d: Date): number {
  const parts = hmFormat.formatToParts(d)
  const h = Number(parts.find((p) => p.type === 'hour')?.value ?? 0)
  const m = Number(parts.find((p) => p.type === 'minute')?.value ?? 0)
  return h * 60 + m
}

/** "20:00" */
export function hhmm(minutes: number): string {
  const h = Math.floor(minutes / 60) % 24
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** "08:00:00" (Postgres time) → 480 */
export function timeToMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}

export function addDays(ymd: string, days: number): string {
  const d = new Date(`${ymd}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** "Fri 23 Oct 2026" */
export function dayLabel(ymd: string): string {
  return dayLabelFormat.format(new Date(`${ymd}T12:00:00Z`))
}

function clubOffset(ymd: string, minutes: number): string {
  // Evaluate the zone's offset around that wall-clock time (handles summer time).
  const probe = new Date(`${ymd}T${hhmm(minutes)}:00Z`)
  const name = offsetFormat.formatToParts(probe).find((p) => p.type === 'timeZoneName')?.value ?? 'GMT'
  const match = name.match(/GMT([+-]\d{2}):?(\d{2})?/)
  return match ? `${match[1]}:${match[2] ?? '00'}` : '+00:00'
}

/** ISO timestamp with the club's offset, e.g. "2026-10-23T20:00:00+03:00". 1440 = next midnight. */
export function clubIso(ymd: string, minutes: number): string {
  let day = ymd
  let mins = minutes
  while (mins >= 1440) {
    day = addDays(day, 1)
    mins -= 1440
  }
  return `${day}T${hhmm(mins)}:00${clubOffset(day, mins)}`
}

/** Start/end of a stored booking as club-time minutes on its day (end may be 1440 for midnight). */
export function bookingMinutes(startIso: string, endIso: string): { start: number; end: number } {
  const start = minutesInClub(new Date(startIso))
  let end = minutesInClub(new Date(endIso))
  if (end <= start) end += 1440
  return { start, end }
}
