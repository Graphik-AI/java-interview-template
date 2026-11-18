# High-Frequency Live Stream Tracker

Two-hour senior mobile take-home. Build a live market blotter that stays smooth when the feed is chatty.

AI coding assistants (Cursor, Copilot, Claude, and others) are allowed. Write the kind of code you can walk through and extend in a 30-minute pairing session: small modules, obvious data flow, and no cleverness you cannot explain.

## Run

```bash
npm install
npx expo start
npm test
```

`npx expo start` opens Expo Go, an iOS Simulator, or an Android Emulator. No local native project is required. `npm test` runs the Jest smoke test and must stay green.

## What is already running

The starter is a single screen (`App.tsx` → `TrackerScreen`). It is a baseline, not the design you should ship.

- Types for the book and for ticks.
- A deterministic mock feed. The public scenario holds 50 names and emits a batch every 500ms. Four of every five batches contain one tick; the fifth contains two (about 2 ticks/sec).
- Search, sector pills, an in-memory pin toggle, a scrollable row list, and a detail sheet that shows the current quote.
- Tick application that writes into one array of items and re-renders that list. Pins are forgotten on reload. Session high, low, and tick history are not tracked. The app does not yet pause the feed in the background. Stale and duplicate ticks are applied.

Replace that ingestion and list path. Keep the screen usable while you do it.

## Build

### Live virtualized list

Render the book with `@shopify/flash-list` (already installed). Each row shows:

- symbol and name
- price with two decimal places (`189.42`)
- percent change from `prevPrice`, with a sign
- volume
- an `ACTIVE` or `HALTED` badge

The starter renders every row inside a `ScrollView`. That will not hold once the book or the tick rate grows. Row height should be stable.

### Search and filters

- Instant search on `symbol` or `name` (case-insensitive, substring).
- Sector filter: `ALL`, `TECH`, `FINANCE`, `ENERGY`, `HEALTH`.
- `Pinned only` toggle.

Filtering changes which ids are on screen. It does not drop ticks for hidden names. A tick that arrives while a filter is active is still in the book when the filter clears.

### Pinning

Pin and unpin any row. Pinned names stay in a section above the rest of the current filter, and they stay pinned when the filter changes. Persist the pinned id set with `@react-native-async-storage/async-storage` and restore it on the next launch.

### Live detail

Tapping a row opens a sheet or modal. While it is open, that name keeps updating. Show:

- the live quote, volume, and status
- session high and session low (seed price and every tick you apply after launch)
- a sparkline, or any compact history, of the last 20 applied prices for that name

Stale and duplicate ticks do not enter the high, low, or history.

### App lifecycle and tick identity

- When `AppState` is not `active`, pause or throttle ingestion. Resume when the app is active again. Do not stack a new subscription on every foreground.
- A tick is stale or a duplicate when `tick.sequenceId <= item.sequenceId`. Ignore the whole tick. Do not move price, volume, status, or the stored sequence.
- `volume` and `price` on a tick are absolute values, not increments.
- On unmount, the screen's listener count on `mockStream` is `0`.

## State and rendering

A tick for one id updates that id. It does not re-render the other rows.

Keep the book normalized by id. Derive the visible id list from search, sector, and pins. Give each row a stable identity (`key` is the id) and pass it data that changes only when that record changes. Callbacks passed into rows should be stable.

`zustand` is installed if you want it. A reducer, a context split by id, or FlashList's own recycling are all fine. The result that matters: one name can tick without the rest of the list rendering again.

Coalesce work if many ticks arrive before the next paint. The UI should keep taking input while ticks are in flight.

## testIDs

Leave these in place. Tests look them up directly.

| testID | Where |
| --- | --- |
| `search-input` | Search field |
| `ticker-row-<id>` | Row container, for example `ticker-row-ITEM_001` |
| `pin-button-<id>` | Pin control on that row |
| `detail-modal` | Open detail sheet |
| `sector-filter-<ALL\|TECH\|FINANCE\|ENERGY\|HEALTH>` | Sector pills |
| `pinned-only-toggle` | Pinned-only control |

Price text inside the row uses two decimal places.

## Mock feed

`mockStream` in `src/stream/MockStreamService.ts` is the source of the book.

| Method | Behavior |
| --- | --- |
| `subscribe(listener)` | Receives `TickDelta[]`. Returns an unsubscribe. |
| `start()` / `stop()` | Arms or clears the timer. `stop` resets the emit clock. |
| `pause()` / `resume()` | Timer stays armed; paused means no batches. |
| `getListenerCount()` | Active `subscribe` listeners. |
| `getSeedItems()` | Shallow copies of the active scenario's initial items. |
| `getScenario()` / `setScenario(scenario)` | Read or replace the feed. `setScenario` notifies `onScenarioChange`. |
| `onScenarioChange(listener)` | Fires after a scenario swap. Returns an unsubscribe. |
| `publish(batch)` | Delivers one batch immediately. Ignores pause. Does not move the timer clock. |

Read the initial book from `mockStream.getSeedItems()` and reload it from `onScenarioChange`. Do not import a private fixture as the only source of rows. `createPublicScenario()` builds a fresh public feed (id `public`) with its own price cursors. `PUBLIC_SCENARIO` is one already-built instance.

`react-native-reanimated` and `react-native-gesture-handler` are installed. Gesture Handler is already initialized from `index.ts`.

## Layout

```
src/types/stream.ts          StreamItem, TickDelta, StreamScenario
src/data/publicFixtures.ts   50 seed names and the public scenario
src/stream/MockStreamService.ts
src/format/market.ts
src/components/TickerRow.tsx
src/screens/TrackerScreen.tsx
__tests__/TrackerSmoke.test.tsx
DECISIONS.md
```

## Before the review

Answer the four prompts in `DECISIONS.md`. We will use them as the script for the pairing session.
