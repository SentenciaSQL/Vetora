import { AfterViewInit, Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Chart } from 'chart.js/auto';
import { ApiService } from '../../core/services/api.service';
import { ThemeService } from '../../core/services/theme.service';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { StatusBadgePipe } from '../../shared/ui/status-badge.pipe';
import { StatusLabelPipe } from '../../shared/ui/status-label.pipe';
import { statusLabel } from '../../core/team-labels';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

type RangeKey = 'TODAY' | 'LAST_7' | 'LAST_30' | 'LAST_90' | 'THIS_YEAR' | 'CUSTOM';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, StatCardComponent, StatusBadgePipe, StatusLabelPipe, EmptyStateComponent],
  template: `
    <div class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'admin.title' | translate }}</h1>
        <p class="text-sm text-slate-500">{{ 'admin.subtitle' | translate }}</p>
      </div>
      <div class="flex flex-wrap gap-2">
        @for (link of quickLinks; track link.path) {
          <a [routerLink]="link.path" [queryParams]="link.query" class="btn-secondary text-xs">{{ link.label | translate }}</a>
        }
      </div>
    </div>

    <div class="card mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-6">
      <label class="text-sm">{{ 'admin.filters.range' | translate }}
        <select class="input mt-1" [(ngModel)]="range" (ngModelChange)="load()">
          @for (option of ranges; track option) {
            <option [value]="option">{{ ('admin.filters.' + option) | translate }}</option>
          }
        </select>
      </label>
      @if (range === 'CUSTOM') {
        <label class="text-sm">{{ 'reports.from' | translate }}
          <input class="input mt-1" type="date" [(ngModel)]="customFrom" (ngModelChange)="load()" />
        </label>
        <label class="text-sm">{{ 'reports.to' | translate }}
          <input class="input mt-1" type="date" [(ngModel)]="customTo" (ngModelChange)="load()" />
        </label>
      }
      @if ((data().countries || []).length) {
        <label class="text-sm">{{ 'admin.filters.country' | translate }}
          <select class="input mt-1" [(ngModel)]="country" (ngModelChange)="load()">
            <option value="">{{ 'admin.filters.allCountries' | translate }}</option>
            @for (c of data().countries || []; track c) { <option [value]="c">{{ c }}</option> }
          </select>
        </label>
      }
      <label class="text-sm">{{ 'admin.filters.plan' | translate }}
        <select class="input mt-1" [(ngModel)]="planCode" (ngModelChange)="load()">
          <option value="">{{ 'admin.filters.allPlans' | translate }}</option>
          @for (p of data().plans || []; track p.code) { <option [value]="p.code">{{ p.name || p.code }}</option> }
        </select>
      </label>
      <label class="text-sm">{{ 'admin.filters.subscriptionStatus' | translate }}
        <select class="input mt-1" [(ngModel)]="tenantStatus" (ngModelChange)="load()">
          <option value="">{{ 'admin.filters.allStatuses' | translate }}</option>
          @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
        </select>
      </label>
    </div>

    @if (error()) {
      <div class="card mt-4 border-rose-200 text-sm text-rose-700 dark:border-rose-900 dark:text-rose-200">
        {{ 'admin.loadError' | translate }}
        <button type="button" class="btn-secondary ml-3 text-xs" (click)="load()">{{ 'common.continue' | translate }}</button>
      </div>
    }

    <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      @if (loading()) {
        @for (n of skeleton; track n) {
          <app-stat-card [label]="'common.loading' | translate" [loading]="true" />
        }
      } @else if (!(data().cards || []).length) {
        <div class="sm:col-span-2 xl:col-span-4">
          <empty-state [title]="'admin.emptyMetrics' | translate" />
        </div>
      } @else {
        @for (card of data().cards || []; track card.key) {
          <app-stat-card
            [label]="metricLabel(card.key)"
            [value]="formatCard(card)"
            [tooltip]="metricHint(card.key)"
            [hint]="card.kind === 'TOTAL' ? ('admin.currentTotal' | translate) : (card.kind === 'PERIOD' ? ('admin.periodNew' | translate) : '')"
            [changePercent]="card.changePercent"
            [trend]="card.trend"
            [unit]="card.unit || ''"
            [error]="!!error()"
            [errorText]="'common.error' | translate" />
        }
      }
    </div>

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
      <div class="card">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 class="font-medium">{{ 'admin.growthTitle' | translate }}</h2>
          <div class="flex flex-wrap gap-2 text-xs">
            @for (series of growthKeys; track series) {
              <label class="inline-flex items-center gap-1">
                <input type="checkbox" [checked]="growthOn()[series]" (change)="toggleGrowth(series)" />
                {{ ('admin.series.' + series) | translate }}
              </label>
            }
          </div>
        </div>
        @if (hasPoints(data().growth)) {
          <canvas #growthChart></canvas>
        } @else {
          <empty-state [title]="'admin.emptyChart' | translate" />
        }
      </div>
      <div class="card">
        <h2 class="mb-3 font-medium">{{ 'admin.plansTitle' | translate }}</h2>
        @if ((data().subscriptionsByPlan || []).length) {
          <canvas #plansChart></canvas>
          <div class="mt-4 overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead class="text-slate-400">
                <tr>
                  <th class="py-1">{{ 'admin.plan' | translate }}</th>
                  <th>{{ 'admin.cycle' | translate }}</th>
                  <th>{{ 'common.status' | translate }}</th>
                  <th>{{ 'admin.count' | translate }}</th>
                  <th>%</th>
                  <th>{{ 'admin.estimatedRevenue' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of data().subscriptionsByPlan || []; track row.planCode + row.billingCycle + row.statusGroup) {
                  <tr class="border-t border-slate-100 dark:border-white/10">
                    <td class="py-2">{{ row.planName }} {{ row.billingCycle === 'ANNUAL' ? ('admin.annual' | translate) : ('admin.monthly' | translate) }}</td>
                    <td>{{ row.billingCycle === 'ANNUAL' ? ('billing.annual' | translate) : ('billing.monthly' | translate) }}</td>
                    <td><span [class]="row.statusGroup | statusBadge">{{ row.statusGroup | statusLabel }}</span></td>
                    <td>{{ row.count }}</td>
                    <td>{{ row.percent }}%</td>
                    <td>
                      @if (row.statusGroup === 'ACTIVE' && row.estimated) {
                        {{ row.estimatedMonthlyRevenue | number:'1.2-2' }}
                        <span class="text-slate-400">{{ 'admin.estimated' | translate }}</span>
                      } @else { — }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <empty-state [title]="'admin.emptyChart' | translate" />
        }
      </div>
    </div>

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
      <div class="card">
        <h2 class="mb-3 font-medium">{{ 'admin.tenantsTitle' | translate }}</h2>
        @if ((data().tenantStatuses || []).length) {
          <canvas #statusChart></canvas>
        } @else {
          <empty-state [title]="'admin.emptyChart' | translate" />
        }
      </div>
      <div class="card">
        <h2 class="mb-1 font-medium">{{ 'admin.billingTitle' | translate }}</h2>
        <p class="text-xs text-slate-400">{{ data().billing?.note || data().revenueNote }}</p>
        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <div>
            <p class="text-xs text-slate-400">{{ 'admin.metrics.confirmedRevenue' | translate }}</p>
            <p class="font-display text-xl">
              @if (data().billing?.confirmedRevenue?.available) {
                {{ data().billing.confirmedRevenue.amount | number:'1.2-2' }} {{ data().billing.confirmedRevenue.currency }}
              } @else {
                {{ 'admin.unavailable' | translate }}
              }
            </p>
            <p class="text-[11px] text-slate-400">{{ 'admin.realRevenue' | translate }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-400">{{ 'admin.metrics.mrr' | translate }}</p>
            <p class="font-display text-xl">{{ data().billing?.mrr?.amount | number:'1.2-2' }} {{ data().billing?.mrr?.currency }}</p>
            <p class="text-[11px] text-amber-700">{{ 'admin.estimated' | translate }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-400">{{ 'admin.metrics.arr' | translate }}</p>
            <p class="font-display text-xl">{{ data().billing?.arr?.amount | number:'1.2-2' }} {{ data().billing?.arr?.currency }}</p>
            <p class="text-[11px] text-amber-700">{{ 'admin.estimated' | translate }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-400">{{ 'admin.metrics.averageSubscription' | translate }}</p>
            <p class="font-display text-xl">
              @if (data().billing?.averageSubscription?.available) {
                {{ data().billing.averageSubscription.amount | number:'1.2-2' }} {{ data().billing.averageSubscription.currency }}
              } @else { — }
            </p>
            <p class="text-[11px] text-amber-700">{{ 'admin.estimated' | translate }}</p>
          </div>
        </div>
        <div class="mt-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
          <p>{{ 'admin.metrics.newSubscriptions' | translate }}: <strong>{{ data().billing?.newSubscriptions || 0 }}</strong></p>
          <p>{{ 'admin.metrics.renewals' | translate }}: <strong>{{ data().billing?.renewals || 0 }}</strong></p>
          <p>{{ 'admin.metrics.cancellations' | translate }}: <strong>{{ data().billing?.cancellations || 0 }}</strong></p>
          <p>{{ 'admin.metrics.failedPayments' | translate }}: <strong>{{ data().billing?.failedPayments || 0 }}</strong></p>
          <p>{{ 'admin.metrics.recoveredPayments' | translate }}: <strong>{{ data().billing?.recoveredPayments || 0 }}</strong></p>
          <p>{{ 'admin.metrics.refunds' | translate }}: <strong>{{ data().billing?.refunds || 0 }}</strong></p>
          <p>{{ 'admin.metrics.churnRate' | translate }}: <strong>{{ rate(data().billing?.churnRate) }}</strong></p>
          <p>{{ 'admin.metrics.trialConversion' | translate }}: <strong>{{ rate(data().billing?.trialConversionRate) }}</strong></p>
        </div>
        <h3 class="mt-5 mb-2 text-sm font-medium">{{ 'admin.revenueTitle' | translate }}</h3>
        @if (hasPoints(data().billing?.revenueChart)) {
          <canvas #revenueChart></canvas>
        } @else {
          <empty-state [title]="'admin.emptyChart' | translate" />
        }
      </div>
    </div>

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
      <div class="card">
        <h2 class="mb-3 font-medium">{{ 'admin.activityTitle' | translate }}</h2>
        <div class="mb-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
          <p>{{ 'admin.metrics.appointmentsCreated' | translate }}: <strong>{{ data().activity?.appointmentsCreated || 0 }}</strong></p>
          <p>{{ 'admin.metrics.appointmentsCompleted' | translate }}: <strong>{{ data().activity?.appointmentsCompleted || 0 }}</strong></p>
          <p>{{ 'admin.metrics.appointmentsCancelled' | translate }}: <strong>{{ data().activity?.appointmentsCancelled || 0 }}</strong></p>
          <p>{{ 'admin.metrics.consultations' | translate }}: <strong>{{ data().activity?.consultations || 0 }}</strong></p>
          <p>{{ 'admin.metrics.newPets' | translate }}: <strong>{{ data().activity?.newPets || 0 }}</strong></p>
          <p>{{ 'admin.metrics.newOwners' | translate }}: <strong>{{ data().activity?.newOwners || 0 }}</strong></p>
          <p>{{ 'admin.metrics.messages' | translate }}: <strong>{{ data().activity?.messages || 0 }}</strong></p>
          <p>{{ 'admin.metrics.activeUsers' | translate }}: <strong>{{ data().activity?.activeUsers || 0 }}</strong></p>
          <p>{{ 'admin.metrics.activeTenantsPeriod' | translate }}: <strong>{{ data().activity?.activeTenants || 0 }}</strong></p>
          <p>{{ 'admin.metrics.inactiveTenants' | translate }}: <strong>{{ data().activity?.inactiveTenants || 0 }}</strong></p>
        </div>
        @if (hasPoints(data().activity?.chart)) {
          <canvas #activityChart></canvas>
        } @else {
          <empty-state [title]="'admin.emptyChart' | translate" />
        }
      </div>
      <div class="card">
        <h2 class="mb-3 font-medium">{{ 'admin.alertsTitle' | translate }}</h2>
        @if ((data().alerts || []).length === 1 && data().alerts[0].key === 'allClear') {
          <p class="text-sm text-emerald-700">{{ 'admin.alerts.allClear' | translate }}</p>
        } @else {
          <div class="space-y-2">
            @for (alert of data().alerts || []; track alert.key) {
              <a [routerLink]="alertPath(alert.href)" [queryParams]="alertQuery(alert.href)"
                 class="flex items-center justify-between rounded-xl border border-slate-100 px-3 py-2 text-sm hover:border-brand-200 dark:border-white/10">
                <span>{{ ('admin.alerts.' + alert.key) | translate }}</span>
                <span class="rounded-full px-2 py-0.5 text-xs"
                      [class.bg-rose-50]="alert.severity === 'danger'"
                      [class.text-rose-700]="alert.severity === 'danger'"
                      [class.bg-amber-50]="alert.severity === 'warning'"
                      [class.text-amber-800]="alert.severity === 'warning'"
                      [class.bg-slate-100]="alert.severity === 'info'">{{ alert.count }}</span>
              </a>
            }
          </div>
        }
      </div>
    </div>

    <div class="card mt-6 overflow-x-auto p-0">
      <div class="border-b border-slate-100 px-5 py-4 font-medium dark:border-white/10">{{ 'admin.recentTitle' | translate }}</div>
      @if (!(data().recentActivity || []).length) {
        <empty-state [title]="'admin.emptyActivity' | translate" />
      } @else {
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
            <tr>
              <th class="px-4 py-3">{{ 'admin.when' | translate }}</th>
              <th class="px-4 py-3">{{ 'admin.eventType' | translate }}</th>
              <th class="px-4 py-3">{{ 'nav.tenants' | translate }}</th>
              <th class="px-4 py-3">{{ 'admin.description' | translate }}</th>
              <th class="px-4 py-3">{{ 'common.status' | translate }}</th>
              <th class="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            @for (item of data().recentActivity || []; track item.at + item.type + (item.tenantName || '')) {
              <tr class="border-t border-slate-100 dark:border-white/5">
                <td class="px-4 py-3 whitespace-nowrap">{{ item.at | date:'short' }}</td>
                <td class="px-4 py-3">{{ item.type }}</td>
                <td class="px-4 py-3">{{ item.tenantName || '—' }}</td>
                <td class="px-4 py-3">{{ item.description }}</td>
                <td class="px-4 py-3"><span [class]="item.status | statusBadge">{{ item.status | statusLabel }}</span></td>
                <td class="px-4 py-3 text-right">
                  <a [routerLink]="item.href || '/admin/audit'" class="text-brand-700">{{ 'common.view' | translate }}</a>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `
})
export class AdminDashboardPage implements AfterViewInit, OnDestroy {
  private api = inject(ApiService);
  private i18n = inject(TranslateService);
  private theme = inject(ThemeService);
  private router = inject(Router);

