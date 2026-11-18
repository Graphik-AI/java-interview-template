export function roundPrice(value: number): number {
  return Math.round(value * 100) / 100;
}

export function formatPrice(value: number): string {
  return value.toFixed(2);
}

export function formatChangePercent(value: number): string {
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

export function formatVolume(value: number): string {
  return Math.round(value)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
