import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { TickerRow } from '../components/TickerRow';
import { formatChangePercent, formatPrice, formatVolume } from '../format/market';
import { mockStream } from '../stream/MockStreamService';
import type { StreamItem, TickDelta } from '../types/stream';

const SECTOR_FILTERS = ['ALL', 'TECH', 'FINANCE', 'ENERGY', 'HEALTH'] as const;

type SectorFilter = (typeof SECTOR_FILTERS)[number];

function applyTicks(items: StreamItem[], batch: TickDelta[]): StreamItem[] {
  if (batch.length === 0) {
    return items;
  }
  const indexById = new Map(items.map((item, index) => [item.id, index]));
  const next = items.slice();
  let changed = false;
  for (const tick of batch) {
    const index = indexById.get(tick.id);
    if (index == null) {
      continue;
    }
    const current = next[index];
    const updated: StreamItem = { ...current };
    if (tick.price != null) {
      updated.prevPrice = current.price;
      updated.price = tick.price;
      updated.changePercent =
        current.price === 0 ? 0 : ((tick.price - current.price) / current.price) * 100;
    }
    if (tick.volume != null) {
      updated.volume = tick.volume;
    }
    if (tick.status != null) {
      updated.status = tick.status;
    }
    updated.sequenceId = tick.sequenceId;
    updated.updatedAt = tick.timestamp;
    next[index] = updated;
    changed = true;
  }
  return changed ? next : items;
}

export function TrackerScreen() {
  const [items, setItems] = useState<StreamItem[]>(() => mockStream.getSeedItems());
  const [query, setQuery] = useState('');
  const [sector, setSector] = useState<SectorFilter>('ALL');
  const [pinnedOnly, setPinnedOnly] = useState(false);
  const [pinnedIds, setPinnedIds] = useState<ReadonlySet<string>>(() => new Set());
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    setItems(mockStream.getSeedItems());
    const unsubscribeTicks = mockStream.subscribe((batch) => {
      setItems((current) => applyTicks(current, batch));
    });
    const unsubscribeScenario = mockStream.onScenarioChange(() => {
      setItems(mockStream.getSeedItems());
    });
    mockStream.start();
    return () => {
      unsubscribeTicks();
      unsubscribeScenario();
      mockStream.stop();
    };
  }, []);

  const togglePin = useCallback((id: string) => {
    setPinnedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    const pinned: StreamItem[] = [];
    const rest: StreamItem[] = [];
    for (const item of items) {
      if (pinnedOnly && !pinnedIds.has(item.id)) {
        continue;
      }
      if (sector !== 'ALL' && item.sector !== sector) {
        continue;
      }
      if (
        normalized.length > 0 &&
        !item.symbol.toLowerCase().includes(normalized) &&
        !item.name.toLowerCase().includes(normalized)
      ) {
        continue;
      }
      if (pinnedIds.has(item.id)) {
        pinned.push(item);
      } else {
        rest.push(item);
      }
    }
    return { pinned, rest };
  }, [items, pinnedIds, pinnedOnly, query, sector]);

  const selected = selectedId == null ? null : (items.find((item) => item.id === selectedId) ?? null);
  const showingPins = visible.pinned.length > 0;

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <Text style={styles.title}>Live Stream</Text>
        <Text style={styles.count}>{items.length} symbols</Text>
      </View>
      <TextInput
        testID="search-input"
        value={query}
        onChangeText={setQuery}
        placeholder="Search symbol or name"
        placeholderTextColor="#6E7A89"
        autoCapitalize="none"
        autoCorrect={false}
        accessibilityLabel="Search symbols"
        style={styles.search}
      />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filters}
        keyboardShouldPersistTaps="handled"
      >
        {SECTOR_FILTERS.map((option) => {
          const selectedFilter = option === sector;
          return (
            <Pressable
              key={option}
              testID={`sector-filter-${option}`}
              accessibilityRole="button"
              accessibilityState={{ selected: selectedFilter }}
              onPress={() => setSector(option)}
              style={[styles.chip, selectedFilter && styles.chipSelected]}
            >
              <Text style={[styles.chipText, selectedFilter && styles.chipTextSelected]}>{option}</Text>
            </Pressable>
          );
        })}
        <Pressable
          testID="pinned-only-toggle"
          accessibilityRole="button"
          accessibilityState={{ selected: pinnedOnly }}
          onPress={() => setPinnedOnly((current) => !current)}
          style={[styles.chip, pinnedOnly && styles.chipSelected]}
        >
          <Text style={[styles.chipText, pinnedOnly && styles.chipTextSelected]}>Pinned only</Text>
        </Pressable>
      </ScrollView>
      <ScrollView style={styles.list} testID="ticker-list" keyboardShouldPersistTaps="handled">
        {visible.pinned.length === 0 && visible.rest.length === 0 ? (
          <Text style={styles.empty}>No matching symbols</Text>
        ) : null}
        {showingPins ? <Text style={styles.section}>Pinned</Text> : null}
        {visible.pinned.map((item) => (
          <TickerRow
            key={item.id}
            item={item}
            pinned
            onPress={setSelectedId}
            onTogglePin={togglePin}
          />
        ))}
        {showingPins && visible.rest.length > 0 ? <Text style={styles.section}>Market</Text> : null}
        {visible.rest.map((item) => (
          <TickerRow
            key={item.id}
            item={item}
            pinned={false}
            onPress={setSelectedId}
            onTogglePin={togglePin}
          />
        ))}
      </ScrollView>
      <Modal
        visible={selected != null}
        animationType="slide"
        transparent
        onRequestClose={() => setSelectedId(null)}
      >
        <View style={styles.backdrop}>
          {selected != null ? (
            <DetailSheet item={selected} onClose={() => setSelectedId(null)} />
          ) : null}
        </View>
      </Modal>
    </View>
  );
}

