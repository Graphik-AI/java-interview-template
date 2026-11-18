import { createPublicScenario } from '../data/publicFixtures';
import type { StreamItem, StreamScenario, TickDelta } from '../types/stream';

/**
 * In-process stand-in for a multiplexed market feed.
 * `publish` fans a batch out immediately. The timer does not, while paused.
 */
export class MockStreamService {
  private readonly listeners = new Set<(batch: TickDelta[]) => void>();
  private readonly scenarioListeners = new Set<() => void>();
  private timer: ReturnType<typeof setInterval> | null = null;
  private scenario: StreamScenario;
  private emitIndex = 0;
  private running = false;
  private paused = false;

  constructor(scenario: StreamScenario = createPublicScenario()) {
    this.scenario = scenario;
  }

  subscribe(listener: (batch: TickDelta[]) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /** Fires after `setScenario` replaces the active book. */
  onScenarioChange(listener: () => void): () => void {
    this.scenarioListeners.add(listener);
    return () => {
      this.scenarioListeners.delete(listener);
    };
  }

  getListenerCount(): number {
    return this.listeners.size;
  }

  getScenario(): StreamScenario {
    return this.scenario;
  }

  getSeedItems(): StreamItem[] {
    return this.scenario.items.map((item) => ({ ...item }));
  }

  setScenario(scenario: StreamScenario): void {
    const shouldRun = this.running;
    const shouldPause = this.paused;
    this.stop();
    this.scenario = scenario;
    for (const listener of [...this.scenarioListeners]) {
      listener();
    }
    if (shouldRun) {
      this.start();
      if (shouldPause) {
        this.pause();
      }
    }
  }

  /** Deliver one batch now. Ignores pause. Does not advance the timer clock. */
  publish(batch: TickDelta[]): void {
    this.dispatch(batch);
  }

  start(): void {
    if (this.running) {
      this.paused = false;
      return;
    }
    this.running = true;
    this.paused = false;
    this.timer = setInterval(() => {
      this.pump();
    }, this.scenario.intervalMs);
  }

  stop(): void {
    if (this.timer != null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.running = false;
    this.paused = false;
    this.emitIndex = 0;
  }

  pause(): void {
    if (this.running) {
      this.paused = true;
    }
  }

  resume(): void {
    if (this.running) {
      this.paused = false;
    }
  }

  private pump(): void {
    if (!this.running || this.paused) {
      return;
    }
    const batch = this.scenario.createBatch(this.emitIndex, Date.now());
    this.emitIndex += 1;
    this.dispatch(batch);
  }

  private dispatch(batch: TickDelta[]): void {
    for (const listener of [...this.listeners]) {
      listener(batch);
    }
  }
}

export const mockStream = new MockStreamService();
