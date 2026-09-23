import { TranslateService } from '@ngx-translate/core';

export const VETERINARY_SPECIALTIES = [
  'GENERAL_MEDICINE',
  'INTERNAL_MEDICINE',
  'SURGERY',
  'DERMATOLOGY',
  'CARDIOLOGY',
  'NEUROLOGY',
  'ONCOLOGY',
  'OPHTHALMOLOGY',
  'DENTISTRY',
  'ANESTHESIOLOGY',
  'DIAGNOSTIC_IMAGING',
  'EMERGENCY_CRITICAL_CARE',
  'FELINE_MEDICINE',
  'EXOTIC_ANIMALS',
  'ORTHOPEDICS_TRAUMATOLOGY',
  'REPRODUCTION',
  'PATHOLOGY',
  'OTHER'
] as const;

export const PLATFORM_ROLES = [
  'SUPER_ADMIN',
  'TENANT_OWNER',
  'TENANT_ADMIN',
  'VETERINARIAN',
  'RECEPTIONIST',
  'PET_OWNER'
] as const;

export const TEAM_ASSIGNABLE_ROLES = ['TENANT_ADMIN', 'VETERINARIAN', 'RECEPTIONIST'] as const;

export function translatedCode(i18n: TranslateService, prefix: string, code?: string | null): string {
  if (!code) {
    return '';
  }
  const key = `${prefix}.${code}`;
  const value = i18n.instant(key);
  return value === key ? code : value;
}

export function roleLabel(i18n: TranslateService, code?: string | null): string {
  return translatedCode(i18n, 'roles', code);
}

export function statusLabel(i18n: TranslateService, code?: string | null): string {
  if (!code) {
    return '';
  }
  const status = translatedCode(i18n, 'statuses', code);
  if (status !== code) {
    return status;
  }
  const vaccine = translatedCode(i18n, 'pets.vaccineStatus', code);
  if (vaccine !== code) {
    return vaccine;
  }
  const billing = translatedCode(i18n, 'billing.status', code);
  if (billing !== code) {
    return billing;
  }
  if (code === 'ACTIVE') {
    return translatedCode(i18n, 'team', 'active');
  }
  if (code === 'INACTIVE') {
    return translatedCode(i18n, 'team', 'inactive');
  }
  return /^[A-Z0-9_]+$/.test(code) ? '' : code;
}

export function specialtyLabel(
  i18n: TranslateService,
  code?: string | null,
  other?: string | null,
  legacy?: string | null
): string {
  const value = code || legacy;
  if (!value) {
    return '';
  }
  if (value === 'OTHER') {
    return other?.trim() || translatedCode(i18n, 'specialties', 'OTHER');
  }
  const known = translatedCode(i18n, 'specialties', value);
  if (known !== value) {
    return known;
  }
  return other?.trim() || value;
}
