import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { BillingService } from '../../../core/services/billing.service';
import { PaddleService } from '../../../core/services/paddle.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/services/auth.service';
import { BillingPlan, ChangePreview, PublicPlan, UsageMetric } from '../../../core/models';
import { StatusBadgePipe } from '../../../shared/ui/status-badge.pipe';

export type BillingCycle = 'MONTHLY' | 'ANNUAL';

export function selectedPriceId(plan: BillingPlan, cycle: BillingCycle): string | null {
  if (cycle === 'ANNUAL') {
    return plan.paddleAnnualPriceId || null;
  }
  return plan.paddleMonthlyPriceId || null;
}

export function cycleAvailable(plan: Pick<BillingPlan, 'annualAvailable' | 'monthlyAvailable' | 'paddleAnnualPriceId' | 'paddleMonthlyPriceId'>, cycle: BillingCycle): boolean {
  if (cycle === 'ANNUAL') {
    return plan.annualAvailable !== false && (plan.annualAvailable === true || !!plan.paddleAnnualPriceId);
  }
  return plan.monthlyAvailable !== false && (plan.monthlyAvailable === true || !!plan.paddleMonthlyPriceId);
}

export function displayedPrice(plan: Pick<BillingPlan, 'monthlyPrice' | 'annualPrice'>, cycle: BillingCycle): number {
  if (cycle === 'ANNUAL' && plan.annualPrice != null) {
    return Number(plan.annualPrice);
  }
  return Number(plan.monthlyPrice);
}

export function monthlyEquivalentAmount(plan: Pick<BillingPlan, 'monthlyEquivalent' | 'annualPrice'>): number | null {
  if (plan.monthlyEquivalent != null) {
    return Number(plan.monthlyEquivalent);
  }
  if (plan.annualPrice == null) {
    return null;
  }
  return Math.round((Number(plan.annualPrice) / 12) * 100) / 100;
}

export function savingsPercentAmount(plan: Pick<BillingPlan, 'savingsPercent' | 'monthlyPrice' | 'annualPrice'>): number | null {
  if (plan.savingsPercent != null) {
    return Number(plan.savingsPercent);
  }
  const monthly = Number(plan.monthlyPrice);
  const annual = Number(plan.annualPrice);
  if (!monthly || !annual) {
    return null;
  }
  return Math.round(((monthly * 12 - annual) / (monthly * 12)) * 1000) / 10;
}

export function checkoutPayload(planId: number, billingCycle: BillingCycle) {
  return { planId, billingCycle };
}

export function isPopularPlan(code?: string | null): boolean {
  return (code || '').toUpperCase() === 'PROFESSIONAL';
}

export function showsFreeTrial(
  plan: Pick<BillingPlan, 'code' | 'monthlyTrialDays'> | Pick<PublicPlan, 'code' | 'monthlyTrialDays'> | null | undefined,
  cycle: BillingCycle
): boolean {
  if (!plan || cycle !== 'MONTHLY') {
    return false;
  }
  return (plan.code || '').toUpperCase() === 'BASIC' && (plan.monthlyTrialDays || 0) > 0;
}

export function isSuccessfulCheckoutStatus(status?: string | null): boolean {
  return status === 'ACTIVE' || status === 'TRIALING' || status === 'TRIAL';
}

export function formatUsage(metric?: UsageMetric | null): string {
  if (!metric) {
    return '—';
  }
  return `${metric.current} / ${metric.limit}`;
}

