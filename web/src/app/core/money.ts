const SYMBOLS: Record<string, string> = {
  DOP: 'RD$',
  USD: 'US$',
  EUR: '€'
};

export const TENANT_CURRENCIES = ['DOP', 'USD', 'EUR'] as const;

export function formatMoney(value: number | string | null | undefined, currency?: string | null): string {
  const code = (currency || 'DOP').toUpperCase();
  const amount = Number(value ?? 0);
  const formatted = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number.isFinite(amount) ? amount : 0);
  return `${SYMBOLS[code] || code} ${formatted}`;
}