function DetailSheet({ item, onClose }: { item: StreamItem; onClose: () => void }) {
  return (
    <View style={styles.sheet} testID="detail-modal">
      <View style={styles.sheetHeader}>
        <View>
          <Text style={styles.sheetSymbol}>{item.symbol}</Text>
          <Text style={styles.sheetName}>{item.name}</Text>
        </View>
        <Pressable
          testID="detail-close"
          accessibilityRole="button"
          accessibilityLabel="Close details"
          onPress={onClose}
          style={styles.close}
        >
          <Text style={styles.closeText}>Close</Text>
        </Pressable>
      </View>
      <Text style={styles.sheetPrice}>{formatPrice(item.price)}</Text>
      <Text style={styles.sheetChange}>{formatChangePercent(item.changePercent)}</Text>
      <View style={styles.metrics}>
        <Metric label="Sector" value={item.sector} />
        <Metric label="Status" value={item.status} />
        <Metric label="Volume" value={formatVolume(item.volume)} />
        <Metric label="Previous" value={formatPrice(item.prevPrice)} />
        <Metric label="Sequence" value={String(item.sequenceId)} />
        <Metric label="Updated" value={new Date(item.updatedAt).toISOString()} />
      </View>
      <Text style={styles.placeholder}>
        Session high, low, and the last 20 ticks are not tracked yet.
      </Text>
    </View>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metric}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0E1116',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  title: {
    color: '#F2F5F8',
    fontSize: 22,
    fontWeight: '700',
  },
  count: {
    color: '#8B98A8',
    fontSize: 13,
  },
  search: {
    marginTop: 12,
    marginHorizontal: 16,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#2A3340',
    backgroundColor: '#171C24',
    color: '#F2F5F8',
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
  },
  filters: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 8,
  },
  chip: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#2A3340',
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#171C24',
  },
  chipSelected: {
    backgroundColor: '#1A2740',
    borderColor: '#7AA2FF',
  },
  chipText: {
    color: '#8B98A8',
    fontSize: 12,
    fontWeight: '700',
  },
  chipTextSelected: {
    color: '#D6E4FF',
  },
  list: {
    flex: 1,
  },
  section: {
    color: '#8B98A8',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
  },
  empty: {
    color: '#8B98A8',
    textAlign: 'center',
    marginTop: 48,
    fontSize: 15,
  },
  backdrop: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0, 0, 0, 0.55)',
  },
  sheet: {
    backgroundColor: '#171C24',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    gap: 8,
  },
  sheetHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  sheetSymbol: {
    color: '#F2F5F8',
    fontSize: 20,
    fontWeight: '700',
  },
  sheetName: {
    color: '#8B98A8',
    marginTop: 2,
  },
  close: {
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  closeText: {
    color: '#7AA2FF',
    fontWeight: '700',
  },
  sheetPrice: {
    color: '#F2F5F8',
    fontSize: 32,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  sheetChange: {
    color: '#8B98A8',
    fontVariant: ['tabular-nums'],
  },
  metrics: {
    marginTop: 8,
    gap: 8,
  },
  metric: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  metricLabel: {
    color: '#8B98A8',
    fontSize: 13,
  },
  metricValue: {
    color: '#F2F5F8',
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    flexShrink: 1,
    textAlign: 'right',
  },
  placeholder: {
    marginTop: 12,
    color: '#8B98A8',
    lineHeight: 20,
  },
});
