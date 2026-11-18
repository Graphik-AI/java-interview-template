import { roundPrice } from '../format/market';
import type { Sector, StreamItem, StreamScenario, StreamStatus, TickDelta } from '../types/stream';

export const PUBLIC_ITEM_COUNT = 50;
export const PUBLIC_INTERVAL_MS = 500;
export const SEED_TIMESTAMP = 1_735_689_600_000;

type SeedRow = {
  symbol: string;
  name: string;
  sector: Sector;
  price: number;
  volume: number;
  status?: StreamStatus;
};

const SEED_ROWS: readonly SeedRow[] = [
  { symbol: 'AAPL', name: 'Apple Inc.', sector: 'TECH', price: 189.42, volume: 48_230_120 },
  { symbol: 'MSFT', name: 'Microsoft Corp.', sector: 'TECH', price: 415.18, volume: 22_140_330 },
  { symbol: 'NVDA', name: 'NVIDIA Corp.', sector: 'TECH', price: 131.55, volume: 61_882_410 },
  { symbol: 'AVGO', name: 'Broadcom Inc.', sector: 'TECH', price: 172.4, volume: 18_220_540 },
  { symbol: 'ORCL', name: 'Oracle Corp.', sector: 'TECH', price: 166.27, volume: 9_540_220 },
  { symbol: 'CRM', name: 'Salesforce Inc.', sector: 'TECH', price: 288.63, volume: 7_110_450 },
  { symbol: 'AMD', name: 'Advanced Micro Devices', sector: 'TECH', price: 142.08, volume: 33_450_900 },
  { symbol: 'INTC', name: 'Intel Corp.', sector: 'TECH', price: 24.16, volume: 41_002_180, status: 'HALTED' },
  { symbol: 'ADBE', name: 'Adobe Inc.', sector: 'TECH', price: 512.74, volume: 3_220_640 },
  { symbol: 'CSCO', name: 'Cisco Systems', sector: 'TECH', price: 56.91, volume: 15_660_270 },
  { symbol: 'IBM', name: 'International Business Machines', sector: 'TECH', price: 198.35, volume: 4_880_150 },
  { symbol: 'NOW', name: 'ServiceNow Inc.', sector: 'TECH', price: 892.1, volume: 1_540_880 },
  { symbol: 'QCOM', name: 'Qualcomm Inc.', sector: 'TECH', price: 168.44, volume: 8_930_410 },
  { symbol: 'AMAT', name: 'Applied Materials', sector: 'TECH', price: 184.72, volume: 6_420_330 },
  { symbol: 'JPM', name: 'JPMorgan Chase', sector: 'FINANCE', price: 214.58, volume: 11_240_660 },
  { symbol: 'BAC', name: 'Bank of America', sector: 'FINANCE', price: 42.37, volume: 28_910_440 },
  { symbol: 'GS', name: 'Goldman Sachs', sector: 'FINANCE', price: 548.22, volume: 2_140_780 },
  { symbol: 'MS', name: 'Morgan Stanley', sector: 'FINANCE', price: 118.65, volume: 5_670_920 },
  { symbol: 'WFC', name: 'Wells Fargo', sector: 'FINANCE', price: 64.18, volume: 14_330_250, status: 'HALTED' },
  { symbol: 'C', name: 'Citigroup', sector: 'FINANCE', price: 68.44, volume: 12_880_140 },
  { symbol: 'BLK', name: 'BlackRock', sector: 'FINANCE', price: 948.3, volume: 620_440 },
  { symbol: 'AXP', name: 'American Express', sector: 'FINANCE', price: 276.15, volume: 2_980_330 },
  { symbol: 'SCHW', name: 'Charles Schwab', sector: 'FINANCE', price: 72.84, volume: 8_120_550 },
  { symbol: 'SPGI', name: 'S&P Global', sector: 'FINANCE', price: 498.62, volume: 1_240_180 },
  { symbol: 'USB', name: 'U.S. Bancorp', sector: 'FINANCE', price: 48.27, volume: 6_540_910 },
  { symbol: 'BK', name: 'Bank of New York Mellon', sector: 'FINANCE', price: 77.53, volume: 3_410_270 },
  { symbol: 'XOM', name: 'Exxon Mobil', sector: 'ENERGY', price: 112.46, volume: 16_780_330 },
  { symbol: 'CVX', name: 'Chevron Corp.', sector: 'ENERGY', price: 154.92, volume: 9_220_140 },
  { symbol: 'COP', name: 'ConocoPhillips', sector: 'ENERGY', price: 108.35, volume: 7_640_880 },
  { symbol: 'SLB', name: 'Schlumberger', sector: 'ENERGY', price: 44.18, volume: 10_550_420 },
  { symbol: 'EOG', name: 'EOG Resources', sector: 'ENERGY', price: 128.74, volume: 3_980_150 },
  { symbol: 'PSX', name: 'Phillips 66', sector: 'ENERGY', price: 132.6, volume: 2_740_660 },
  { symbol: 'VLO', name: 'Valero Energy', sector: 'ENERGY', price: 138.22, volume: 3_120_480 },
  { symbol: 'MPC', name: 'Marathon Petroleum', sector: 'ENERGY', price: 162.48, volume: 2_460_910 },
  { symbol: 'WMB', name: 'Williams Companies', sector: 'ENERGY', price: 52.14, volume: 8_880_240 },
  { symbol: 'OKE', name: 'ONEOK', sector: 'ENERGY', price: 96.37, volume: 2_910_550 },
  { symbol: 'HAL', name: 'Halliburton', sector: 'ENERGY', price: 31.85, volume: 9_140_330, status: 'HALTED' },
  { symbol: 'BKR', name: 'Baker Hughes', sector: 'ENERGY', price: 41.26, volume: 5_670_180 },
  { symbol: 'LLY', name: 'Eli Lilly', sector: 'HEALTH', price: 812.45, volume: 3_240_660 },
  { symbol: 'UNH', name: 'UnitedHealth', sector: 'HEALTH', price: 542.18, volume: 4_110_920 },
  { symbol: 'JNJ', name: 'Johnson & Johnson', sector: 'HEALTH', price: 158.33, volume: 7_820_140 },
  { symbol: 'MRK', name: 'Merck & Co.', sector: 'HEALTH', price: 102.74, volume: 9_450_330 },
  { symbol: 'ABBV', name: 'AbbVie', sector: 'HEALTH', price: 186.52, volume: 5_330_470 },
  { symbol: 'TMO', name: 'Thermo Fisher', sector: 'HEALTH', price: 548.9, volume: 1_680_250 },
  { symbol: 'ABT', name: 'Abbott Laboratories', sector: 'HEALTH', price: 116.28, volume: 4_920_810 },
  { symbol: 'PFE', name: 'Pfizer', sector: 'HEALTH', price: 26.44, volume: 32_110_640, status: 'HALTED' },
  { symbol: 'AMGN', name: 'Amgen', sector: 'HEALTH', price: 312.67, volume: 2_240_150 },
  { symbol: 'ISRG', name: 'Intuitive Surgical', sector: 'HEALTH', price: 524.18, volume: 1_120_480 },
  { symbol: 'SYK', name: 'Stryker', sector: 'HEALTH', price: 368.42, volume: 1_540_770 },
  { symbol: 'VRTX', name: 'Vertex Pharmaceuticals', sector: 'HEALTH', price: 462.55, volume: 1_880_340 },
];

