import { afterEach, beforeEach, describe, expect, jest, test } from '@jest/globals';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react-native';
import { createPublicScenario } from '../../src/data/publicFixtures';
import { createBurstItems } from '../../src/eval/hiddenBurstFixtures';
import { renderTracker } from '../../src/eval/RenderTracker';
import { roundPrice } from '../../src/format/market';
import { TrackerScreen } from '../../src/screens/TrackerScreen';
import { mockStream } from '../../src/stream/MockStreamService';
import type { StreamItem, StreamScenario } from '../../src/types/stream';

function itemId(number: number): string {
  return `ITEM_${String(number).padStart(3, '0')}`;
}

function heldScenario(items: StreamItem[]): StreamScenario {
  return {
    id: 'eval-held',
    items,
    intervalMs: 60_000,
    createBatch: () => [],
  };
}

beforeEach(() => {
  jest.useFakeTimers();
  mockStream.stop();
  mockStream.setScenario(createPublicScenario());
  renderTracker.reset();
});

afterEach(async () => {
  await cleanup();
  mockStream.stop();
  mockStream.setScenario(createPublicScenario());
  renderTracker.reset();
  jest.useRealTimers();
});

describe('HiddenPerformanceEval', () => {
  test('does not render untouched rows when two ids tick', async () => {
    mockStream.setScenario(heldScenario(createBurstItems(200)));
    await render(<TrackerScreen />);
    renderTracker.reset();

    for (let index = 0; index < 30; index += 1) {
      const id = index % 2 === 0 ? itemId(1) : itemId(2);
      await act(async () => {
        mockStream.publish([
          {
            id,
            price: roundPrice(100 + index),
            sequenceId: index + 1,
            timestamp: 1_735_689_600_000 + index,
          },
        ]);
      });
    }

    expect(renderTracker.getCount(`row:${itemId(1)}`)).toBeGreaterThan(0);
    expect(renderTracker.getCount(`row:${itemId(2)}`)).toBeGreaterThan(0);
    for (let number = 3; number <= 200; number += 1) {
      expect(renderTracker.getCount(`row:${itemId(number)}`)).toBe(0);
    }
    // 30 targeted ticks. Strict Mode can render each twice. A full-list refresh is far above this.
    expect(renderTracker.getTotalRowRenders()).toBeLessThanOrEqual(80);
  });

  test('keeps the price from the newest sequence when a duplicate and a stale tick follow', async () => {
    mockStream.setScenario(heldScenario(createBurstItems(1)));
    await render(<TrackerScreen />);

    await act(async () => {
      mockStream.publish([
        { id: itemId(1), price: 150, sequenceId: 10, timestamp: 1_735_689_600_000 },
      ]);
    });
    expect(screen.getByTestId(`ticker-row-${itemId(1)}`)).toHaveTextContent('150.00', { exact: false });

    await act(async () => {
      mockStream.publish([
        { id: itemId(1), price: 777, sequenceId: 10, timestamp: 1_735_689_600_001 },
      ]);
    });
    expect(screen.getByTestId(`ticker-row-${itemId(1)}`)).toHaveTextContent('150.00', { exact: false });
    expect(screen.getByTestId(`ticker-row-${itemId(1)}`)).not.toHaveTextContent('777.00', { exact: false });

    await act(async () => {
      mockStream.publish([
        { id: itemId(1), price: 999, sequenceId: 9, timestamp: 1_735_689_600_002 },
      ]);
    });
    expect(screen.getByTestId(`ticker-row-${itemId(1)}`)).toHaveTextContent('150.00', { exact: false });
    expect(screen.getByTestId(`ticker-row-${itemId(1)}`)).not.toHaveTextContent('999.00', { exact: false });
  });

  test('applies ticks for the visible symbol while a filter is typed', async () => {
    const items = createBurstItems(200);
    mockStream.setScenario({
      id: 'search-load',
      items,
      intervalMs: 50,
      createBatch(emitIndex, now) {
        const sequenceId = emitIndex + 1;
        return [
          {
            id: itemId(1),
            price: roundPrice(180 + sequenceId * 0.25),
            sequenceId,
            timestamp: now,
          },
          {
            id: itemId(10),
            price: roundPrice(10 + sequenceId),
            sequenceId,
            timestamp: now,
          },
        ];
      },
    });

    await render(<TrackerScreen />);
    await act(async () => {
      await jest.advanceTimersByTimeAsync(500);
    });
    await fireEvent.changeText(screen.getByTestId('search-input'), 'AAPL');
    await act(async () => {
      await jest.advanceTimersByTimeAsync(250);
    });

    expect(screen.getByTestId(`ticker-row-${itemId(1)}`)).toHaveTextContent('183.75', { exact: false });
    expect(screen.queryByTestId(`ticker-row-${itemId(2)}`)).toBeNull();
    expect(screen.queryByTestId(`ticker-row-${itemId(10)}`)).toBeNull();
  });

  test('drops stream listeners when the screen unmounts', async () => {
    const view = await render(<TrackerScreen />);
    expect(mockStream.getListenerCount()).toBeGreaterThan(0);
    await view.unmount();
    expect(mockStream.getListenerCount()).toBe(0);
  });
});
