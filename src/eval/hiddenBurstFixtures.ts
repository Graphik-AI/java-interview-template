import { roundPrice } from '../format/market';
import type { Sector, StreamItem, StreamScenario, TickDelta } from '../types/stream';

export const HIDDEN_BURST_ITEM_COUNT = 1000;
export const HIDDEN_BURST_INTERVAL_MS = 250;
export const HIDDEN_BURST_BATCH_SIZE = 15;
export const HIDDEN_HOT_SYMBOL_COUNT = 10;
const SEED_TIMESTAMP = 1_735_689_600_000;

const SECTORS: readonly Sector[] = ['TECH', 'FINANCE', 'ENERGY', 'HEALTH'];

export function createBurstItems(count: number): StreamItem[] {
  return Array.from({ length: count }, (_, index) => {
    const number = index + 1;
    const price = roundPrice(20 + (index % 80) + (index % 7) * 0.13);
    return {
      id: `ITEM_${String(number).padStart(3, '0')}`,
      symbol: index === 0 ? 'AAPL' : `S${String(number).padStart(4, '0')}`,
      name: index === 0 ? 'Apple Inc.' : `Synthetic Name ${number}`,
      sector: SECTORS[index % SECTORS.length],
      price,
      prevPrice: price,
      changePercent: 0,
      volume: 1_000 + index * 17,
      status: index !== 0 && index % 50 === 0 ? 'HALTED' : 'ACTIVE',
      sequenceId: 0,
      updatedAt: SEED_TIMESTAMP,
    };
  });
}

/**
 * 1,000 names. Every 250ms, 15 deltas (60 ticks/sec).
 * Ten hot names take most of the batch. One slot repeats an earlier
 * packet. One slot walks the sequence backward and prints 999.
 */
export function createHiddenBurstScenario(): StreamScenario {
  const items = createBurstItems(HIDDEN_BURST_ITEM_COUNT);
  const sequence = new Map<string, number>();

  return {
    id: 'hidden-burst',
    items,
    intervalMs: HIDDEN_BURST_INTERVAL_MS,
    createBatch(emitIndex, now) {
      const batch: TickDelta[] = [];
      for (let slot = 0; slot < 13; slot += 1) {
        const item =
          slot < HIDDEN_HOT_SYMBOL_COUNT
            ? items[slot]
            : items[(emitIndex * 13 + slot) % items.length];
        const sequenceId = (sequence.get(item.id) ?? 0) + 1;
        sequence.set(item.id, sequenceId);
        batch.push({
          id: item.id,
          price: roundPrice(item.price + ((sequenceId % 9) - 4) * 0.15),
          volume: item.volume + sequenceId * 25,
          status: 'ACTIVE',
          sequenceId,
          timestamp: now,
        });
      }

      const staleItem = items[emitIndex % HIDDEN_HOT_SYMBOL_COUNT];
      const staleSequence = Math.max(1, (sequence.get(staleItem.id) ?? 1) - 1);
      batch.push({
        id: staleItem.id,
        price: 999,
        volume: 1,
        sequenceId: staleSequence,
        timestamp: now,
      });

      const first = batch[0];
      batch.push({ ...first });

      if (batch.length !== HIDDEN_BURST_BATCH_SIZE) {
        throw new Error(
          `Hidden burst batch must contain ${HIDDEN_BURST_BATCH_SIZE} ticks, received ${batch.length}.`,
        );
      }
      return batch;
    },
  };
}
