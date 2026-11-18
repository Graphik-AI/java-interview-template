export type Sector = 'TECH' | 'FINANCE' | 'ENERGY' | 'HEALTH';

export type StreamStatus = 'ACTIVE' | 'HALTED';

export interface StreamItem {
  id: string;
  symbol: string;
  name: string;
  sector: Sector;
  price: number;
  prevPrice: number;
  /** Percent move from `prevPrice` to `price`. */
  changePercent: number;
  volume: number;
  status: StreamStatus;
  /**
   * Per-item clock. A tick is stale or a duplicate when
   * `tick.sequenceId <= item.sequenceId` and must be ignored.
   */
  sequenceId: number;
  updatedAt: number;
}

export interface TickDelta {
  id: string;
  /** Absolute price after the tick. */
  price?: number;
  /** Absolute volume after the tick, not an increment. */
  volume?: number;
  status?: StreamStatus;
  sequenceId: number;
  timestamp: number;
}

export interface StreamScenario {
  id: string;
  items: StreamItem[];
  /** Delay between batches while the service is running. */
  intervalMs: number;
  /**
   * Build the next batch. `emitIndex` starts at 0 and increases by one
   * after each timer fire. `now` is the clock supplied by the service.
   */
  createBatch: (emitIndex: number, now: number) => TickDelta[];
}
