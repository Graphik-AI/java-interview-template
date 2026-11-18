# Interviewee guide

This note arrives with the live-review harness. It is not on `main`.

In the review we merge `eval/hidden-perf-harness` into your branch. That branch adds render accounting, a burst feed, a small overlay, and Jest checks. It does not contain a solved blotter. On the unmodified starter, `npm test` is red: every row re-renders, and a duplicate tick overwrites the last price. Search-under-load and the unmount listener check already pass. A fully green hidden suite is the bar for your branch after the merge.

## What lands on your branch

```bash
git merge eval/hidden-perf-harness
```

If we are bringing the branch in from another remote:

```bash
git fetch <review-remote> eval/hidden-perf-harness
git merge FETCH_HEAD
```

These files should merge cleanly:

- `src/eval/RenderTracker.ts`
- `src/eval/PerformanceHud.tsx`
- `src/eval/hiddenBurstFixtures.ts`
- `__tests__/eval/HiddenPerformanceEval.test.tsx`
- `INTERVIEWEE_GUIDE.md`

Expect conflicts in `src/components/TickerRow.tsx` and `App.tsx` if you rewrote them. Keep your UI. Put the instrumentation back yourself.

### Row counter

Inside the function that renders one row, including inside `memo`:

```tsx
import { useTrackRender } from '../eval/RenderTracker';

useTrackRender(`row:${item.id}`);
```

The hook has to run in that function body. A wrapper around a memoized row counts the parent's renders and will fail a correct solution. A hook placed after an early return is not counted on the skipped path, which is what we want.

Optional, same helper:

```tsx
useTrackRender('screen');        // TrackerScreen body
useTrackRender('detail-modal');  // only while the sheet is mounted
```

The Jest suite reads `row:<id>` only.

### HUD

`App.tsx` should mount the overlay next to the screen:

```tsx
import { PerformanceHud } from './src/eval/PerformanceHud';

<TrackerScreen />
<PerformanceHud />
```

Your screen has to load rows from `mockStream.getSeedItems()` and replace them on `mockStream.onScenarioChange`. The starter does. If you hardcoded the public fixture, the HUD toggle will not swap the book. Use those two methods.

## Run the suite

```bash
npm test -- --watchman=false HiddenPerformanceEval
npx expo start
```

`HiddenPerformanceEval` covers four behaviors:

1. **Render isolation.** 200 rows mount, the counter resets, then 30 ticks hit only `ITEM_001` and `ITEM_002`. `ITEM_003` through `ITEM_200` must stay at 0. Total row renders must be `<= 80` (30 ticks, with room for Strict Mode painting twice). A list-wide `setState` fails this. Rows that were never mounted also read 0, so also watch the HUD on device: a virtualized list that still re-renders every visible row will pass Jest only for off-screen ids and will look bad on the HUD.
2. **Stale and duplicate ticks.** Sequence 10 sets price `150.00`. A second sequence 10 (`777`) and a sequence 9 (`999`) must leave `150.00` on screen. The row has to show the price with two decimal places.
3. **Search under load.** Ticks run on a 50ms timer while the field changes to `AAPL`. The row shows `183.75` (the 15th tick). Other ids are not on screen. A filter that drops updates, or a book that ignores ticks during typing, fails.
4. **Listener leak.** After unmount, `mockStream.getListenerCount()` is 0.

`npm test` also runs `__tests__/TrackerSmoke.test.tsx`. That one should stay green.

## HUD

The pill sits at the bottom right.

| Readout | Meaning |
| --- | --- |
| Row renders/sec | `useTrackRender('row:…')` calls in the last second |
| JS stalls (>20ms) | frames whose `requestAnimationFrame` gap exceeded 20ms, since the pill mounted or since the last scenario toggle |
| Active listeners | `mockStream.getListenerCount()` |
| Burst scenario / Public scenario | swaps the live feed |

Public feed: 50 names, a batch every 500ms, usually one tick. A healthy pill stays near the number of rows whose price actually changed (about 1–2), not 50.

Burst feed: 1,000 names, 15 deltas every 250ms (60 ticks/sec). Ten symbols are hot. Each batch repeats one packet and includes one tick with an older `sequenceId` and price `999`. A healthy pill stays near the count of visible hot rows that changed, not `1000 * 60`. Stalls climbing every second mean the JS thread is blocked. Listeners should stay at 1. A number that grows as you navigate is a leak. Toggling back to Public resets the stall counter and reloads the 50-name book.

## Questions you should be ready for

- Pin about twenty names, search one of them, clear the search. Pins stay on top. Reload the app. The same ids are pinned.
- Background the app. Ticks pause. Listeners stay at 1. Foreground again. Ticks resume and the count does not become 2.
- Open the detail sheet on a hot name during the burst. History holds 20 prices. High and low ignore the `999` prints. Other rows do not render.
- Add sector volume (sum of `volume` for the selected sector) on the screen without rendering every row again.
- Toggle Burst scenario and watch row renders/sec for ten seconds. If it tracks the size of the book, the state is still one big array.

## Reading a failure

| Failure | Likely cause |
| --- | --- |
| Every `row:ITEM_*` count is 0, including `ITEM_001` | `useTrackRender` is missing, or it sits outside the row component |
| Untouched ids are above 0, or the total is hundreds | one state update replaces the whole list and rows are not isolated |
| Price becomes `999.00` or `777.00` | sequence check is missing or only the price is ignored |
| `AAPL` is missing, or the price is still the seed (`20.00`) | ticks are dropped while the filter is applied, or the timer is stopped on keypress |
| Listener count is above 0 after unmount | `subscribe` cleanup does not run, or a new subscription is added on each focus |
