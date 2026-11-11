/**
 * Executable model of the 1-hour slot booking state machine (padel / tennis / football / pool).
 *
 * Run:  node --test audit/model/booking-model.test.ts        (Node 22.6+, no dependencies)
 *
 * The prose version of this model is in audit/README.md ("Formal model"). This file enumerates
 * EVERY interleaving of the actor scripts below (per-actor order preserved) against several models
 * of what the database enforces, and checks the invariants after each step.
 *
 *   DB_NONE       no constraint at all
 *   DB_EXCL_ALL   EXCLUDE (court_id WITH =, tstzrange WITH &&) over ALL rows (not partial)
 *   DB_EXCL_CONF  same, but WHERE status = 'confirmed'   <- what admin-web/README.md claims exists
 *   DB_HARDENED   DB_EXCL_CONF + supabase/02_booking_integrity.sql (status CHECK, end > start,
 *                 grid alignment trigger, per-villa limit trigger)
 *
 * The real schema is NOT in this repository and could not be inspected from the audit sandbox, so
 * the deployed database is believed (not verified) to be DB_EXCL_ALL or DB_EXCL_CONF. Every test that
 * fails under DB_EXCL_CONF is a gap the fix PR closes. Tests are tagged with the finding id.
 *
 * Client behaviour is modelled from the code, not idealised:
 *   - Tablet  (BookingViewModel): marks a slot booked only if a cached confirmed row STARTS exactly at
 *     the slot time; checks "max 2 active bookings per sport" by a SELECT before the INSERT; never
 *     re-checks availability before INSERT.
 *   - Admin   (admin-web/src/lib/schedule.ts): overlap-correct free-slot computation from its cache.
 *   - API     (anything holding a villa JWT + the publishable key): PostgREST allows any row shape
 *     that RLS and constraints allow; this models uncancel / status variants / misaligned times.
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'

// ---------------------------------------------------------------- domain
type Row = {
  id: number
  court: string
  sport: string
  user: string
  kind: 'booking' | 'block'
  status: string // free text on purpose: the DB has no CHECK today
  start: number // minutes since club midnight
  end: number
  byAdmin: boolean
}
const OPEN = 8 * 60
const CLOSE = 22 * 60
const DUR = 60
const NOW = 9 * 60 + 30 // "now" in the model: 09:30

const overlaps = (a: Row, b: Row) => a.start < b.end && b.start < a.end // empty ranges never overlap (tstzrange semantics)
const isConfirmedExact = (r: Row) => r.status === 'confirmed'
const claimsCourt = (r: Row) => r.status !== 'cancelled' // what a human would call "a live booking"
/** What a human reads from the row: an empty/inverted range still names the slot it starts in. */
const humanOverlaps = (a: Row, b: Row) => {
  const ea = a.end > a.start ? a.end : a.start + DUR
  const eb = b.end > b.start ? b.end : b.start + DUR
  return a.start < eb && b.start < ea
}

// ---------------------------------------------------------------- DB models
type Verdict = { ok: true } | { ok: false; code: string }
type DbModel = { name: string; check: (rows: Row[], cand: Row, replacing?: number) => Verdict }

function exclusion(rows: Row[], cand: Row, replacing: number | undefined, partial: boolean): Verdict {
  if (partial && !isConfirmedExact(cand)) return { ok: true }
  for (const r of rows) {
    if (r.id === replacing || r.court !== cand.court) continue
    if (partial && !isConfirmedExact(r)) continue
    if (overlaps(r, cand)) return { ok: false, code: '23P01' }
  }
  return { ok: true }
}

const DB_NONE: DbModel = { name: 'DB_NONE', check: () => ({ ok: true }) }
const DB_EXCL_ALL: DbModel = { name: 'DB_EXCL_ALL', check: (rows, c, rep) => exclusion(rows, c, rep, false) }
const DB_EXCL_CONF: DbModel = { name: 'DB_EXCL_CONF', check: (rows, c, rep) => exclusion(rows, c, rep, true) }
const DB_HARDENED: DbModel = {
  name: 'DB_HARDENED',
  check: (rows, c, rep) => {
    if (c.status !== 'confirmed' && c.status !== 'cancelled') return { ok: false, code: '23514' } // bookings_status_check
    if (!(c.end > c.start)) return { ok: false, code: '23514' } // bookings_time_order_check
    if (c.status === 'confirmed') {
      // kayan_check_slot_grid(): on the sport grid, exactly one slot long, inside opening hours
      if ((c.start - OPEN) % DUR !== 0 || c.end !== c.start + DUR || c.start < OPEN || c.end > CLOSE) return { ok: false, code: 'P0001' }
      if (!c.byAdmin && c.end <= NOW) return { ok: false, code: 'P0001' } // villas cannot book a slot that already ended
      // kayan_check_booking_limit(): max 2 active (not yet ended) villa bookings per sport, serialised by advisory lock
      if (!c.byAdmin && c.kind === 'booking') {
        const active = rows.filter(
          (r) => r.id !== rep && r.user === c.user && r.sport === c.sport && r.kind === 'booking' && r.status === 'confirmed' && r.end > NOW,
        ).length
        if (active >= 2) return { ok: false, code: 'P0001' }
      }
    }
    return exclusion(rows, c, rep, true)
  },
}
const ALL_MODELS = [DB_NONE, DB_EXCL_ALL, DB_EXCL_CONF, DB_HARDENED]

