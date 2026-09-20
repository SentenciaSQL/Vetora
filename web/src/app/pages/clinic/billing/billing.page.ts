import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { BillingService } from '../../../core/services/billing.service';
import { PaddleService } from '../../../core/services/paddle.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/services/auth.service';
import { BillingPlan } from '../../../core/models';
import { StatusBadgePipe } from '../../../shared/ui/status-badge.pipe';

@Component({
  standalone: true,
  imports: [CommonModule, TranslatePipe, StatusBadgePipe],
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
        @if (sub.scheduledChangeEffectiveAt) {
          <p class="text-sm text-amber-700">{{ 'billing.cancelsOn' | translate }}: {{ sub.scheduledChangeEffectiveAt | date:'mediumDate' }}</p>
        }
      }
      <div class="flex flex-wrap gap-3">
        @if (canManage() && subscription()?.hasPaddleCustomer) {
          <button type="button" class="btn-secondary" (click)="openPortal()">{{ 'billing.portal' | translate }}</button>
        }
        @if (canManage() && subscription()?.status === 'ACTIVE' && !subscription()?.scheduledChangeEffectiveAt) {
          <button type="button" class="btn-secondary" (click)="cancel()">{{ 'billing.cancel' | translate }}</button>
        }
      </div>
    </section>

    <section class="mt-8">
      <h2 class="font-display text-xl font-semibold">{{ 'billing.plans' | translate }}</h2>
      <div class="mt-4 grid gap-4 md:grid-cols-3">
        @for (plan of plans(); track plan.id) {
          <article class="card space-y-3">
            <p class="text-xs uppercase tracking-wide text-slate-400">{{ plan.code }}</p>
            <h3 class="font-display text-xl font-semibold">{{ plan.name }}</h3>
            <p class="text-sm text-slate-500">{{ plan.description }}</p>
            <p class="text-3xl font-semibold">{{ plan.monthlyPrice | number:'1.2-2' }} {{ plan.currency }}</p>
            <p class="text-xs text-slate-400">{{ 'billing.perMonth' | translate }}</p>
            <ul class="text-sm text-slate-600 dark:text-slate-300">
              <li>{{ 'admin.users' | translate }}: {{ plan.limits.maxUsers }}</li>
              <li>{{ 'nav.team' | translate }}: {{ plan.limits.maxVeterinarians }}</li>
              <li>{{ 'nav.branches' | translate }}: {{ plan.limits.maxBranches }}</li>
            </ul>
            @if (canManage() && plan.paddleMonthlyPriceId) {
              <button type="button" class="btn-primary w-full" [disabled]="busy()" (click)="subscribe(plan)">
                {{ 'billing.subscribe' | translate }}
              </button>
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
    return this.auth.hasRole('TENANT_ADMIN');
  }

  subscribe(plan: BillingPlan): void {
    if (!plan.paddleMonthlyPriceId) {
      this.toast.show('billing.planUnavailable', true);
      return;
    }
    this.busy.set(true);
    this.billing.checkout(plan.paddleMonthlyPriceId, 'MONTHLY').subscribe({
      next: session => {
        void this.paddle.openCheckout(session, () => {
          this.toast.show('billing.checkoutSuccess');
          this.refreshUntilActive();
        }).catch(() => this.toast.show('billing.checkoutError', true));
        this.busy.set(false);
      },
      error: () => {
        this.busy.set(false);
        this.toast.show('common.error', true);
      }
    });
  }

  openPortal(): void {
    this.billing.customerPortal().subscribe({
      next: session => window.open(session.url, '_blank', 'noopener'),
      error: () => this.toast.show('common.error', true)
    });
  }

  cancel(): void {
    if (!confirm(this.i18n.instant('billing.cancelConfirm'))) {
      return;
    }
    this.billing.cancel().subscribe({
      next: () => this.toast.show('billing.cancelScheduled'),
      error: () => this.toast.show('common.error', true)
    });
  }

  private refreshUntilActive(attempt = 0): void {
    this.billing.loadSubscription().subscribe(sub => {
      if (sub.status === 'ACTIVE' || attempt >= 8) {
        return;
      }
      setTimeout(() => this.refreshUntilActive(attempt + 1), 1500);
    });
  }
}
