import { useMemo, useState, type FormEvent } from 'react'
import { createBookingForVilla, friendlyError, type Villa } from '../lib/api'
import { type Bar, type CourtLine, freeSlots } from '../lib/schedule'
import { dayLabel, hhmm } from '../lib/time'
import Modal from './Modal'

interface Props {
  lines: CourtLine[]
  villas: Villa[]
  bars: Bar[]
  ymd: string
  nowMinutes: number | null
  adminId: string
  initial?: { courtId: string; start: number }
  onDone: () => void
  onClose: () => void
}

export default function BookDialog({ lines, villas, bars, ymd, nowMinutes, adminId, initial, onDone, onClose }: Props) {
  const [villaId, setVillaId] = useState('')
  const [courtId, setCourtId] = useState(initial?.courtId ?? lines[0]?.court.id ?? '')
  const [start, setStart] = useState<number | ''>(initial?.start ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const line = lines.find((l) => l.court.id === courtId)
  const slots = useMemo(() => (line ? freeSlots(line, bars, nowMinutes) : []), [line, bars, nowMinutes])
  const startValid = start !== '' && slots.includes(start)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!line || !villaId || start === '' || !startValid) return
    setBusy(true)
    setError(null)
    try {
      await createBookingForVilla({ courtId, villaId, ymd, startMinutes: start, durationMinutes: line.sport.slot_duration_minutes, adminId })
      onDone()
    } catch (err) {
      setError(friendlyError(err as Error))
      setBusy(false)
    }
  }

  return (
    <Modal title="Book for a villa" onClose={onClose}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <p className="muted" style={{ margin: 0 }}>{dayLabel(ymd)}. The villa sees it in My Bookings on their tablet.</p>
        <div className="field">
          <label htmlFor="bk-villa">Villa</label>
          <select id="bk-villa" value={villaId} onChange={(e) => setVillaId(e.target.value)} required>
            <option value="">Choose a villa…</option>
            {villas.map((v) => <option key={v.id} value={v.id}>{v.villa_name}</option>)}
          </select>
        </div>
        <div className="row2">
          <div className="field">
            <label htmlFor="bk-court">Court</label>
            <select id="bk-court" value={courtId} onChange={(e) => { setCourtId(e.target.value); setStart('') }}>
              {groupBySport(lines).map(([sport, group]) => (
                <optgroup key={sport} label={sport}>
                  {group.map((l) => <option key={l.court.id} value={l.court.id}>{l.court.name}</option>)}
                </optgroup>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="bk-time">Time</label>
            <select id="bk-time" value={start} onChange={(e) => setStart(e.target.value === '' ? '' : Number(e.target.value))} required>
              <option value="">{slots.length ? 'Choose…' : 'No free slots'}</option>
              {slots.map((s) => <option key={s} value={s}>{hhmm(s)}–{hhmm(s + (line?.sport.slot_duration_minutes ?? 60))}</option>)}
            </select>
          </div>
        </div>
        {error && <div role="alert" className="alert">{error}</div>}
        <div className="dialog-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn--primary" disabled={busy || !villaId || !startValid}>{busy ? 'Booking…' : 'Book'}</button>
        </div>
      </form>
    </Modal>
  )
}

export function groupBySport(lines: CourtLine[]): [string, CourtLine[]][] {
  const map = new Map<string, CourtLine[]>()
  for (const l of lines) {
    const list = map.get(l.sport.name) ?? []
    list.push(l)
    map.set(l.sport.name, list)
  }
  return [...map.entries()]
}