// ---------------------------------------------------------------- world + operations
type World = {
  db: Row[]
  nextId: number
  cache: Map<string, Row[]> // actor -> last snapshot of confirmed rows (tablet + admin filter status = 'confirmed')
  sel: Map<string, { court: string; start: number } | null>
  log: string[]
  rejected: string[]
}
type Op = { label: string; run: (w: World, db: DbModel) => void }

const fresh = (): World => ({ db: [], nextId: 1, cache: new Map(), sel: new Map(), log: [], rejected: [] })

function insert(w: World, db: DbModel, actor: string, r: Omit<Row, 'id'>): number | null {
  const cand: Row = { ...r, id: w.nextId }
  const v = db.check(w.db, cand)
  if (!v.ok) {
    w.rejected.push(`${actor}: insert rejected ${v.code}`)
    // A 23P01 while NO live row occupies the slot means a cancelled row is still blocking it.
    if (v.code === '23P01' && !w.db.some((r) => claimsCourt(r) && r.court === cand.court && overlaps(r, cand))) w.rejected.push(`${actor}: phantom 23P01`)
    return null
  }
  w.db.push(cand)
  w.nextId++
  return cand.id
}
function update(w: World, db: DbModel, actor: string, id: number, patch: Partial<Row>) {
  const cur = w.db.find((r) => r.id === id)
  if (!cur) return
  const cand = { ...cur, ...patch }
  const v = db.check(w.db, cand, id)
  if (!v.ok) {
    w.rejected.push(`${actor}: update rejected ${v.code}`)
    return
  }
  Object.assign(cur, patch)
}

const refresh = (actor: string): Op => ({
  label: `${actor}.refresh`,
  run: (w) => {
    w.cache.set(actor, w.db.filter(isConfirmedExact).map((r) => ({ ...r })))
  },
})

/** Tablet: slot shows as booked only when a cached confirmed row starts exactly at slot.start (BookingViewModel.bookedByTime). */
const tabletSelect = (actor: string, court: string, start: number): Op => ({
  label: `${actor}.select(${court}@${start / 60})`,
  run: (w) => {
    const cached = w.cache.get(actor) ?? []
    const booked = cached.some((r) => r.court === court && r.start === start)
    w.sel.set(actor, booked ? null : { court, start })
  },
})
/**
 * Tablet confirm is TWO round trips in BookingViewModel.confirmBooking: a SELECT that counts the
 * villa's active bookings for the sport, then (if < 2) the INSERT. Nothing links them, so another
 * actor can commit in between. No availability re-check happens before the INSERT.
 */
const tabletCheckLimit = (actor: string, user: string, sport = 'padel'): Op => ({
  label: `${actor}.countActive`,
  run: (w) => {
    if (!w.sel.get(actor)) return
    const active = w.db.filter((r) => r.user === user && r.sport === sport && r.status === 'confirmed' && r.end > NOW).length
    if (active >= 2) {
      w.rejected.push(`${actor}: client limit`)
      w.sel.set(actor, null)
    }
  },
})
const tabletInsert = (actor: string, user: string, sport = 'padel'): Op => ({
  label: `${actor}.insert`,
  run: (w, db) => {
    const s = w.sel.get(actor)
    if (!s) return
    insert(w, db, actor, { court: s.court, sport, user, kind: 'booking', status: 'confirmed', start: s.start, end: s.start + DUR, byAdmin: false })
  },
})
/** Admin: overlap-correct free-slot check against its cache, then INSERT for a villa. */
const adminBook = (court: string, start: number, villa: string, sport = 'padel'): Op => ({
  label: `admin.book(${court}@${start / 60} for ${villa})`,
  run: (w, db) => {
    const cached = w.cache.get('admin') ?? []
    const cand = { court, start, end: start + DUR } as Row
    if (cached.some((r) => r.court === court && overlaps(r, cand))) return
    insert(w, db, 'admin', { court, sport, user: villa, kind: 'booking', status: 'confirmed', start, end: start + DUR, byAdmin: true })
  },
})
const cancel = (actor: string, id: number): Op => ({ label: `${actor}.cancel(#${id})`, run: (w, db) => update(w, db, actor, id, { status: 'cancelled' }) })
/** API-only: nothing in either UI does this, but PostgREST + a villa JWT allows it unless RLS/constraints stop it. */
const apiInsert = (actor: string, r: Omit<Row, 'id'>): Op => ({
  label: `${actor}.api.insert(${r.status} ${r.start / 60}-${r.end / 60})`,
  run: (w, db) => {
    insert(w, db, actor, r)
  },
})
const apiUncancel = (actor: string, id: number): Op => ({ label: `${actor}.api.uncancel(#${id})`, run: (w, db) => update(w, db, actor, id, { status: 'confirmed' }) })

