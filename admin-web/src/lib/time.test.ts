// Run with: npm test (node --test, no extra dependencies; Node 22.6+).
// Tests marked FAILING document audit findings; they pass once the finding is fixed.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { bookingMinutes, clubIso } from './time'

test('clubIso: ordinary winter slot carries the club offset', () => {
  assert.equal(clubIso('2026-11-11', 10 * 60), '2026-11-11T10:00:00+02:00')
})

test('clubIso: 1440 rolls to next midnight', () => {
  assert.equal(clubIso('2026-11-11', 1440), '2026-11-12T00:00:00+02:00')
})

// FAILING (finding B-07): a stored row with end_time <= start_time (empty range) is drawn as a
// 24-hour bar. Postgres' tstzrange treats such a range as EMPTY, so it blocks nobody at the DB
// level, yet the admin page shows the court as busy for the whole day.
test('bookingMinutes: an empty range must not become a 24h bar', () => {
  const r = bookingMinutes('2026-11-11T10:00:00+02:00', '2026-11-11T10:00:00+02:00')
  assert.deepEqual(r, { start: 600, end: 600 })
})

// FAILING (finding B-10, low): on the Egypt autumn fall-back night the local hour 23:00 occurs
// twice. The tablet (java.time atZone) resolves an ambiguous wall time to the EARLIER offset
// (+03:00); clubIso probes the wall time as if it were UTC and lands on the LATER offset (+02:00).
// The two clients therefore write different instants for "the same" slot, and the overlap
// constraint cannot relate them. 2026-10-29 is the last Thursday of October 2026.
test('clubIso: ambiguous wall time on DST fall-back resolves like java.time (earlier offset)', () => {
  assert.equal(clubIso('2026-10-29', 23 * 60), '2026-10-29T23:00:00+03:00')
})
