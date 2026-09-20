import {
  checkoutPayload,
  cycleAvailable,
  displayedPrice,
  formatUsage,
  isPopularPlan,
  isSuccessfulCheckoutStatus,
  monthlyEquivalentAmount,
  savingsPercentAmount,
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
  monthlyPrice: 39,
  annualPrice: 390,
  monthlyEquivalent: 32.5,
  savingsPercent: 16.7,
  monthlyAvailable: true,
  annualAvailable: true,
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
  it('uses backend price ids only for availability and never sends them in checkout', () => {
    expect(selectedPriceId(plan, 'MONTHLY')).toBe('pri_month');
    expect(selectedPriceId(plan, 'ANNUAL')).toBe('pri_year');
    expect(displayedPrice(plan, 'MONTHLY')).toBe(39);
    expect(displayedPrice(plan, 'ANNUAL')).toBe(390);
    expect(checkoutPayload(plan.id, 'MONTHLY')).toEqual({ planId: 2, billingCycle: 'MONTHLY' });
    expect(checkoutPayload(plan.id, 'ANNUAL')).toEqual({ planId: 2, billingCycle: 'ANNUAL' });
    expect(JSON.stringify(checkoutPayload(plan.id, 'ANNUAL'))).not.toContain('pri_');
  });

  it('shows the annual monthly equivalent and savings from backend amounts', () => {
    expect(monthlyEquivalentAmount(plan)).toBe(32.5);
    expect(savingsPercentAmount(plan)).toBe(16.7);
    expect(monthlyEquivalentAmount({ ...plan, monthlyEquivalent: null, annualPrice: 190 })).toBe(15.83);
    expect(savingsPercentAmount({ ...plan, savingsPercent: null, monthlyPrice: 19, annualPrice: 190 })).toBe(16.7);
  });

  it('blocks annual sale when the backend annual price id is still null', () => {
    expect(cycleAvailable({ ...plan, paddleAnnualPriceId: null, annualAvailable: false }, 'ANNUAL')).toBeFalse();
    expect(cycleAvailable(plan, 'ANNUAL')).toBeTrue();
    expect(cycleAvailable(plan, 'MONTHLY')).toBeTrue();
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
});