// ---------------------------------------------------------------- invariants
type Inv = { name: string; holds: (w: World) => boolean }
/** INV1 no double booking: no two live rows on one court overlap in time. */
const INV1: Inv = {
  name: 'INV1 no two live rows overlap on a court',
  holds: (w) => {
    const live = w.db.filter(claimsCourt)
    return !live.some((a) => live.some((b) => a.id < b.id && a.court === b.court && humanOverlaps(a, b)))
  },
}
/** INV2 visibility: every live row is rendered as "Booked" on the tablet for every grid slot it touches. */
const INV2: Inv = {
  name: 'INV2 every live row is visible on the tablet grid',
  holds: (w) =>
    w.db.filter(claimsCourt).every((r) => {
      const touched: number[] = []
      for (let s = OPEN; s + DUR <= CLOSE; s += DUR) if (s < r.end && r.start < s + DUR) touched.push(s)
      if (r.end <= r.start) touched.push(r.start) // empty range: a human still expects the slot it names to be held
      // tablet: visible only if the row is status 'confirmed' (exact) and starts exactly at the slot
      return touched.every((s) => isConfirmedExact(r) && r.start === s)
    }),
}
/** INV3 business rule: a villa never holds more than 2 active bookings per sport (admin-made ones excepted). */
const INV3: Inv = {
  name: 'INV3 max 2 active villa bookings per sport',
  holds: (w) => {
    const m = new Map<string, number>()
    for (const r of w.db) {
      if (r.kind === 'booking' && !r.byAdmin && r.status === 'confirmed' && r.end > NOW) {
        const k = r.user + '|' + r.sport
        m.set(k, (m.get(k) ?? 0) + 1)
      }
    }
    return [...m.values()].every((n) => n <= 2)
  },
}
/** INV4 liveness: a slot whose only booking was cancelled can be booked again (no phantom 23P01). */
const INV4_rebookable: Inv = { name: 'INV4 cancelled slot is re-bookable', holds: (w) => !w.rejected.some((x) => x.includes('phantom 23P01')) }

// ---------------------------------------------------------------- interleaving explorer
function* interleavings(scripts: Op[][]): Generator<Op[]> {
  const idx = scripts.map(() => 0)
  function* rec(acc: Op[]): Generator<Op[]> {
    let done = true
    for (let i = 0; i < scripts.length; i++) {
      if (idx[i] < scripts[i].length) {
        done = false
        acc.push(scripts[i][idx[i]++])
        yield* rec(acc)
        acc.pop()
        idx[i]--
      }
    }
    if (done) yield [...acc]
  }
  yield* rec([])
}

function explore(db: DbModel, setup: Op[], scripts: Op[][], invs: Inv[]): { count: number; violation?: { inv: string; trace: string[]; db: Row[] } } {
  let count = 0
  for (const seq of interleavings(scripts)) {
    count++
    const w = fresh()
    for (const op of [...setup, ...seq]) {
      op.run(w, db)
      w.log.push(op.label)
      for (const inv of invs) if (!inv.holds(w)) return { count, violation: { inv: inv.name, trace: w.log, db: w.db } }
    }
  }
  return { count }
}

function expectNoViolation(db: DbModel, setup: Op[], scripts: Op[][], invs: Inv[]) {
  const r = explore(db, setup, scripts, invs)
  assert.ok(!r.violation, `${db.name}: ${r.violation?.inv} violated after ${r.violation?.trace.join(' -> ')}\n  db=${JSON.stringify(r.violation?.db)}`)
  return r.count
}

