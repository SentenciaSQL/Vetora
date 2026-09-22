import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { StatusBadgePipe } from '../../shared/ui/status-badge.pipe';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

interface AdminSubscription {
  id: number;
  status: string;
  trial: boolean;
  startedAt: string | null;
  currentPeriodEnd: string | null;
  cancelledAt: string | null;
  canceledAt: string | null;
  billingCycle: string | null;
  currency: string | null;
  gracePeriodEndsAt: string | null;
  suspendedAt: string | null;
  tenantName: string;
  planCode: string;
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, StatusBadgePipe, EmptyStateComponent],
  template: `
    <div>
      <h1 class="font-display text-2xl font-semibold">{{ 'nav.subscriptions' | translate }}</h1>
      <p class="mt-1 text-sm text-slate-500">{{ 'admin.subscriptionsSubtitle' | translate }}</p>
    </div>

    <div class="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      @for (card of summary(); track card.status) {
        <button
          type="button"
          class="card text-left transition hover:border-brand-300 dark:hover:border-brand-700"
          [class.ring-2]="status() === card.status"
          [class.ring-brand-500]="status() === card.status"
          [attr.aria-pressed]="status() === card.status"
          (click)="setStatus(card.status)">
          <p class="text-sm text-slate-500">{{ card.label | translate }}</p>
          @if (loading()) {
            <div class="mt-3 h-8 w-12 animate-pulse rounded-lg bg-slate-100 dark:bg-white/10"></div>
          } @else if (error()) {
            <p class="mt-1 font-display text-3xl font-semibold text-slate-300">—</p>
          } @else {
            <p class="mt-1 font-display text-3xl font-semibold text-slate-900 dark:text-white">{{ card.value }}</p>
          }
        </button>
      }
    </div>

    <div class="card mt-6 overflow-hidden p-0">
      <div class="flex flex-wrap items-end gap-3 border-b border-slate-100 px-4 py-4 dark:border-white/5">
        <div class="min-w-52 flex-1">
          <label class="block text-sm font-medium" for="subscription-search">{{ 'common.search' | translate }}</label>
          <input
            id="subscription-search"
            class="input mt-1"
            [ngModel]="query()"
            (ngModelChange)="query.set($event)"
            [placeholder]="'admin.searchSubscriptions' | translate" />
        </div>
        <div class="w-full sm:w-56">
          <label class="block text-sm font-medium" for="subscription-status">{{ 'common.status' | translate }}</label>
          <select id="subscription-status" class="input mt-1" [ngModel]="status()" (ngModelChange)="setStatus($event)">
            <option value="">{{ 'common.all' | translate }}</option>
            @for (s of statuses; track s) {
              <option [value]="s">{{ ('billing.status.' + s) | translate }}</option>
            }
          </select>
        </div>
        @if (!loading() && !error()) {
          <p class="pb-2 text-xs text-slate-500">
            {{ 'admin.subscriptionsCount' | translate:{ count: filtered().length, total: items().length } }}
          </p>
        }
      </div>

      @if (error()) {
        <div class="px-6 py-10 text-center">
          <p class="text-sm text-rose-700 dark:text-rose-200">{{ 'admin.subscriptionsError' | translate }}</p>
          <button type="button" class="btn-secondary mt-4" (click)="load()">{{ 'admin.subscriptionsRetry' | translate }}</button>
        </div>
      } @else if (loading()) {
        <div class="space-y-3 p-4">
          @for (n of skeletons; track n) {
            <div class="h-12 animate-pulse rounded-xl bg-slate-100 dark:bg-white/5"></div>
          }
        </div>
      } @else if (!filtered().length) {
        <empty-state
          [title]="(items().length ? 'admin.subscriptionsNoMatch' : 'admin.subscriptionsEmpty') | translate" />
      } @else {
        <div class="overflow-x-auto">
          <table class="min-w-full text-sm">
            <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
              <tr>
                <th class="px-4 py-3">{{ 'admin.clinic' | translate }}</th>
                <th class="px-4 py-3">{{ 'admin.plan' | translate }}</th>
                <th class="px-4 py-3">{{ 'common.status' | translate }}</th>
                <th class="px-4 py-3">{{ 'admin.cycle' | translate }}</th>
                <th class="px-4 py-3">{{ 'admin.currency' | translate }}</th>
                <th class="px-4 py-3">{{ 'billing.periodEnd' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (s of filtered(); track s.id) {
                <tr class="border-t border-slate-100 dark:border-white/5">
                  <td class="px-4 py-3">
                    <p class="font-medium text-slate-900 dark:text-white">{{ s.tenantName }}</p>
                    <p class="mt-0.5 text-xs text-slate-500">{{ 'admin.startedAt' | translate }} · {{ s.startedAt | date:'mediumDate' }}</p>
                  </td>
                  <td class="px-4 py-3">
                    <span class="rounded-lg bg-slate-100 px-2 py-1 text-xs font-semibold tracking-wide text-slate-700 dark:bg-white/10 dark:text-slate-200">{{ s.planCode }}</span>
                  </td>
                  <td class="px-4 py-3">
                    <span [class]="s.status | statusBadge">{{ ('billing.status.' + s.status) | translate }}</span>
                    @if (s.trial && s.status !== 'TRIAL') {
                      <span class="badge ml-1 bg-sky-50 text-sky-800 dark:bg-sky-500/10 dark:text-sky-200">{{ 'billing.status.TRIAL' | translate }}</span>
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-700 dark:text-slate-200">
                    @if (s.billingCycle === 'MONTHLY') {
                      {{ 'billing.monthly' | translate }}
                    } @else if (s.billingCycle === 'ANNUAL') {
                      {{ 'billing.annual' | translate }}
                    } @else {
                      {{ s.billingCycle || '—' }}
                    }
                  </td>
                  <td class="px-4 py-3 text-slate-500">{{ s.currency || '—' }}</td>
                  <td class="px-4 py-3">
                    <p>{{ s.currentPeriodEnd ? (s.currentPeriodEnd | date:'mediumDate') : '—' }}</p>
                    @if (s.gracePeriodEndsAt) {
                      <p class="mt-0.5 text-xs text-amber-700 dark:text-amber-200">{{ 'billing.graceTitle' | translate }} · {{ s.gracePeriodEndsAt | date:'mediumDate' }}</p>
                    }
                    @if (s.suspendedAt) {
                      <p class="mt-0.5 text-xs text-rose-700 dark:text-rose-200">{{ 'billing.suspendedOn' | translate }} · {{ s.suspendedAt | date:'mediumDate' }}</p>
                    }
                    @if (canceledAt(s)) {
                      <p class="mt-0.5 text-xs text-slate-500">{{ 'billing.cancelsOn' | translate }} · {{ canceledAt(s) | date:'mediumDate' }}</p>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class AdminSubscriptionsPage implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  items = signal<AdminSubscription[]>([]);
  status = signal('');
  query = signal('');
  loading = signal(true);
  error = signal(false);
  statuses = ['ACTIVE', 'TRIAL', 'PAST_DUE', 'GRACE_PERIOD', 'CANCELED', 'SUSPENDED', 'PENDING'];
  skeletons = [1, 2, 3, 4];

  filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const status = this.status().toUpperCase();
    return this.items().filter(row => {
      if (status && !this.matchesStatus(row.status, status)) {
        return false;
      }
      if (!q) {
        return true;
      }
      return (row.tenantName || '').toLowerCase().includes(q)
        || (row.planCode || '').toLowerCase().includes(q);
    });
  });

  summary = computed(() => {
    const all = this.items();
    const count = (code: string) => all.filter(row => row.status === code).length;
    return [
      { status: '', label: 'admin.totalSubscriptions', value: all.length },
      { status: 'ACTIVE', label: 'admin.subscriptionsActive', value: count('ACTIVE') },
      { status: 'TRIAL', label: 'admin.subscriptionsTrial', value: count('TRIAL') },
      { status: 'PAST_DUE', label: 'admin.subscriptionsAttention', value: count('PAST_DUE') + count('GRACE_PERIOD') },
      { status: 'SUSPENDED', label: 'admin.subscriptionsSuspended', value: count('SUSPENDED') }
    ];
  });

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      this.status.set(params.get('status') || '');
    });
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set(false);
    this.api.get<AdminSubscription[]>('/admin/subscriptions').subscribe({
      next: rows => {
        this.items.set(rows || []);
        this.loading.set(false);
      },
      error: () => {
        this.items.set([]);
        this.error.set(true);
        this.loading.set(false);
      }
    });
  }

  setStatus(status: string) {
    this.status.set(status);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: status || null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  canceledAt(row: AdminSubscription): string | null {
    return row.canceledAt || row.cancelledAt || null;
  }

  private matchesStatus(rowStatus: string, selected: string): boolean {
    if (selected === 'PAST_DUE') {
      return rowStatus === 'PAST_DUE' || rowStatus === 'GRACE_PERIOD';
    }
    return rowStatus === selected;
  }
}
