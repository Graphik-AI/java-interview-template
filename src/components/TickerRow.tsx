import { Pressable, StyleSheet, Text, View } from 'react-native';
import { formatChangePercent, formatPrice, formatVolume } from '../format/market';
import type { StreamItem } from '../types/stream';

type TickerRowProps = {
  item: StreamItem;
  pinned: boolean;
  onPress: (id: string) => void;
  onTogglePin: (id: string) => void;
};

export function TickerRow({ item, pinned, onPress, onTogglePin }: TickerRowProps) {
  const movingUp = item.changePercent > 0;
  const movingDown = item.changePercent < 0;
  const halted = item.status === 'HALTED';

  return (
    <View style={styles.row} testID={`ticker-row-${item.id}`}>
      <Pressable
        style={styles.main}
        accessibilityRole="button"
        accessibilityLabel={`${item.symbol} ${item.name}`}
        onPress={() => onPress(item.id)}
      >
        <View style={styles.identity}>
          <View style={styles.titleLine}>
            <Text style={styles.symbol}>{item.symbol}</Text>
            <View style={[styles.badge, halted ? styles.badgeHalted : styles.badgeActive]}>
              <Text style={[styles.badgeText, halted ? styles.badgeTextHalted : styles.badgeTextActive]}>
                {item.status}
              </Text>
            </View>
          </View>
          <Text style={styles.name} numberOfLines={1}>
            {item.name}
          </Text>
        </View>
        <View style={styles.quote}>
          <Text style={styles.price}>{formatPrice(item.price)}</Text>
          <Text style={[styles.change, movingUp && styles.up, movingDown && styles.down]}>
            {formatChangePercent(item.changePercent)}
          </Text>
          <Text style={styles.volume}>Vol {formatVolume(item.volume)}</Text>
        </View>
      </Pressable>
      <Pressable
        testID={`pin-button-${item.id}`}
        accessibilityRole="button"
        accessibilityLabel={pinned ? `Unpin ${item.symbol}` : `Pin ${item.symbol}`}
        onPress={() => onTogglePin(item.id)}
        style={[styles.pin, pinned && styles.pinOn]}
        hitSlop={8}
      >
        <Text style={[styles.pinText, pinned && styles.pinTextOn]}>{pinned ? 'Unpin' : 'Pin'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2A3340',
    backgroundColor: '#12171E',
  },
  main: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  identity: {
    flex: 1,
    minWidth: 0,
    gap: 4,
  },
  titleLine: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  symbol: {
    color: '#F2F5F8',
    fontSize: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  name: {
    color: '#8B98A8',
    fontSize: 12,
  },
  quote: {
    alignItems: 'flex-end',
    gap: 2,
  },
  price: {
    color: '#F2F5F8',
    fontSize: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  change: {
    color: '#8B98A8',
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  up: {
    color: '#1FBF75',
  },
  down: {
    color: '#F05454',
  },
  volume: {
    color: '#8B98A8',
    fontSize: 11,
    fontVariant: ['tabular-nums'],
  },
  badge: {
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 3,
    minWidth: 62,
    alignItems: 'center',
  },
  badgeActive: {
    backgroundColor: '#163226',
  },
  badgeHalted: {
    backgroundColor: '#3A2C18',
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.4,
  },
  badgeTextActive: {
    color: '#1FBF75',
  },
  badgeTextHalted: {
    color: '#E6A15A',
  },
  pin: {
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#2A3340',
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  pinOn: {
    borderColor: '#7AA2FF',
    backgroundColor: '#1A2740',
  },
  pinText: {
    color: '#8B98A8',
    fontSize: 12,
    fontWeight: '600',
  },
  pinTextOn: {
    color: '#7AA2FF',
  },
});
