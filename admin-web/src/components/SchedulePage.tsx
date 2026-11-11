import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase, emailToUsername } from '../lib/supabase'
import {
  cancelBookings, fetchSchedule, fetchSports, fetchVillas, friendlyError,
  type ScheduleRow, type Sport, type Villa,
} from '../lib/api'
import { buildBars, courtLines, dayRange, type CourtLine } from '../lib/schedule'
import { addDays, clubToday, dayLabel, hhmm, minutesInClub } from '../lib/time'
import Timeline from './Timeline'
import SelectedBooking from './SelectedBooking'
import BookDialog from './BookDialog'
import BlockDialog from './BlockDialog'
import Modal from './Modal'
import { Block, ChevronLeft, ChevronRight, Close, Plus } from './Icons'

type DialogState =
  | { type: 'book'; initial?: { courtId: string; start: number } }
  | { type: 'block' }
  | { type: 'confirmCancel' }
  | null

export default function SchedulePage({ session }: { session: Session }) {
  const adminId = session.user.id
  const [ymd, setYmd] = useState(clubToday)
  const [sports, setSports] = useState<Sport[]>([])
  const [villas, setVillas] = useState<Villa[]>([])
  const [rows, setRows] = useState<ScheduleRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [dialog, setDialog] = useState<DialogState>(null)
  const [busy, setBusy] = useState(false)
  const [live, setLive] = useState(false)
  const [clock, setClock] = useState(() => new Date())

  // The realtime callback must always reload the day currently on screen.
  const ymdRef = useRef(ymd)
  ymdRef.current = ymd

  const loadSchedule = useCallback(async (day: string) => {
    try {
      const data = await fetchSchedule(day)
      if (ymdRef.current === day) setRows(data)
    } catch (e) {
      setError(friendlyError(e as Error))
    } finally {
      if (ymdRef.current === day) setLoading(false)
    }
  }, [])

  // Sports, courts and villas: once.
  useEffect(() => {
    Promise.all([fetchSports(), fetchVillas()])
      .then(([s, v]) => { setSports(s); setVillas(v) })
      .catch((e) => setError(friendlyError(e as Error)))
  }, [])

  // Bookings for the chosen day.
  useEffect(() => {
    setLoading(true)
    setSelectedKey(null)
    void loadSchedule(ymd)
  }, [ymd, loadSchedule])

  // Live updates: any change to bookings reloads the visible day.
  useEffect(() => {
    const channel = supabase
      .channel('admin-bookings')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'bookings' }, () => {
        void loadSchedule(ymdRef.current)
      })
      .subscribe((status) => setLive(status === 'SUBSCRIBED'))
    return () => { void supabase.removeChannel(channel) }
  }, [loadSchedule])

  // Clock for the "now" line.
  useEffect(() => {
    const id = window.setInterval(() => setClock(new Date()), 30_000)
    return () => window.clearInterval(id)
  }, [])

  const today = clubToday()
  const nowMinutes = ymd === today ? minutesInClub(clock) : null
  const isPastDay = ymd < today
  const bars = useMemo(() => buildBars(rows), [rows])
  const lines = useMemo(() => courtLines(sports), [sports])
  const range = useMemo(() => dayRange(sports), [sports])
  const selected = bars.find((b) => b.key === selectedKey) ?? null
  const selectedLine = selected ? lines.find((l) => l.court.id === selected.courtId) : undefined
  const courtLabel = selectedLine ? `${selectedLine.sport.name} ${selectedLine.court.name}` : 'Court'
  const villaDayCount = selected ? bars.filter((b) => b.kind === 'booking' && b.userId === selected.userId).length : 0

  async function confirmCancel() {
    if (!selected) return
    setBusy(true)
    try {
      await cancelBookings(selected.ids)
      setSelectedKey(null)
      setDialog(null)
      await loadSchedule(ymd)
    } catch (e) {
      setError(friendlyError(e as Error))
      setDialog(null)
    } finally {
      setBusy(false)
    }
  }

  function afterWrite() {
    setDialog(null)
    void loadSchedule(ymd)
  }

  return (
    <>
      <header className="topbar">
        <div className="brand">
          <img src="/kayan-crest.png" alt="Kayan Club crest" />
          <span>Kayan Club</span>
          <span className="tag">ADMIN</span>
        </div>
        <nav className="topnav" aria-label="Admin">
          <a href="/" aria-current="page">Schedule</a>
        </nav>
        <div className="who">
          <span>{emailToUsername(session.user.email)}</span>
          <button className="btn btn--outline" onClick={() => void supabase.auth.signOut()}>Sign out</button>
        </div>
      </header>

      <main className="page">
        <div className="toolbar">
          <div className="daypicker">
            <button className="btn btn--icon" aria-label="Previous day" onClick={() => setYmd((d) => addDays(d, -1))}><ChevronLeft /></button>
            <label>
              {dayLabel(ymd)}
              <input type="date" aria-label="Pick a day" value={ymd} onChange={(e) => e.target.value && setYmd(e.target.value)} />
            </label>
            <button className="btn btn--icon" aria-label="Next day" onClick={() => setYmd((d) => addDays(d, 1))}><ChevronRight /></button>
          </div>
          <button className="btn" onClick={() => setYmd(clubToday())} disabled={ymd === today}>Today</button>
          <span className={live ? 'live' : 'live offline'} role="status">
            <span className="dot" />
            {live ? `LIVE ${hhmm(minutesInClub(clock))}` : 'Reconnecting…'}
          </span>
          <div className="spacer" />
          <button className="btn btn--outline" onClick={() => setDialog({ type: 'block' })} disabled={isPastDay || lines.length === 0}>
            <Block /> Block court
          </button>
          <button className="btn btn--primary" onClick={() => setDialog({ type: 'book' })} disabled={isPastDay || lines.length === 0 || villas.length === 0}>
            <Plus /> Book for a villa
          </button>
        </div>

        {error && (
          <div className="banner" role="alert">
            <span>{error}</span>
            <button className="btn btn--icon" aria-label="Dismiss" onClick={() => setError(null)}><Close /></button>
          </div>
        )}

        {loading && rows.length === 0 && sports.length === 0 ? (
          <section className="timeline"><p className="empty">Loading schedule…</p></section>
        ) : (
          <Timeline
            sports={sports}
            bars={bars}
            range={range}
            nowMinutes={nowMinutes}
            isPastDay={isPastDay}
            selectedKey={selectedKey}
            onSelect={(bar) => setSelectedKey((k) => (k === bar.key ? null : bar.key))}
            onFreeSlot={(line: CourtLine, start: number) => setDialog({ type: 'book', initial: { courtId: line.court.id, start } })}
          />
        )}

        <SelectedBooking
          bar={selected}
          courtLabel={courtLabel}
          villaDayCount={villaDayCount}
          busy={busy}
          onCancel={() => setDialog({ type: 'confirmCancel' })}
          onClose={() => setSelectedKey(null)}
        />
      </main>

      {dialog?.type === 'book' && (
        <BookDialog
          lines={lines}
          villas={villas}
          bars={bars}
          ymd={ymd}
          nowMinutes={nowMinutes}
          adminId={adminId}
          initial={dialog.initial}
          onDone={afterWrite}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === 'block' && (
        <BlockDialog
          lines={lines}
          bars={bars}
          ymd={ymd}
          nowMinutes={nowMinutes}
          adminId={adminId}
          onDone={afterWrite}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === 'confirmCancel' && selected && (
        <Modal title={selected.kind === 'block' ? 'Remove this block?' : 'Cancel this booking?'} onClose={() => setDialog(null)}>
          <p className="muted" style={{ margin: 0 }}>
            {selected.kind === 'block'
              ? `${courtLabel}, ${hhmm(selected.start)}–${hhmm(selected.end)} opens up for villas again.`
              : `${selected.villaName} · ${courtLabel}, ${hhmm(selected.start)}–${hhmm(selected.end)}. The villa will see it as cancelled.`}
          </p>
          <div className="dialog-actions">
            <button className="btn" onClick={() => setDialog(null)}>Keep it</button>
            <button className="btn btn--danger" onClick={() => void confirmCancel()} disabled={busy}>
              {busy ? 'Working…' : selected.kind === 'block' ? 'Remove block' : 'Cancel booking'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
