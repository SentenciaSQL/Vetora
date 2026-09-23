export const COUNTRY_CODES = ['DO', 'US', 'MX', 'ES', 'CO', 'AR', 'CL', 'PE'] as const;

export type CountryCode = (typeof COUNTRY_CODES)[number];

export const DEFAULT_COUNTRY_CODE: CountryCode = 'DO';

export function isCountryCode(value: string): value is CountryCode {
  return (COUNTRY_CODES as readonly string[]).includes(value);
}
