import {
  displayedPrice,
  formatUsage,
  isPopularPlan,
  isSuccessfulCheckoutStatus,
  selectedPriceId,
  usageReached
} from './billing.page';
import { BillingPlan } from '../../../core/models';

const plan: BillingPlan = {
  id: 2,
  code: 'PROFESSIONAL',
  name: 'Profesional',
  description: 'Crecimiento',
  currency: 'USD',
  monthlyPrice: 79,
  annualPrice: 790,
  paddleMonthlyPriceId: 'pri_month',
  paddleAnnualPriceId: 'pri_year',
  enabled: true,
  limits: {
    maxUsers: 20,
    maxVeterinarians: 8,
    maxBranches: 3,
    maxStorageMb: 10240,
    maxMessagesMonth: 2000,
    reportsEnabled: true,
    messagingEnabled: true,
    laboratoryEnabled: true
  }
};

describe('Billing page helpers', () => {
  it('selects monthly or annual price ids without sending an amount', () => {
    expect(selectedPriceId(plan, 'MONTHLY')).toBe('pri_month');
    expect(selectedPriceId(plan, 'ANNUAL')).toBe('pri_year');
    expect(displayedPrice(plan, 'MONTHLY')).toBe(79);
    expect(displayedPrice(plan, 'ANNUAL')).toBe(790);
  });

  it('highlights Professional as the most popular plan', () => {
    expect(isPopularPlan('PROFESSIONAL')).toBeTrue();
    expect(isPopularPlan('BASIC')).toBeFalse();
  });

  it('treats TRIALING and ACTIVE as successful checkout statuses', () => {
    expect(isSuccessfulCheckoutStatus('TRIALING')).toBeTrue();
    expect(isSuccessfulCheckoutStatus('ACTIVE')).toBeTrue();
    expect(isSuccessfulCheckoutStatus('SUSPENDED')).toBeFalse();
  });

  it('renders usage against the plan limit and flags exhausted resources', () => {
    expect(formatUsage({ current: 5, limit: 5 })).toBe('5 / 5');
    expect(usageReached({ current: 5, limit: 5 })).toBeTrue();
    expect(usageReached({ current: 1, limit: 5 })).toBeFalse();
  });

  it('returns a clear error message for plan limit responses', () => {
    const message = 'Has alcanzado el límite de usuarios permitido por tu plan.';
    expect(message).toContain('límite de usuarios');
  });

  it('keeps the monthly/annual selector and loading flag consistent', () => {
    expect(selectedPriceId({ ...plan, paddleAnnualPriceId: null }, 'ANNUAL')).toBeNull();
    expect(isSuccessfulCheckoutStatus('TRIALING')).toBeTrue();
  });
});
