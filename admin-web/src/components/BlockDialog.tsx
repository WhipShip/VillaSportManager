import { useMemo, useState, type FormEvent } from 'react'
import { blockCourt, friendlyError } from '../lib/api'
import { type Bar, type CourtLine, overlaps, slotStarts } from '../lib/schedule'
import { dayLabel, hhmm } from '../lib/time'
import Modal from './Modal'
import { groupBySport } from './BookDialog'

interface Props {
  lines: CourtLine[]
  bars: Bar[]
  ymd: string
  nowMinutes: number | null
  adminId: string
  onDone: () => void
  onClose: () => void
}

export default function BlockDialog({ lines, bars, ymd, nowMinutes, adminId, onDone, onClose }: Props) {
  const [courtId, setCourtId] = useState(lines[0]?.court.id ?? '')
  const [from, setFrom] = useState<number | ''>('')
  const [to, setTo] = useState<number | ''>('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const line = lines.find((l) => l.court.id === courtId)
  const duration = line?.sport.slot_duration_minutes ?? 60
  const starts = useMemo(
    () => (line ? slotStarts(line.sport).filter((s) => nowMinutes == null || s + duration > nowMinutes) : []),
    [line, nowMinutes, duration],
  )
  const ends = from === '' ? [] : starts.filter((s) => s >= from).map((s) => s + duration)
  const chosen = from === '' || to === '' ? [] : starts.filter((s) => s >= from && s + duration <= to)
  const conflicts = bars.filter((b) => b.courtId === courtId && chosen.some((s) => overlaps(s, s + duration, b.start, b.end)))

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!line || chosen.length === 0 || conflicts.length > 0 || !reason.trim()) return
    setBusy(true)
    setError(null)
    try {
      await blockCourt({ courtId, ymd, slotStarts: chosen, durationMinutes: duration, reason: reason.trim(), adminId })
      onDone()
    } catch (err) {
      setError(friendlyError(err as Error))
      setBusy(false)
    }
  }

  return (
    <Modal title="Block a court" onClose={onClose}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <p className="muted" style={{ margin: 0 }}>{dayLabel(ymd)}. Villas see blocked slots as booked.</p>
        <div className="field">
          <label htmlFor="bl-court">Court</label>
          <select id="bl-court" value={courtId} onChange={(e) => { setCourtId(e.target.value); setFrom(''); setTo('') }}>
            {groupBySport(lines).map(([sport, group]) => (
              <optgroup key={sport} label={sport}>
                {group.map((l) => <option key={l.court.id} value={l.court.id}>{l.court.name}</option>)}
              </optgroup>
            ))}
          </select>
        </div>
        <div className="row2">
          <div className="field">
            <label htmlFor="bl-from">From</label>
            <select id="bl-from" value={from} onChange={(e) => { const v = e.target.value === '' ? '' : Number(e.target.value); setFrom(v); setTo(v === '' ? '' : v + duration) }}>
              <option value="">Choose…</option>
              {starts.map((s) => <option key={s} value={s}>{hhmm(s)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="bl-to">Until</label>
            <select id="bl-to" value={to} onChange={(e) => setTo(e.target.value === '' ? '' : Number(e.target.value))} disabled={from === ''}>
              <option value="">Choose…</option>
              {ends.map((s) => <option key={s} value={s}>{hhmm(s)}</option>)}
            </select>
          </div>
        </div>
        <div className="field">
          <label htmlFor="bl-reason">Reason</label>
          <input id="bl-reason" maxLength={60} placeholder="e.g. Resurfacing" value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
        {conflicts.length > 0 && (
          <div role="alert" className="alert">
            Already booked in this range: {conflicts.map((b) => `${hhmm(b.start)} ${b.kind === 'block' ? 'block' : b.villaName}`).join(', ')}. Cancel those first or pick another range.
          </div>
        )}
        {error && <div role="alert" className="alert">{error}</div>}
        <div className="dialog-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn--dark" disabled={busy || chosen.length === 0 || conflicts.length > 0 || !reason.trim()}>
            {busy ? 'Blocking…' : chosen.length > 1 ? `Block ${chosen.length} slots` : 'Block slot'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