// ---------------------------------------------------------------- scenarios (one test per DB model each)
const C = 'padel-1'
const TEN = 10 * 60
const ELEVEN = 11 * 60
const TWELVE = 12 * 60
const villa = (actor: string, user: string, start: number) => [refresh(actor), tabletSelect(actor, C, start), tabletCheckLimit(actor, user), tabletInsert(actor, user)]
const row = (p: Partial<Row>): Omit<Row, 'id'> => ({ court: C, sport: 'padel', user: 'v1', kind: 'booking', status: 'confirmed', start: TEN, end: ELEVEN, byAdmin: false, ...p })

for (const db of ALL_MODELS) {
  // I1 — two tablets, two villas, same slot. Passes with any exclusion constraint.  [B-01]
  test(`I1 two tablets same slot · ${db.name}`, () => {
    expectNoViolation(db, [], [villa('T1', 'v1', TEN), villa('T2', 'v2', TEN)], [INV1])
  })

  // I2 — tablet vs admin "Book for a villa", same slot.  [B-01]
  test(`I2 tablet vs admin same slot · ${db.name}`, () => {
    expectNoViolation(db, [], [villa('T1', 'v1', TEN), [refresh('admin'), adminBook(C, TEN, 'v3')]], [INV1])
  })

  // I3 — cancel then re-book then UNCANCEL via API. Needs the exclusion to be checked on UPDATE,
  // and a non-partial exclusion blocks the legitimate re-book (INV4).  [B-01, B-03]
  test(`I3 cancel / re-book / uncancel · ${db.name}`, () => {
    const setup = villa('T1', 'v1', TEN) // row #1
    expectNoViolation(db, setup, [[cancel('T1', 1), apiUncancel('T1', 1)], villa('T2', 'v2', TEN)], [INV1, INV4_rebookable])
  })

  // I4 — status spelled 'Confirmed' via the API: escapes a partial exclusion, invisible to both UIs,
  // still shown as CONFIRMED in that villa's My Bookings.  [B-02]
  test(`I4 status variant 'Confirmed' · ${db.name}`, () => {
    expectNoViolation(db, [], [[apiInsert('T1', row({ status: 'Confirmed' }))], villa('T2', 'v2', TEN)], [INV1, INV2])
  })

  // I5 — misaligned 10:30-11:30 row via the API: the DB (if any exclusion) keeps INV1, but tablets
  // show 10:00 and 11:00 as free and every attempt fails with 23P01.  [B-04]
  test(`I5 misaligned row hides from tablets · ${db.name}`, () => {
    expectNoViolation(db, [], [[apiInsert('T1', row({ start: TEN + 30, end: ELEVEN + 30 }))], villa('T2', 'v2', ELEVEN)], [INV2])
  })

  // I6 — empty range (start == end): tstzrange is EMPTY, overlaps nothing, so a second villa books
  // the same 10:00; the tablet shows both as "10:00", the admin page draws a 24h bar.  [B-05, B-07]
  test(`I6 empty-range row · ${db.name}`, () => {
    expectNoViolation(db, [], [[apiInsert('T1', row({ end: TEN }))], villa('T2', 'v2', TEN)], [INV1, INV2])
  })

  // I7 — same villa account on two tablets (or a double-send): both pass the client-side
  // "count < 2" SELECT, both INSERT -> 3 active bookings.  [B-06]
  test(`I7 limit TOCTOU, one account on two tablets · ${db.name}`, () => {
    const setup = [apiInsert('seed', row({ court: 'padel-2', start: TWELVE, end: TWELVE + DUR }))] // v1 already has 1 active
    expectNoViolation(db, setup, [villa('T1', 'v1', TEN), villa('T2', 'v1', ELEVEN)], [INV3])
  })

  // I8 — villa books a slot that already ended (tablet never disables past slots).  [B-08]
  test(`I8 villa books an already-ended slot · ${db.name}`, () => {
    const w = fresh()
    for (const op of villa('T1', 'v1', OPEN)) op.run(w, db)
    assert.equal(w.db.length, 0, `${db.name}: accepted a booking for ${OPEN / 60}:00 at NOW=${NOW / 60}:30`)
  })
}

// Sanity: the explorer really enumerates all interleavings (4 ops x 4 ops = C(8,4) = 70).
test('explorer enumerates every interleaving', () => {
  assert.equal(explore(DB_NONE, [], [villa('T1', 'v1', TEN), villa('T2', 'v2', ELEVEN)], []).count, 70)
})