export function usageReached(metric?: UsageMetric | null): boolean {
  return !!metric && metric.limit >= 0 && metric.current >= metric.limit;
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, StatusBadgePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'billing.title' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ 'billing.subtitle' | translate }}</p>

    @if (subscription()?.suspended) {
      <section class="card mt-6 border-rose-200 bg-rose-50 dark:border-rose-900 dark:bg-rose-950/40">
        <h2 class="font-display text-xl font-semibold text-rose-800 dark:text-rose-100">{{ 'billing.suspendedTitle' | translate }}</h2>
        <p class="mt-2 text-sm text-rose-700 dark:text-rose-200">{{ 'billing.suspendedBody' | translate }}</p>
        @if (subscription()?.gracePeriodEndsAt) {
          <p class="mt-2 text-sm font-medium">{{ 'billing.suspendedOn' | translate }}: {{ subscription()?.gracePeriodEndsAt | date:'mediumDate' }}</p>
        }
      </section>
    } @else if (subscription()?.gracePeriod) {
      <section class="card mt-6 border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/40">
        <h2 class="font-medium text-amber-900 dark:text-amber-100">{{ 'billing.graceTitle' | translate }}</h2>
        <p class="mt-1 text-sm text-amber-800 dark:text-amber-200">
          {{ 'billing.graceBody' | translate:{ date: (subscription()?.gracePeriodEndsAt | date:'mediumDate') } }}
        </p>
      </section>
    } @else if (isSuccessfulCheckoutStatus(subscription()?.status)) {
      <section class="card mt-6 border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/30">
        <p class="text-sm font-medium text-emerald-800 dark:text-emerald-100">
          {{ subscription()?.status === 'TRIALING' ? ('billing.trialingOk' | translate) : ('billing.activeOk' | translate) }}
        </p>
      </section>
    }

    <section class="card mt-6 space-y-3">
      <h2 class="font-medium">{{ 'billing.current' | translate }}</h2>
      @if (subscription(); as sub) {
        <p><span class="text-slate-500">{{ 'admin.plan' | translate }}:</span> {{ sub.planName || sub.planCode }}</p>
        <p><span class="text-slate-500">{{ 'common.status' | translate }}:</span>
          <span [class]="sub.status | statusBadge">{{ ('billing.status.' + sub.status) | translate }}</span>
        </p>
        <p class="text-sm text-slate-500">{{ 'billing.cycle' | translate }}: {{ sub.billingCycle || '—' }} · {{ sub.currency }}</p>
        @if (sub.currentPeriodEndsAt) {
          <p class="text-sm text-slate-500">{{ 'billing.periodEnd' | translate }}: {{ sub.currentPeriodEndsAt | date:'mediumDate' }}</p>
        }
        @if (sub.nextBillingAt) {
          <p class="text-sm text-slate-500">{{ 'billing.nextCharge' | translate }}: {{ sub.nextBillingAt | date:'mediumDate' }}</p>
        }
        @if (sub.scheduledChangeEffectiveAt) {
          <p class="text-sm text-amber-700">{{ 'billing.cancelsOn' | translate }}: {{ sub.scheduledChangeEffectiveAt | date:'mediumDate' }}</p>
        }
        @if (sub.usage; as usage) {
          <ul class="grid gap-1 text-sm text-slate-600 dark:text-slate-300 sm:grid-cols-2">
            <li [class.text-rose-600]="usageReached(usage.users)">{{ 'admin.users' | translate }}: {{ formatUsage(usage.users) }}</li>
            <li [class.text-rose-600]="usageReached(usage.veterinarians)">{{ 'nav.team' | translate }}: {{ formatUsage(usage.veterinarians) }}</li>
            <li [class.text-rose-600]="usageReached(usage.branches)">{{ 'nav.branches' | translate }}: {{ formatUsage(usage.branches) }}</li>
            <li [class.text-rose-600]="usageReached(usage.storageMb)">{{ 'admin.storageMb' | translate }}: {{ formatUsage(usage.storageMb) }}</li>
            <li [class.text-rose-600]="usageReached(usage.messagesMonth)">{{ 'admin.messagesMonth' | translate }}: {{ formatUsage(usage.messagesMonth) }}</li>
          </ul>
        }
      }
      <div class="flex flex-wrap gap-3">
        @if (canManage() && subscription()?.hasPaddleCustomer) {
          <button type="button" class="btn-secondary" [disabled]="busy()" (click)="openPortal()">{{ 'billing.portal' | translate }}</button>
        }
        @if (canManage() && (subscription()?.status === 'ACTIVE' || subscription()?.status === 'TRIALING') && !subscription()?.scheduledChangeEffectiveAt) {
          <button type="button" class="btn-secondary" [disabled]="busy()" (click)="cancel()">{{ 'billing.cancel' | translate }}</button>
        }
      </div>
    </section>

    @if (preview(); as change) {
      <section class="card mt-6 space-y-2 border-brand-200">
        <h2 class="font-medium">{{ 'billing.changePreviewTitle' | translate }}</h2>
        <p class="text-sm">{{ 'billing.currentPlan' | translate }}: {{ change.currentPlanName || change.currentPlanCode }} ({{ change.currentCycle || '—' }})</p>
        <p class="text-sm">{{ 'billing.newPlan' | translate }}: {{ change.newPlanName || change.newPlanCode }} ({{ change.newCycle }})</p>
        <p class="text-sm">{{ 'billing.estimatedAmount' | translate }}:
          {{ change.estimatedAmount == null ? '—' : (change.estimatedAmount | number:'1.2-2') }} {{ change.currency }}
        </p>
        <p class="text-sm">{{ 'billing.nextCharge' | translate }}: {{ change.nextBillingAt ? (change.nextBillingAt | date:'mediumDate') : '—' }}</p>
        <div class="flex flex-wrap gap-2">
          <button type="button" class="btn-primary" [disabled]="busy()" (click)="confirmChange()">{{ 'billing.confirmChange' | translate }}</button>
          <button type="button" class="btn-secondary" [disabled]="busy()" (click)="preview.set(null)">{{ 'common.cancel' | translate }}</button>
        </div>
      </section>
    }

    <section class="mt-8">
      <div class="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <h2 class="font-display text-xl font-semibold">{{ 'billing.plans' | translate }}</h2>
        <div class="inline-flex w-full rounded-full border border-slate-200 p-1 text-sm sm:w-auto dark:border-slate-700">
          <button type="button" class="flex-1 rounded-full px-3 py-1 sm:flex-none" [class.bg-brand-600]="cycle() === 'MONTHLY'" [class.text-white]="cycle() === 'MONTHLY'" (click)="cycle.set('MONTHLY')">{{ 'billing.monthly' | translate }}</button>
          <button type="button" class="flex-1 rounded-full px-3 py-1 sm:flex-none" [class.bg-brand-600]="cycle() === 'ANNUAL'" [class.text-white]="cycle() === 'ANNUAL'" (click)="cycle.set('ANNUAL')">{{ 'billing.annual' | translate }}</button>
        </div>
      </div>
      <div class="mt-4 grid gap-4 md:grid-cols-3">
        @for (plan of plans(); track plan.id) {
          <article class="card relative space-y-3" [class.ring-2]="isPopularPlan(plan.code)" [class.ring-brand-500]="isPopularPlan(plan.code)">
            @if (isPopularPlan(plan.code)) {
              <p class="absolute right-4 top-4 rounded-full bg-brand-600 px-2 py-0.5 text-xs text-white">{{ 'billing.mostPopular' | translate }}</p>
            }
            <p class="text-xs uppercase tracking-wide text-slate-400">{{ plan.code }}</p>
            <h3 class="font-display text-xl font-semibold">{{ plan.name }}</h3>
            <p class="text-sm text-slate-500">{{ plan.description }}</p>
            <p class="text-3xl font-semibold">{{ displayedPrice(plan, cycle()) | number:'1.2-2' }} {{ plan.currency }}</p>
            <p class="text-xs text-slate-400">{{ cycle() === 'ANNUAL' ? ('billing.perYear' | translate) : ('billing.perMonth' | translate) }}</p>
            @if (cycle() === 'ANNUAL' && monthlyEquivalentAmount(plan); as equivalent) {
              <p class="text-sm text-slate-500">{{ equivalent | number:'1.2-2' }} {{ plan.currency }}/mes, {{ 'billing.billedAnnually' | translate }}</p>
              <p class="text-xs font-medium text-brand-700">{{ 'billing.twoMonthsFree' | translate }}
                @if (savingsPercentAmount(plan); as save) {
                  · {{ 'billing.savePercent' | translate:{ percent: save } }}
                }
              </p>
            }
            @if (!hasActivePaddleSubscription() && showsFreeTrial(plan, cycle())) {
              <p class="text-xs font-medium text-brand-700">{{ 'billing.basicMonthlyTrial' | translate }}</p>
            }
            <ul class="text-sm text-slate-600 dark:text-slate-300">
              <li>{{ 'admin.users' | translate }}: {{ plan.limits.maxUsers }}</li>
              <li>{{ 'nav.team' | translate }}: {{ plan.limits.maxVeterinarians }}</li>
              <li>{{ 'nav.branches' | translate }}: {{ plan.limits.maxBranches }}</li>
              <li>{{ 'admin.storageMb' | translate }}: {{ plan.limits.maxStorageMb }}</li>
              <li>{{ 'admin.messagesMonth' | translate }}: {{ plan.limits.maxMessagesMonth }}</li>
              <li>{{ 'nav.reports' | translate }}: {{ plan.limits.reportsEnabled ? ('common.yes' | translate) : ('common.no' | translate) }}</li>
              <li>{{ 'nav.messages' | translate }}: {{ plan.limits.messagingEnabled ? ('common.yes' | translate) : ('common.no' | translate) }}</li>
              <li>{{ 'pets.tabs.labs' | translate }}: {{ plan.limits.laboratoryEnabled ? ('common.yes' | translate) : ('common.no' | translate) }}</li>
            </ul>
            @if (canManage() && cycleAvailable(plan, cycle())) {
              <button type="button" class="btn-primary w-full" [disabled]="busy()" (click)="subscribe(plan)">
                {{ hasActivePaddleSubscription() ? ('billing.changePlan' | translate) : ('billing.subscribe' | translate) }}
              </button>
            } @else if (canManage() && cycle() === 'ANNUAL') {
              <p class="text-xs text-slate-400">{{ 'billing.annualUnavailable' | translate }}</p>
            }
          </article>
        }
      </div>
    </section>
  `
})
export class BillingPage implements OnInit {
  private billing = inject(BillingService);
  private paddle = inject(PaddleService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private i18n = inject(TranslateService);

  plans = signal<BillingPlan[]>([]);
  subscription = this.billing.subscription;
  busy = signal(false);
  cycle = signal<BillingCycle>('MONTHLY');
  preview = signal<ChangePreview | null>(null);

  readonly selectedPriceId = selectedPriceId;
  readonly cycleAvailable = cycleAvailable;
  readonly displayedPrice = displayedPrice;
  readonly monthlyEquivalentAmount = monthlyEquivalentAmount;
  readonly savingsPercentAmount = savingsPercentAmount;
  readonly isPopularPlan = isPopularPlan;
  readonly showsFreeTrial = showsFreeTrial;
  readonly isSuccessfulCheckoutStatus = isSuccessfulCheckoutStatus;
  readonly formatUsage = formatUsage;
  readonly usageReached = usageReached;

  ngOnInit(): void {
    this.billing.loadSubscription().subscribe();
    this.billing.loadConfig().subscribe(config => {
      this.plans.set(config.plans || []);
      void this.paddle.ensure(config);
    });
    this.route.queryParamMap.subscribe(params => {
      if (params.get('checkout') === 'success') {
        this.toast.show('billing.checkoutSuccess');
        this.billing.loadSubscription().subscribe();
      }
    });
  }

  canManage(): boolean {
    return this.auth.hasRole('TENANT_OWNER') || this.auth.hasRole('TENANT_ADMIN');
  }

  hasActivePaddleSubscription(): boolean {
    const sub = this.subscription();
    return !!sub?.hasPaddleSubscription && (sub.status === 'ACTIVE' || sub.status === 'TRIALING' || sub.status === 'TRIAL');
  }

  subscribe(plan: BillingPlan): void {
    if (!cycleAvailable(plan, this.cycle())) {
      this.toast.show('billing.planUnavailable', true);
      return;
    }
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    if (this.hasActivePaddleSubscription()) {
      this.billing.previewChange(plan.id, this.cycle()).subscribe({
        next: preview => {
          this.preview.set(preview);
          this.busy.set(false);
        },
        error: err => {
          this.busy.set(false);
          this.toast.showHttpError(err);
        }
      });
      return;
    }
    this.billing.checkout(plan.id, this.cycle()).subscribe({
      next: session => {
        void this.paddle.openCheckout(session, () => {
          this.toast.show('billing.checkoutSuccess');
          this.refreshUntilActive();
        }).catch(() => this.toast.show('billing.checkoutError', true));
        this.busy.set(false);
      },
      error: err => {
        this.busy.set(false);
        this.toast.showHttpError(err);
      }
    });
  }

  confirmChange(): void {
    const change = this.preview();
    if (!change || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.billing.changePlan(change.newPlanId, change.newCycle as BillingCycle).subscribe({
      next: () => {
        this.busy.set(false);
        this.preview.set(null);
        this.toast.show('billing.changeSubmitted');
        this.billing.loadSubscription().subscribe();
      },
      error: err => {
        this.busy.set(false);
        this.toast.showHttpError(err);
      }
    });
  }

  openPortal(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.billing.customerPortal().subscribe({
      next: session => {
        this.busy.set(false);
        window.open(session.url, '_blank', 'noopener');
      },
      error: err => {
        this.busy.set(false);
        this.toast.showHttpError(err);
      }
    });
  }

  cancel(): void {
    if (!confirm(this.i18n.instant('billing.cancelConfirm'))) {
      return;
    }
    this.billing.cancel().subscribe({
      next: () => this.toast.show('billing.cancelScheduled'),
      error: err => this.toast.showHttpError(err)
    });
  }

  private refreshUntilActive(attempt = 0): void {
    this.billing.loadSubscription().subscribe(sub => {
      if (isSuccessfulCheckoutStatus(sub.status) || attempt >= 8) {
        return;
      }
      setTimeout(() => this.refreshUntilActive(attempt + 1), 1500);
    });
  }
}
