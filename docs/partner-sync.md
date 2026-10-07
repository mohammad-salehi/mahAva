# Data sync, partner sync and personal patterns (design note)

## Her data lives on the server (source of truth)
- Endpoints, all requiring the Mah token:
  - `GET /api/mah/data?since=<cursor>`: `since=0` is a full restore after login.
  - `POST /api/mah/data/sync {changes:[{kind,key,data,deleted,updatedAt}]}`
  - `DELETE /api/mah/data`
  - `POST /api/mah/auth/delete-account {password}`
- What syncs, by `kind` and `key`:
  - `profile/main`, minus device-only fields: lock and sample-data flags
  - `period/<startEpochDay>`
  - `log/<epochDay>`
  - `reminder/<id>`
- Conflicts are last-write-wins on the client's `updatedAt`. A device clock in the future is clamped to server time.
- Deletes are stored as tombstones and expire after 180 days.
- Server limits:
  - 500 changes per request and a 2 MB body
  - 16 KB per record and 20,000 records per user
  - flat primitive fields only
  - per-user rate limit
- Room is only an offline cache. `sync/DataSyncRepository.kt` finds offline edits by comparing each item's hash with its hash at the last sync, and pushes them through a network-constrained WorkManager job (`DataSync`). That job also runs every 6 hours to pick up edits made on other phones.
- First login on a phone: local-only items are uploaded once and merged. Items without their own timestamp (reminders) are stamped with the current time, because the server rejects a zero time.
  - When both sides differ, the newer copy wins.
  - The profile always comes from the server.
  - If the account already has data and the phone only holds onboarding answers, the server copy replaces them.
- Logout first tries to send pending changes; it warns if some could not be sent, then clears the local cache. Deleting the account removes everything on the server.
- The partner share (`GET /partner/share`) returns everything she logs: her full log for today, her logs from the last 120 days, and up to 24 periods. When her phone has not pushed a snapshot, the server derives her phase, cycle day and predictions from her synced periods and profile (`deriveCycle` in `partner.service.js`). There is no allowed-fields filter.
- Server copies are merged over the entity defaults on the phone, so a field missing on the server never nulls a required field (`withDefaults` in `DataSyncRepository`).
- Tests: `tests/mah-data.test.js` (backend) and `DataSyncRoundTripTest` (app) cover every record type, deletes, restore after re-login, and that his view matches her logs.

## Personal patterns
- `pattern/PersonalPattern.kt` computes patterns on the phone from her own period starts and daily logs.
- A pattern is shown only when there are at least 2 complete cycles (each 15 to 90 days long) and the item was logged in at least 2 of them. Only the last 6 cycles are checked.
- Below that threshold the screens show the general, sourced science text, together with an honest "not enough data yet" line.
- The pattern shows up in three places:
  - the Today card «الگوی خودت در این روزها»
  - teaser rows, as «الگوی خودت: …»
  - the signal detail card «بر اساس الگوی خودت»

## Partner pairing and sync
- Server endpoints live under `/api/mah/partner/*` and require the Mah token. See `dara-backend/src/routes/mah/partner.routes.js`.
- Pairing works like this:
  1. She creates a 6-character code that expires after 30 minutes.
  2. He redeems it. Redemption is rate-limited per user.
  3. She approves.
- Each person can have one active partner, and pairs are female↔male only.
- Approving the request is her consent. The approval dialog says, in simple Persian, that her husband will see everything she logs. There is no separate consent screen or switch.
- No names are stored or shown. The partner is always «همسر»; the server only returns a masked phone number.
- If she has not logged anything today, he sees the same sourced science content as her Today screen for her phase (cravings, mood, body, pain).
- Unpairing from either side ends the pair, deletes the snapshot and events, and leaves a data-free `unpaired` event for the other side.
- Subscriptions are never copied. Premium is active if the user or their active partner has a valid subscription of their own.

## Running with the app closed
- All jobs are unique WorkManager jobs. `BootAndTimeReceiver` re-enqueues them on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- On Android 13+ the partner screens ask for `POST_NOTIFICATIONS`. The `mahava_partner` channel is created at app start.
- The permission request is shown inside the app. There are no redirects to system settings.
- All approvals and alerts appear inside the app (dialogs and the notifications list behind the bell icon). His system notifications appear only while the app is closed; while it is open, the app polls every 5 to 60 seconds.

## Why polling and not push
- FCM is unreliable in Iran. Instead, `PartnerSyncWorker` (WorkManager) runs every 15 minutes, which is the Android minimum, and only while a pair exists or a code is waiting.
- Each run calls `GET /partner/changes?since=`, records every event in the in-app notifications list, and (on his phone, app closed) posts local notifications on channel `mahava_partner`:
  - pair requests and approvals
  - removal
  - her updates
  - one daily status after 09:00
- On her phone, changes are pushed about 2.5 s after a log or period change (debounced) and also on each periodic run.
- Limits:
  - Updates arrive within roughly 15 minutes, not instantly.
  - Doze and battery savers (e.g. MIUI) can delay the worker further.
