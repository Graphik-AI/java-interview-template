import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { createPublicScenario } from '../data/publicFixtures';
import { mockStream } from '../stream/MockStreamService';
import { createHiddenBurstScenario } from './hiddenBurstFixtures';
import { renderTracker } from './RenderTracker';

export function PerformanceHud() {
  const [scenarioId, setScenarioId] = useState(() => mockStream.getScenario().id);
  const [rowRate, setRowRate] = useState(0);
  const [stalls, setStalls] = useState(0);
  const [listeners, setListeners] = useState(0);
  const stallsRef = useRef(0);

  useEffect(() => {
    let frame = 0;
    let last = 0;
    const loop = (now: number) => {
      if (last !== 0 && now - last > 20) {
        stallsRef.current += 1;
      }
      last = now;
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => {
      cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    const timer = setInterval(() => {
      setRowRate(renderTracker.getRecentRowRenderCount(1000));
      setListeners(mockStream.getListenerCount());
      setStalls(stallsRef.current);
    }, 1000);
    return () => {
      clearInterval(timer);
    };
  }, []);

  const burst = scenarioId === 'hidden-burst';

  const toggleScenario = () => {
    const next = burst ? createPublicScenario() : createHiddenBurstScenario();
    stallsRef.current = 0;
    setStalls(0);
    mockStream.setScenario(next);
    setScenarioId(next.id);
  };

  return (
    <View style={styles.pill} testID="perf-hud" pointerEvents="box-none">
      <Text style={styles.title}>Perf</Text>
      <Text style={styles.metric} testID="hud-row-rate">
        Row renders/sec {rowRate}
      </Text>
      <Text style={styles.metric} testID="hud-stalls">
        {`JS stalls (>20ms) ${stalls}`}
      </Text>
      <Text style={styles.metric} testID="hud-listeners">
        Active listeners {listeners}
      </Text>
      <Pressable
        testID="scenario-toggle"
        accessibilityRole="button"
        onPress={toggleScenario}
        style={styles.toggle}
      >
        <Text style={styles.toggleText}>{burst ? 'Public scenario' : 'Burst scenario'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    position: 'absolute',
    right: 12,
    bottom: 12,
    width: 196,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: 'rgba(14, 17, 22, 0.92)',
    borderWidth: 1,
    borderColor: '#3A4658',
    gap: 4,
  },
  title: {
    color: '#D6E4FF',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
  metric: {
    color: '#F2F5F8',
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  toggle: {
    marginTop: 6,
    borderRadius: 8,
    backgroundColor: '#1A2740',
    paddingVertical: 6,
    alignItems: 'center',
  },
  toggleText: {
    color: '#D6E4FF',
    fontSize: 12,
    fontWeight: '700',
  },
});
