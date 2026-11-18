import { cleanup, fireEvent, render, screen } from '@testing-library/react-native';
import { createPublicScenario } from '../src/data/publicFixtures';
import { TrackerScreen } from '../src/screens/TrackerScreen';
import { mockStream } from '../src/stream/MockStreamService';

beforeEach(() => {
  jest.useFakeTimers();
  mockStream.stop();
  mockStream.setScenario(createPublicScenario());
});

afterEach(async () => {
  await cleanup();
  mockStream.stop();
  mockStream.setScenario(createPublicScenario());
  jest.useRealTimers();
});

describe('TrackerScreen', () => {
  test('renders the public book and the search field', async () => {
    await render(<TrackerScreen />);

    expect(screen.getByTestId('search-input')).toBeOnTheScreen();
    expect(screen.getByTestId('ticker-row-ITEM_001')).toBeOnTheScreen();
    expect(screen.getByText('AAPL')).toBeOnTheScreen();
    expect(screen.getByTestId('ticker-row-ITEM_050')).toBeOnTheScreen();
    expect(screen.getByText('50 symbols')).toBeOnTheScreen();
  });

  test('filters rows by symbol or name', async () => {
    await render(<TrackerScreen />);

    await fireEvent.changeText(screen.getByTestId('search-input'), 'AAPL');

    expect(screen.getByTestId('ticker-row-ITEM_001')).toBeOnTheScreen();
    expect(screen.queryByTestId('ticker-row-ITEM_002')).toBeNull();

    await fireEvent.changeText(screen.getByTestId('search-input'), 'vertex');

    expect(screen.getByTestId('ticker-row-ITEM_050')).toBeOnTheScreen();
    expect(screen.queryByTestId('ticker-row-ITEM_001')).toBeNull();

    await fireEvent.changeText(screen.getByTestId('search-input'), 'not-a-symbol');

    expect(screen.getByText('No matching symbols')).toBeOnTheScreen();
    expect(screen.queryByTestId('ticker-row-ITEM_001')).toBeNull();
  });
});
