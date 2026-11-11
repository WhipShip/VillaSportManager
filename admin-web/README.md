# Kayan Club · Admin site

Web page for admins: live court schedule, cancel bookings, book for a villa, block courts.
React + Vite + TypeScript, talking directly to the same Supabase project as the tablet app.

## One-time Supabase setup

1. SQL Editor → run `../supabase/01_admin_portal.sql`.
2. Authentication → Users → **Add user**: email `frontdesk@myapp.local`, a password, tick **Auto Confirm User**.
3. SQL Editor → run (change the email if you used another username):
   ```sql
   insert into public.admins (user_id)
   select id from auth.users where email = 'frontdesk@myapp.local'
   on conflict do nothing;
   ```
4. Optional: fix villa names in **Table Editor → profiles** (e.g. the account `mo` has no display name yet).

## Run it on your computer

Needs Node.js 20+.

```bat
cd admin-web
npm install
npm run dev
```

Open http://localhost:5173 and sign in with `frontdesk` and its password.
`.env.local` holds the Supabase URL and publishable key (same values as the Android app; see `.env.example`).

## Deploy (free static hosting)

`npm run build` makes the `dist/` folder. On Vercel, Netlify or Cloudflare Pages:
- Root directory: `admin-web`
- Build command: `npm run build`, output folder: `dist`
- Environment variables: `VITE_SUPABASE_URL` and `VITE_SUPABASE_KEY` (copy from `.env.example`)

## How admin actions are stored

| Action | Database change |
|---|---|
| Cancel | `bookings.status = 'cancelled'` (same as the app) |
| Book for a villa | new `bookings` row with the villa's `user_id`, `created_by` = admin |
| Block a court | one `bookings` row per slot, `kind = 'block'`, `note` = reason, `user_id` = admin |

Blocks are normal confirmed rows, so the tablet app shows them as "Booked" and the
existing overlap rule (`bookings_court_id_tstzrange_excl`) stops double-booking.
