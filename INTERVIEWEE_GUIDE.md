# Interviewee guide

This note arrives with the live-review harness. It is not on `main`.

We merge `eval/hidden-perf-harness` into your branch. It adds render accounting, a second feed, an overlay, and Jest checks. It does not change your screen for you.

## Merge

```bash
git merge eval/hidden-perf-harness
```

If the branch is on another remote:

```bash
git fetch <review-remote> eval/hidden-perf-harness
git merge FETCH_HEAD
```

These paths should come in clean:

- `src/eval/RenderTracker.ts`
- `src/eval/PerformanceHud.tsx`
- `src/eval/hiddenBurstFixtures.ts`
- `__tests__/eval/HiddenPerformanceEval.test.tsx`
- `INTERVIEWEE_GUIDE.md`

If `src/components/TickerRow.tsx` or `App.tsx` conflicts, keep your UI and add the wiring below.

### Row counter

Inside the function that renders one row, including inside `memo`:

```tsx
import { useTrackRender } from '../eval/RenderTracker';

useTrackRender(`row:${item.id}`);
```

The hook has to run in that function. The suite reads `row:<id>`.

### Overlay

Mount the overlay next to your screen:

```tsx
import { PerformanceHud } from './src/eval/PerformanceHud';

<TrackerScreen />
<PerformanceHud />
```

The overlay switches the feed through `mockStream`. Load the book from `mockStream.getSeedItems()` and reload it on `mockStream.onScenarioChange`, or the switch will not change the list.

## Run

```bash
npm test -- --watchman=false
npx expo start
```

`npm test` runs the starter smoke test and `HiddenPerformanceEval`. The overlay sits at the bottom right. It shows row renders per second, JS stalls over 20ms, active listeners, and a control that switches the live feed.
