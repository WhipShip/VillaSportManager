import type { Court, ScheduleRow, Sport } from './api'
import { bookingMinutes, timeToMinutes } from './time'

/** A bar on the timeline: one booking, or several back-to-back block rows merged. */
export interface Bar {
  key: string
  ids: string[]
  courtId: string
  start: number
  end: number
  kind: 'booking' | 'block'
  villaName: string
  userId: string
  note: string | null
  madeByAdmin: boolean
}

export interface CourtLine {
  sport: Sport
  court: Court
}

export function courtLines(sports: Sport[]): CourtLine[] {
  return sports.flatMap((sport) => sport.courts.map((court) => ({ sport, court })))
}

export function buildBars(rows: ScheduleRow[]): Bar[] {
  const bars: Bar[] = []
  const sorted = [...rows].sort((a, b) => a.court_id.localeCompare(b.court_id) || a.start_time.localeCompare(b.start_time))
  for (const row of sorted) {
    const { start, end } = bookingMinutes(row.start_time, row.end_time)
    const kind = row.kind === 'block' ? 'block' : 'booking'
    const last = bars[bars.length - 1]
    if (kind === 'block' && last && last.kind === 'block' && last.courtId === row.court_id && last.end === start && last.note === row.note) {
      last.end = end
      last.ids.push(row.id)
      continue
    }
    bars.push({
      key: row.id,
      ids: [row.id],
      courtId: row.court_id,
      start,
      end,
      kind,
      villaName: row.villa_name ?? 'Unknown villa',
      userId: row.user_id,
      note: row.note,
      madeByAdmin: row.created_by != null && row.created_by !== row.user_id,
    })
  }
  return bars
}

/** Slot start times for a sport, from opening to closing. */
export function slotStarts(sport: Sport): number[] {
  const open = timeToMinutes(sport.open_time)
  let close = timeToMinutes(sport.close_time)
  if (close <= open) close += 1440
  const step = Math.max(15, sport.slot_duration_minutes)
  const starts: number[] = []
  for (let t = open; t + step <= close; t += step) starts.push(t)
  return starts
}

export function overlaps(aStart: number, aEnd: number, bStart: number, bEnd: number): boolean {
  return aStart < bEnd && bStart < aEnd
}

/** Free slots on one court. With `afterMinute`, only slots that haven't ended yet. */
export function freeSlots(line: CourtLine, bars: Bar[], afterMinute: number | null): number[] {
  const duration = line.sport.slot_duration_minutes
  const courtBars = bars.filter((b) => b.courtId === line.court.id)
  return slotStarts(line.sport).filter(
    (s) =>
      (afterMinute == null || s + duration > afterMinute) &&
      !courtBars.some((b) => overlaps(s, s + duration, b.start, b.end)),
  )
}

/** Earliest opening and latest closing across sports, rounded to whole hours. */
export function dayRange(sports: Sport[]): { start: number; end: number } {
  if (sports.length === 0) return { start: 8 * 60, end: 22 * 60 }
  let start = Infinity
  let end = -Infinity
  for (const s of sports) {
    const open = timeToMinutes(s.open_time)
    let close = timeToMinutes(s.close_time)
    if (close <= open) close += 1440
    start = Math.min(start, open)
    end = Math.max(end, close)
  }
  return { start: Math.floor(start / 60) * 60, end: Math.ceil(end / 60) * 60 }
}