function assertSeed(rows: readonly SeedRow[]): void {
  if (rows.length !== PUBLIC_ITEM_COUNT) {
    throw new Error(`Expected ${PUBLIC_ITEM_COUNT} public seed rows, received ${rows.length}.`);
  }
  const symbols = new Set(rows.map((row) => row.symbol));
  if (symbols.size !== rows.length) {
    throw new Error('Public seed symbols must be unique.');
  }
}

assertSeed(SEED_ROWS);

export function createSeedItems(): StreamItem[] {
  return SEED_ROWS.map((row, index) => ({
    id: `ITEM_${String(index + 1).padStart(3, '0')}`,
    symbol: row.symbol,
    name: row.name,
    sector: row.sector,
    price: row.price,
    prevPrice: row.price,
    changePercent: 0,
    volume: row.volume,
    status: row.status ?? 'ACTIVE',
    sequenceId: 0,
    updatedAt: SEED_TIMESTAMP,
  }));
}

type Cursor = {
  price: number;
  volume: number;
  sequenceId: number;
};

/**
 * Calm public feed: a batch every 500ms.
 * Four of every five batches contain one tick; the fifth contains two.
 */
export function createPublicScenario(): StreamScenario {
  const items = createSeedItems();
  const cursors = new Map<string, Cursor>(
    items.map((item) => [
      item.id,
      { price: item.price, volume: item.volume, sequenceId: item.sequenceId },
    ]),
  );

  return {
    id: 'public',
    items,
    intervalMs: PUBLIC_INTERVAL_MS,
    createBatch(emitIndex, now) {
      const count = emitIndex % 5 === 4 ? 2 : 1;
      const batch: TickDelta[] = [];
      for (let offset = 0; offset < count; offset += 1) {
        const item = items[(emitIndex + offset) % items.length];
        const cursor = cursors.get(item.id);
        if (cursor == null) {
          continue;
        }
        const step = 0.05 + ((emitIndex + offset) % 5) * 0.04;
        const direction = (emitIndex + offset) % 2 === 0 ? 1 : -1;
        const price = roundPrice(Math.max(0.5, cursor.price + direction * step));
        const volume = cursor.volume + 40 + ((emitIndex + offset) % 4) * 15;
        const sequenceId = cursor.sequenceId + 1;
        cursor.price = price;
        cursor.volume = volume;
        cursor.sequenceId = sequenceId;
        batch.push({
          id: item.id,
          price,
          volume,
          sequenceId,
          timestamp: now + offset,
        });
      }
      return batch;
    },
  };
}

/** One public scenario instance. Call `createPublicScenario()` for a fresh clock. */
export const PUBLIC_SCENARIO: StreamScenario = createPublicScenario();
