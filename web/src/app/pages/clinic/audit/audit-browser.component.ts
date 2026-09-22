import { DatePipe } from '@angular/common';
import { Component, inject, input, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ApiService } from '../../../core/services/api.service';
import { PageResponse } from '../../../core/models';

interface AuditRow {
  id: number;
  username?: string;
  userName?: string;
  action?: string;
  entityType?: string;
  entityId?: number;
  details?: string;
  oldValue?: string | null;
  newValue?: string | null;
  ipAddress?: string | null;
  createdAt?: string;
}

interface AuditUser {
  id: number;
  name?: string;
  email?: string;
}

interface AuditFilters {
  actions: string[];
  entities: string[];
  users: AuditUser[];
}

@Component({
  selector: 'app-audit-browser',
  standalone: true,
  imports: [DatePipe, FormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'nav.audit' | translate }}</h1>
    <p class="mt-1 text-sm text-slate-500">{{ (admin() ? 'audit.adminSubtitle' : 'audit.subtitle') | translate }}</p>

    <div class="mt-4 flex flex-wrap items-center gap-2">
      <label class="sr-only" for="audit-search">{{ 'audit.search' | translate }}</label>
      <input id="audit-search" class="input h-9 min-w-48 flex-1 py-1.5" [ngModel]="query" (ngModelChange)="onQuery($event)" [placeholder]="'audit.searchPlaceholder' | translate" />
      <select class="h-9 max-w-36 rounded-xl border border-slate-200 bg-white px-2 text-xs dark:border-slate-700 dark:bg-slate-950" [ngModel]="action" (ngModelChange)="setAction($event)" [attr.aria-label]="'audit.action' | translate">
        <option value="">{{ 'audit.allActions' | translate }}</option>
        @for (item of filters().actions; track item) {
          <option [value]="item">{{ actionLabel(item) }}</option>
        }
      </select>
      <select class="h-9 max-w-36 rounded-xl border border-slate-200 bg-white px-2 text-xs dark:border-slate-700 dark:bg-slate-950" [ngModel]="entity" (ngModelChange)="setEntity($event)" [attr.aria-label]="'audit.entity' | translate">
        <option value="">{{ 'audit.allEntities' | translate }}</option>
        @for (item of filters().entities; track item) {
          <option [value]="item">{{ entityLabel(item) }}</option>
        }
      </select>
      <select class="h-9 max-w-40 rounded-xl border border-slate-200 bg-white px-2 text-xs dark:border-slate-700 dark:bg-slate-950" [ngModel]="userId" (ngModelChange)="setUser($event)" [attr.aria-label]="'audit.user' | translate">
        <option value="">{{ 'audit.allUsers' | translate }}</option>
        @for (item of filters().users; track item.id) {
          <option [value]="item.id">{{ item.name || item.email }}</option>
        }
      </select>
      <input class="h-9 w-36 rounded-xl border border-slate-200 bg-white px-2 text-xs dark:border-slate-700 dark:bg-slate-950" type="date" [ngModel]="from" (ngModelChange)="setFrom($event)" [attr.aria-label]="'audit.from' | translate" />
      <input class="h-9 w-36 rounded-xl border border-slate-200 bg-white px-2 text-xs dark:border-slate-700 dark:bg-slate-950" type="date" [ngModel]="to" (ngModelChange)="setTo($event)" [attr.aria-label]="'audit.to' | translate" />
      @if (hasCriteria()) {
        <button type="button" class="btn-secondary h-9 px-3 py-0 text-xs" (click)="clear()">{{ 'audit.clear' | translate }}</button>
      }
    </div>

    @if (loading()) {
      <div class="card mt-6 h-48 animate-pulse bg-slate-100 dark:bg-white/5"></div>
    } @else if (failed()) {
      <p class="card mt-6 text-sm text-rose-600">{{ 'audit.error' | translate }}</p>
    } @else if (!rows().length) {
      <div class="card mt-6 space-y-3 text-sm text-slate-500">
        <p>{{ (hasCriteria() ? 'audit.noResults' : 'audit.empty') | translate }}</p>
        @if (hasCriteria()) {
          <button type="button" class="btn-secondary" (click)="clear()">{{ 'audit.clear' | translate }}</button>
        }
      </div>
    } @else {
      <div class="card mt-6 overflow-x-auto p-0">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
            <tr>
              <th class="px-4 py-3"><button type="button" (click)="sortBy('createdAt')">{{ 'audit.when' | translate }}</button></th>
              <th class="px-4 py-3"><button type="button" (click)="sortBy('username')">{{ 'audit.user' | translate }}</button></th>
              <th class="px-4 py-3"><button type="button" (click)="sortBy('action')">{{ 'audit.action' | translate }}</button></th>
              <th class="px-4 py-3">{{ 'audit.entity' | translate }}</th>
              <th class="px-4 py-3">{{ 'audit.detail' | translate }}</th>
              <th class="px-4 py-3">{{ 'audit.ip' | translate }}</th>
              <th class="px-4 py-3 text-right">{{ 'common.actions' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.id) {
              <tr class="border-t border-slate-100 dark:border-white/5">
                <td class="px-4 py-3 whitespace-nowrap">{{ row.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
                <td class="px-4 py-3">
                  <p class="font-medium">{{ row.userName || row.username }}</p>
                  @if (row.username && row.userName && row.username !== row.userName) {
                    <p class="text-xs text-slate-400">{{ row.username }}</p>
                  }
                </td>
                <td class="px-4 py-3">{{ actionLabel(row.action) }}</td>
                <td class="px-4 py-3">{{ entityLabel(row.entityType) }}</td>
                <td class="px-4 py-3 max-w-xs truncate">{{ row.details || '—' }}</td>
                <td class="px-4 py-3 text-slate-500">{{ row.ipAddress || '—' }}</td>
                <td class="px-4 py-3 text-right">
                  <button type="button" class="btn-secondary text-xs" (click)="detail.set(row)">{{ 'audit.view' | translate }}</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm">
        <p class="text-slate-500">{{ 'audit.showing' | translate:{ from: rangeFrom(), to: rangeTo(), total: total() } }}</p>
        <label class="flex items-center gap-2 text-slate-500">
          {{ 'audit.pageSize' | translate }}
          <select class="input w-auto" [ngModel]="size()" (ngModelChange)="setSize($event)">
            @for (option of sizes; track option) {
              <option [ngValue]="option">{{ option }}</option>
            }
          </select>
        </label>
      </div>
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <button type="button" class="btn-secondary" [disabled]="page() === 0" (click)="go(page() - 1)">{{ 'audit.prev' | translate }}</button>
        @for (item of pages(); track item) {
          @if (item === -1) {
            <span class="px-1 text-slate-400">…</span>
          } @else {
            <button type="button" class="h-9 min-w-9 rounded-xl px-2 text-sm" [class.bg-brand-700]="item === page()" [class.text-white]="item === page()" [class.btn-secondary]="item !== page()" (click)="go(item)">{{ item + 1 }}</button>
          }
        }
        <button type="button" class="btn-secondary" [disabled]="page() + 1 >= totalPages()" (click)="go(page() + 1)">{{ 'audit.next' | translate }}</button>
      </div>
    }

    @if (detail()) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="detail.set(null)">
        <div class="card max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto" (click)="$event.stopPropagation()">
          <h2 class="font-display text-lg font-semibold">{{ 'audit.detailTitle' | translate }}</h2>
          <dl class="space-y-2 text-sm">
            <div><dt class="text-slate-500">{{ 'audit.when' | translate }}</dt><dd>{{ detail()!.createdAt | date:'dd/MM/yyyy HH:mm' }}</dd></div>
            <div><dt class="text-slate-500">{{ 'audit.user' | translate }}</dt><dd>{{ detail()!.userName || detail()!.username }}</dd></div>
            <div><dt class="text-slate-500">{{ 'audit.email' | translate }}</dt><dd>{{ detail()!.username || '—' }}</dd></div>
            <div><dt class="text-slate-500">{{ 'audit.action' | translate }}</dt><dd>{{ actionLabel(detail()!.action) }}</dd></div>
            <div><dt class="text-slate-500">{{ 'audit.entity' | translate }}</dt><dd>{{ entityLabel(detail()!.entityType) }}</dd></div>
            <div><dt class="text-slate-500">{{ 'audit.record' | translate }}</dt><dd>{{ detail()!.details || '—' }} @if (detail()!.entityId) { #{{ detail()!.entityId }} }</dd></div>
            <div><dt class="text-slate-500">{{ 'audit.ip' | translate }}</dt><dd>{{ detail()!.ipAddress || '—' }}</dd></div>
          </dl>
          @if (detail()!.oldValue || detail()!.newValue) {
            <div class="rounded-xl bg-slate-50 p-3 text-sm dark:bg-white/5">
              <p class="font-medium">{{ 'audit.changes' | translate }}</p>
              <p class="mt-1 text-slate-600 dark:text-slate-300">{{ detail()!.oldValue || '—' }} → {{ detail()!.newValue || '—' }}</p>
            </div>
          }
          <div class="flex justify-end">
            <button type="button" class="btn-secondary" (click)="detail.set(null)">{{ 'common.close' | translate }}</button>
          </div>
        </div>
      </div>
    }
  `
})
export class AuditBrowserComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private i18n = inject(TranslateService);
  admin = input(false);
  rows = signal<AuditRow[]>([]);
  filters = signal<AuditFilters>({ actions: [], entities: [], users: [] });
  loading = signal(true);
  failed = signal(false);
  page = signal(0);
  size = signal(20);
  total = signal(0);
  totalPages = signal(0);
  detail = signal<AuditRow | null>(null);
  query = '';
  action = '';
  entity = '';
  userId = '';
  from = '';
  to = '';
  sort = 'createdAt';
  direction = 'desc';
  sizes = [10, 20, 50, 100];
  private search$ = new Subject<string>();
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.search$.pipe(debounceTime(400), distinctUntilChanged()).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    this.api.get<AuditFilters>(this.base() + '/filters').subscribe({
      next: value => this.filters.set(value),
      error: () => undefined
    });
    this.load();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onQuery(value: string): void {
    this.query = value;
    this.search$.next(value.trim());
  }

  setAction(value: string): void { this.action = value; this.reset(); }
  setEntity(value: string): void { this.entity = value; this.reset(); }
  setUser(value: string): void { this.userId = value; this.reset(); }
  setFrom(value: string): void { this.from = value; this.reset(); }
  setTo(value: string): void { this.to = value; this.reset(); }

  setSize(value: number): void {
    this.size.set(Number(value));
    this.reset();
  }

  sortBy(field: string): void {
    if (this.sort === field) {
      this.direction = this.direction === 'asc' ? 'desc' : 'asc';
    } else {
      this.sort = field;
      this.direction = field === 'createdAt' ? 'desc' : 'asc';
    }
    this.reset();
  }

  go(page: number): void {
    this.page.set(page);
    this.load();
  }

  clear(): void {
    this.query = '';
    this.action = '';
    this.entity = '';
    this.userId = '';
    this.from = '';
    this.to = '';
    this.sort = 'createdAt';
    this.direction = 'desc';
    this.reset();
  }

  hasCriteria(): boolean {
    return !!(this.query.trim() || this.action || this.entity || this.userId || this.from || this.to);
  }

  rangeFrom(): number {
    if (!this.total()) return 0;
    return this.page() * this.size() + 1;
  }

  rangeTo(): number {
    return Math.min(this.total(), (this.page() + 1) * this.size());
  }

  pages(): number[] {
    const total = this.totalPages();
    const current = this.page();
    if (total <= 7) {
      return Array.from({ length: total }, (_, index) => index);
    }
    const items = new Set<number>([0, total - 1, current - 1, current, current + 1]);
    const sorted = [...items].filter(item => item >= 0 && item < total).sort((a, b) => a - b);
    const result: number[] = [];
    sorted.forEach((item, index) => {
      if (index > 0 && item - sorted[index - 1] > 1) {
        result.push(-1);
      }
      result.push(item);
    });
    return result;
  }

  actionLabel(code?: string): string {
    return this.coded('audit.actions', code);
  }

  entityLabel(code?: string): string {
    return this.coded('audit.entities', code);
  }

  private reset(): void {
    this.page.set(0);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.get<PageResponse<AuditRow>>(this.base(), {
      page: this.page(),
      size: this.size(),
      search: this.query.trim(),
      action: this.action,
      entityType: this.entity,
      userId: this.userId,
      from: this.from,
      to: this.to,
      sort: this.sort,
      direction: this.direction
    }).subscribe({
      next: page => {
        this.rows.set(page.content || []);
        this.total.set(page.totalElements || 0);
        this.totalPages.set(page.totalPages || 0);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      }
    });
  }

  private base(): string {
    return this.admin() ? '/admin/audit' : '/audit';
  }

  private coded(prefix: string, code?: string): string {
    if (!code) return '—';
    const key = `${prefix}.${code}`;
    const value = this.i18n.instant(key);
    return value === key ? code : value;
  }
}
