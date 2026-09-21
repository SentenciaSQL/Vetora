import { Injectable, inject, signal } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { BillingConfig, BillingPlan, ChangePreview, CheckoutSession, PortalSession, TenantSubscription } from '../models';

export type BillingCycle = 'MONTHLY' | 'ANNUAL';

@Injectable({ providedIn: 'root' })
export class BillingService {
  private api = inject(ApiService);
  subscription = signal<TenantSubscription | null>(null);
  config = signal<BillingConfig | null>(null);

  loadConfig(): Observable<BillingConfig> {
    return this.api.get<BillingConfig>('/billing/config').pipe(tap(config => this.config.set(config)));
  }

  loadSubscription(): Observable<TenantSubscription> {
    return this.api.get<TenantSubscription>('/billing/subscription').pipe(tap(sub => this.subscription.set(sub)));
  }

  plans(): Observable<BillingPlan[]> {
    return this.api.get<BillingPlan[]>('/billing/plans');
  }

  checkout(planId: number, billingCycle: BillingCycle = 'MONTHLY'): Observable<CheckoutSession> {
    return this.api.post<CheckoutSession>('/billing/checkout', { planId, billingCycle });
  }

  previewChange(planId: number, billingCycle: BillingCycle): Observable<ChangePreview> {
    return this.api.post<ChangePreview>('/billing/subscription/change-plan/preview', { planId, billingCycle });
  }

  customerPortal(): Observable<PortalSession> {
    return this.api.post<PortalSession>('/billing/customer-portal', {});
  }

  cancel(effectiveFrom = 'next_billing_period'): Observable<TenantSubscription> {
    return this.api.post<TenantSubscription>('/billing/subscription/cancel', { effectiveFrom }).pipe(
      tap(sub => this.subscription.set(sub))
    );
  }

  changePlan(planId: number, billingCycle: BillingCycle): Observable<TenantSubscription> {
    return this.api.post<TenantSubscription>('/billing/subscription/change-plan', { planId, billingCycle }).pipe(
      tap(sub => this.subscription.set(sub))
    );
  }

  cancelPendingChange(): Observable<TenantSubscription> {
    return this.api.post<TenantSubscription>('/billing/subscription/pending-change/cancel', {}).pipe(
      tap(sub => this.subscription.set(sub))
    );
  }

  isSuspended(): boolean {
    return !!this.subscription()?.suspended;
  }

  isGracePeriod(): boolean {
    return !!this.subscription()?.gracePeriod;
  }
}
