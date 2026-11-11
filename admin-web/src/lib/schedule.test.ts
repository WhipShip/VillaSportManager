import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildBars, freeSlots, slotStarts, type Bar, type CourtLine } from './schedule'
import type { ScheduleRow, Sport } from './api'

const padel: Sport = { id: 'sp', name: 'Padel', slot_duration_minutes: 60, open_time: '08:00:00', close_time: '22:00:00', courts: [] }
const court = { id: 'c1', name: 'Court A', sport_id: 'sp' }
const line: CourtLine = { sport: padel, court }

function row(partial: Partial<ScheduleRow>): ScheduleRow {
  return {
    id: 'r', start_time: '2026-11-11T10:00:00+02:00', end_time: '2026-11-11T11:00:00+02:00', status: 'confirmed',
    kind: 'booking', note: null, court_id: 'c1', court_name: 'Court A', sport_id: 'sp', sport_name: 'Padel',
    user_id: 'u1', villa_name: 'Villa 1', created_by: null, ...partial,
  }
}

test('slotStarts: 08:00-22:00 at 60 min gives 14 slots', () => {
  assert.equal(slotStarts(padel).length, 14)
})

test('freeSlots: a misaligned 10:30-11:30 row hides BOTH 10:00 and 11:00 (admin is overlap-correct)', () => {
  const bars: Bar[] = buildBars([row({ start_time: '2026-11-11T10:30:00+02:00', end_time: '2026-11-11T11:30:00+02:00' })])
  const free = freeSlots(line, bars, null)
  assert.ok(!free.includes(600) && !free.includes(660))
  assert.ok(free.includes(540) && free.includes(720))
})

// FAILING (finding B-07): consequence of bookingMinutes() above. One empty-range row wipes out
// every slot of the day in the "Book for a villa" dialog, although the DB would accept them all.
test('freeSlots: an empty-range row must not blank the whole day', () => {
  const bars = buildBars([row({ start_time: '2026-11-11T10:00:00+02:00', end_time: '2026-11-11T10:00:00+02:00' })])
  const free = freeSlots(line, bars, null)
  assert.ok(free.length >= 13, `expected the other 13 slots to stay free, got ${free.length}`)
})

test('buildBars: back-to-back blocks with the same note merge into one bar', () => {
  const bars = buildBars([
    row({ id: 'b1', kind: 'block', note: 'Resurfacing', user_id: 'admin' }),
    row({ id: 'b2', kind: 'block', note: 'Resurfacing', user_id: 'admin', start_time: '2026-11-11T11:00:00+02:00', end_time: '2026-11-11T12:00:00+02:00' }),
  ])
  assert.equal(bars.length, 1)
  assert.deepEqual(bars[0].ids, ['b1', 'b2'])
})
