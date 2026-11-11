import type { Bar } from '../lib/schedule'
import { hhmm } from '../lib/time'

interface Props {
  bar: Bar | null
  courtLabel: string
  villaDayCount: number
  busy: boolean
  onCancel: () => void
  onClose: () => void
}

export default function SelectedBooking({ bar, courtLabel, villaDayCount, busy, onCancel, onClose }: Props) {
  if (!bar) {
    return (
      <section className="selected" aria-live="polite">
        <span className="hint">Select a booking to see details, or click an empty slot to book it for a villa.</span>
      </section>
    )
  }
  const isBlock = bar.kind === 'block'
  return (
    <section className="selected" aria-label="Selected booking" aria-live="polite">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 200 }}>
        <span className="eyebrow">{isBlock ? 'BLOCKED' : 'SELECTED'}</span>
        <span className="title">{isBlock ? courtLabel : `${bar.villaName} · ${courtLabel}`}</span>
      </div>
      <dl className="facts" style={{ margin: 0 }}>
        <div><dt>Time</dt><dd className="mono">{hhmm(bar.start)}–{hhmm(bar.end)}</dd></div>
        {isBlock ? (
          <div><dt>Reason</dt><dd>{bar.note ?? '—'}</dd></div>
        ) : (
          <>
            <div><dt>Status</dt><dd>Confirmed</dd></div>
            <div><dt>Made by</dt><dd>{bar.madeByAdmin ? 'Admin' : 'Villa tablet'}</dd></div>
            <div><dt>{bar.villaName} this day</dt><dd>{villaDayCount} {villaDayCount === 1 ? 'booking' : 'bookings'}</dd></div>
          </>
        )}
      </dl>
      <div style={{ flex: 1 }} />
      <button className="btn" onClick={onClose}>Close</button>
      <button className="btn btn--danger" onClick={onCancel} disabled={busy}>
        {busy ? 'Working…' : isBlock ? 'Remove block' : 'Cancel booking'}
      </button>
    </section>
  )
}
