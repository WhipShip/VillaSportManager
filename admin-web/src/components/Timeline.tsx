import { Fragment, useEffect, useRef } from 'react'
import type { Sport } from '../lib/api'
import { type Bar, type CourtLine, freeSlots } from '../lib/schedule'
import { hhmm } from '../lib/time'

interface Props {
  sports: Sport[]
  bars: Bar[]
  range: { start: number; end: number }
  /** Minutes since midnight when viewing today; null for other days. */
  nowMinutes: number | null
  isPastDay: boolean
  selectedKey: string | null
  onSelect: (bar: Bar) => void
  onFreeSlot: (line: CourtLine, start: number) => void
}

export default function Timeline({ sports, bars, range, nowMinutes, isPastDay, selectedKey, onSelect, onFreeSlot }: Props) {
  const span = range.end - range.start
  const pct = (m: number) => `${((Math.min(Math.max(m, range.start), range.end) - range.start) / span) * 100}%`
  const width = (a: number, b: number) => `${((Math.min(b, range.end) - Math.max(a, range.start)) / span) * 100}%`

  const hours: number[] = []
  for (let m = range.start; m < range.end; m += 60) hours.push(m)

  const showNow = nowMinutes != null && nowMinutes >= range.start && nowMinutes <= range.end
  const pastUntil = isPastDay ? range.end : nowMinutes
  // Hide hour labels the "now" pill would cover.
  const tickVisible = (m: number) => !showNow || Math.abs(m - nowMinutes!) > 25

  // On narrow screens the timeline scrolls sideways: bring "now" into view once per day shown.
  const scroller = useRef<HTMLElement>(null)
  const nowFraction = showNow ? (nowMinutes! - range.start) / span : null
  const hasNow = nowFraction != null
  useEffect(() => {
    const el = scroller.current
    if (!el || nowFraction == null || el.scrollWidth <= el.clientWidth) return
    const labelWidth = 190
    const trackWidth = el.scrollWidth - labelWidth
    el.scrollLeft = Math.max(0, labelWidth + trackWidth * nowFraction - el.clientWidth / 3)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasNow, range.start, range.end])

  if (sports.length > 0 && sports.every((s) => s.courts.length === 0)) {
    return <section className="timeline"><p className="empty">No active courts. Add courts in Supabase (table courts, is_active = true).</p></section>
  }

  return (
    <section ref={scroller} className="timeline" aria-label="Court timeline">
      <div className="timeline-grid">
        <div className="tl-head-label" />
        <div className="tl-head" aria-hidden="true">
          {hours.filter(tickVisible).map((m) => (
            <span key={m} className="tl-tick" style={{ left: pct(m) }}>{hhmm(m)}</span>
          ))}
          {showNow && <span className="tl-now-label" style={{ left: pct(nowMinutes!) }}>{hhmm(nowMinutes!)}</span>}
        </div>

        {sports.filter((s) => s.courts.length > 0).map((sport) => (
          <Fragment key={sport.id}>{renderSport(sport)}</Fragment>
        ))}
      </div>
    </section>
  )

  function renderSport(sport: Sport) {
    return (
      <>
        <div className="tl-group">{sport.name}</div>
        {sport.courts.map((court) => {
          const line: CourtLine = { sport, court }
          const courtBars = bars.filter((b) => b.courtId === court.id)
          const busyNow = nowMinutes != null && courtBars.some((b) => b.start <= nowMinutes && nowMinutes < b.end)
          const slots = isPastDay ? [] : freeSlots(line, bars, nowMinutes)
          return (
            <div key={court.id} style={{ display: 'contents' }}>
              <div className="tl-court">
                <strong>{court.name}</strong>
                {nowMinutes != null && <span>{busyNow ? 'In use now' : 'Free now'}</span>}
              </div>
              <div className="tl-track">
                {pastUntil != null && pastUntil > range.start && <div className="tl-past" style={{ width: pct(pastUntil) }} />}
                {hours.map((m) => <div key={m} className="tl-hourline" style={{ left: pct(m) }} />)}
                {slots.map((start) => (
                  <button
                    key={`slot-${start}`}
                    className="slot"
                    style={{ left: pct(start), width: width(start, start + sport.slot_duration_minutes) }}
                    aria-label={`Book ${sport.name} ${court.name} at ${hhmm(start)}`}
                    onClick={() => onFreeSlot(line, start)}
                  >
                    + Book
                  </button>
                ))}
                {courtBars.map((bar) => {
                  const past = nowMinutes != null ? bar.end <= nowMinutes : isPastDay
                  const label = bar.kind === 'block' ? `Blocked · ${bar.note ?? 'No reason'}` : bar.villaName
                  const cls = ['bar', bar.kind === 'block' ? 'bar--block' : past ? 'bar--past' : ''].join(' ')
                  return (
                    <button
                      key={bar.key}
                      className={cls}
                      style={{ left: pct(bar.start), width: `calc(${width(bar.start, bar.end)} - 4px)`, marginLeft: 2 }}
                      aria-pressed={bar.key === selectedKey}
                      title={`${label} · ${hhmm(bar.start)}–${hhmm(bar.end)}`}
                      onClick={() => onSelect(bar)}
                    >
                      {label}
                    </button>
                  )
                })}
                {showNow && <div className="tl-now" style={{ left: pct(nowMinutes!) }} />}
              </div>
            </div>
          )
        })}
      </>
    )
  }
}