  data = signal<any>({});
  loading = signal(true);
  error = signal(false);
  range: RangeKey = 'LAST_30';
  customFrom = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);
  customTo = new Date().toISOString().slice(0, 10);
  country = '';
  planCode = '';
  tenantStatus = '';
  growthOn = signal<Record<string, boolean>>({
    newTenants: true, newUsers: true, newOwners: true, newPets: true, newSubscriptions: true
  });
  ranges: RangeKey[] = ['TODAY', 'LAST_7', 'LAST_30', 'LAST_90', 'THIS_YEAR', 'CUSTOM'];
  statuses = ['ACTIVE', 'TRIAL', 'PAST_DUE', 'GRACE_PERIOD', 'SUSPENDED', 'CANCELED', 'PENDING_PAYMENT'];
  growthKeys = ['newTenants', 'newUsers', 'newOwners', 'newPets', 'newSubscriptions'];
  skeleton = [1, 2, 3, 4, 5, 6, 7, 8];
  quickLinks = [
    { path: '/admin/tenants', query: {}, label: 'nav.tenants' },
    { path: '/admin/tenants', query: { create: 1 }, label: 'admin.createTenant' },
    { path: '/admin/plans', query: {}, label: 'nav.plans' },
    { path: '/admin/subscriptions', query: {}, label: 'nav.subscriptions' },
    { path: '/admin/users', query: {}, label: 'nav.users' },
    { path: '/admin/audit', query: {}, label: 'nav.audit' },
    { path: '/reports', query: {}, label: 'nav.reports' },
    { path: '/admin/subscriptions', query: { status: 'GRACE_PERIOD' }, label: 'admin.quickFailed' }
  ];

  growthChart = viewChild<ElementRef<HTMLCanvasElement>>('growthChart');
  plansChart = viewChild<ElementRef<HTMLCanvasElement>>('plansChart');
  statusChart = viewChild<ElementRef<HTMLCanvasElement>>('statusChart');
  revenueChart = viewChild<ElementRef<HTMLCanvasElement>>('revenueChart');
  activityChart = viewChild<ElementRef<HTMLCanvasElement>>('activityChart');
  private charts: Chart[] = [];

  constructor() {
    this.load();
  }

  ngAfterViewInit(): void {
    this.renderCharts();
  }

  ngOnDestroy(): void {
    this.clearCharts();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.get('/admin/metrics', this.params()).subscribe({
      next: d => {
        this.data.set(d);
        this.loading.set(false);
        queueMicrotask(() => this.renderCharts());
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      }
    });
  }

  toggleGrowth(key: string): void {
    this.growthOn.update(current => ({ ...current, [key]: !current[key] }));
    this.renderCharts();
  }

  metricLabel(key: string): string {
    return this.i18n.instant('admin.metrics.' + key);
  }

  metricHint(key: string): string {
    return this.i18n.instant('admin.hints.' + key);
  }

  formatCard(card: any): string | number {
    if (card?.unit === 'USD') {
      return Number(card.value).toLocaleString();
    }
    return card?.value ?? 0;
  }

  rate(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return `${Math.round(value * 1000) / 10}%`;
  }

  hasPoints(chart: any): boolean {
    return !!chart?.series?.some((s: any) => (s.points || []).some((p: any) => p.value > 0));
  }

  alertPath(href: string): string {
    return (href || '/dashboard').split('?')[0];
  }

  alertQuery(href: string): Record<string, string> | null {
    const query = (href || '').split('?')[1];
    if (!query) {
      return null;
    }
    return Object.fromEntries(new URLSearchParams(query));
  }

  private params(): Record<string, string> {
    const params: Record<string, string> = { range: this.range };
    if (this.range === 'CUSTOM' && this.customFrom && this.customTo) {
      params['from'] = new Date(this.customFrom + 'T00:00:00Z').toISOString();
      params['to'] = new Date(this.customTo + 'T23:59:59Z').toISOString();
    }
    if (this.country) params['country'] = this.country;
    if (this.planCode) params['planCode'] = this.planCode;
    if (this.tenantStatus) params['tenantStatus'] = this.tenantStatus;
    return params;
  }

  private renderCharts(): void {
    this.clearCharts();
    const dark = this.theme.isDark();
    const tick = dark ? '#cbd5e1' : '#64748b';
    const grid = dark ? 'rgba(148,163,184,.15)' : 'rgba(148,163,184,.25)';
    const growth = this.data().growth;
    const growthEl = this.growthChart()?.nativeElement;
    if (growthEl && this.hasPoints(growth)) {
      const labels = growth.labels || growth.series?.[0]?.points?.map((p: any) => p.label) || [];
      const colors: Record<string, string> = {
        newTenants: '#0f766e', newUsers: '#0d9488', newOwners: '#115e59', newPets: '#2dd4bf', newSubscriptions: '#5eead4'
      };
      this.charts.push(new Chart(growthEl, {
        type: 'line',
        data: {
          labels,
          datasets: (growth.series || []).filter((s: any) => this.growthOn()[s.key]).map((s: any) => ({
            label: this.i18n.instant('admin.series.' + s.key),
            data: (s.points || []).map((p: any) => p.value),
            borderColor: colors[s.key] || '#0f766e',
            backgroundColor: (colors[s.key] || '#0f766e') + '33',
            fill: true,
            tension: 0.35
          }))
        },
        options: {
          plugins: { legend: { position: 'bottom' } },
          scales: { y: { beginAtZero: true, ticks: { color: tick }, grid: { color: grid } }, x: { ticks: { color: tick }, grid: { display: false } } }
        }
      }));
    }

    const planEl = this.plansChart()?.nativeElement;
    const plans = this.data().subscriptionsByPlan || [];
    if (planEl && plans.length) {
      this.charts.push(new Chart(planEl, {
        type: 'bar',
        data: {
          labels: plans.map((p: any) => `${p.planName} ${p.billingCycle === 'ANNUAL' ? this.i18n.instant('billing.annual') : this.i18n.instant('billing.monthly')} · ${statusLabel(this.i18n, p.statusGroup)}`),
          datasets: [{
            label: this.i18n.instant('admin.count'),
            data: plans.map((p: any) => p.count),
            backgroundColor: plans.map((p: any) => p.statusGroup === 'ACTIVE' ? '#0f766e' : p.statusGroup === 'TRIAL' ? '#5eead4' : p.statusGroup === 'PAST_DUE' ? '#f59e0b' : '#e11d48')
          }]
        },
        options: {
          plugins: { legend: { display: false } },
          scales: { y: { beginAtZero: true, ticks: { color: tick }, grid: { color: grid } }, x: { ticks: { color: tick, maxRotation: 60 }, grid: { display: false } } }
        }
      }));
    }

    const statusEl = this.statusChart()?.nativeElement;
    const statuses = this.data().tenantStatuses || [];
    if (statusEl && statuses.length) {
      this.charts.push(new Chart(statusEl, {
        type: 'doughnut',
        data: {
          labels: statuses.map((s: any) => statusLabel(this.i18n, s.status)),
          datasets: [{ data: statuses.map((s: any) => s.count), backgroundColor: ['#0f766e', '#5eead4', '#f59e0b', '#e11d48', '#115e59', '#94a3b8'] }]
        },
        options: {
          plugins: { legend: { position: 'bottom' } },
          onClick: (_evt, elements) => {
            if (elements[0]) {
              const status = statuses[elements[0].index]?.status;
              if (status) {
                void this.router.navigate(['/admin/tenants'], { queryParams: { status } });
              }
            }
          }
        }
      }));
    }

    const revenueEl = this.revenueChart()?.nativeElement;
    const revenue = this.data().billing?.revenueChart;
    if (revenueEl && this.hasPoints(revenue)) {
      const labels = revenue.labels || [];
      const palette: Record<string, string> = { confirmed: '#0f766e', refunds: '#f59e0b', failed: '#e11d48' };
      this.charts.push(new Chart(revenueEl, {
        type: 'bar',
        data: {
          labels,
          datasets: (revenue.series || []).map((s: any) => ({
            label: this.i18n.instant('admin.series.' + s.key),
            data: (s.points || []).map((p: any) => p.value),
            backgroundColor: palette[s.key] || '#0f766e'
          }))
        },
        options: {
          plugins: { legend: { position: 'bottom' } },
          scales: { y: { beginAtZero: true, ticks: { color: tick }, grid: { color: grid } }, x: { ticks: { color: tick }, grid: { display: false } } }
        }
      }));
    }

    const activityEl = this.activityChart()?.nativeElement;
    const activity = this.data().activity?.chart;
    if (activityEl && this.hasPoints(activity)) {
      const labels = activity.labels || [];
      const palette: Record<string, string> = { appointments: '#0f766e', consultations: '#115e59', messages: '#2dd4bf' };
      this.charts.push(new Chart(activityEl, {
        type: 'line',
        data: {
          labels,
          datasets: (activity.series || []).map((s: any) => ({
            label: this.i18n.instant('admin.series.' + s.key),
            data: (s.points || []).map((p: any) => p.value),
            borderColor: palette[s.key] || '#0f766e',
            backgroundColor: (palette[s.key] || '#0f766e') + '33',
            fill: true,
            tension: 0.35
          }))
        },
        options: {
          plugins: { legend: { position: 'bottom' } },
          scales: { y: { beginAtZero: true, ticks: { color: tick }, grid: { color: grid } }, x: { ticks: { color: tick }, grid: { display: false } } }
        }
      }));
    }
  }

  private clearCharts(): void {
    this.charts.forEach(chart => chart.destroy());
    this.charts = [];
  }
}
