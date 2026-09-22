import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { MessageInboxService } from '../../../core/services/message-inbox.service';
import { PageResponse } from '../../../core/models';
import { notificationTarget, relativeTime } from '../../../core/notification-link';

interface Notice {
  id: number;
  type?: string;
  title?: string;
  body?: string;
  entityType?: string;
  entityId?: number;
  createdAt?: string;
  readAt?: string | null;
}

@Component({
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'notifications.title' | translate }}</h1>
        <p class="text-sm text-slate-500">{{ 'notifications.subtitle' | translate }}</p>
      </div>
      <button type="button" class="btn-secondary" (click)="markAll()">{{ 'notifications.markAll' | translate }}</button>
    </div>
    @if (loading()) {
      <div class="card mt-6 h-40 animate-pulse bg-slate-100 dark:bg-white/5"></div>
    } @else if (error()) {
      <p class="card mt-6 text-sm text-rose-600">{{ 'notifications.loadError' | translate }}</p>
    } @else if (!rows().length) {
      <p class="card mt-6 text-sm text-slate-500">{{ 'notifications.empty' | translate }}</p>
    } @else {
      <div class="card mt-6 divide-y divide-slate-100 p-0 dark:divide-white/10">
        @for (item of rows(); track item.id) {
          <button type="button" class="flex w-full gap-3 px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-white/5" (click)="open(item)">
            <span class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" [class.bg-brand-600]="!item.readAt" [class.bg-transparent]="!!item.readAt"></span>
            <span class="min-w-0">
              <span class="block text-sm font-medium">{{ item.title }}</span>
              @if (item.body) {
                <span class="block truncate text-sm text-slate-500">{{ item.body }}</span>
              }
              <span class="block text-xs text-slate-400">{{ when(item.createdAt) }}</span>
            </span>
          </button>
        }
      </div>
      <div class="mt-4 flex items-center justify-between text-sm">
        <button type="button" class="btn-secondary" [disabled]="page() === 0" (click)="go(page() - 1)">{{ 'audit.prev' | translate }}</button>
        <span class="text-slate-500">{{ page() + 1 }} / {{ totalPages() || 1 }}</span>
        <button type="button" class="btn-secondary" [disabled]="page() + 1 >= totalPages()" (click)="go(page() + 1)">{{ 'audit.next' | translate }}</button>
      </div>
    }
  `
})
export class NotificationsPage implements OnInit {
  private api = inject(ApiService);
  private inbox = inject(MessageInboxService);
  private router = inject(Router);
  private i18n = inject(TranslateService);
  rows = signal<Notice[]>([]);
  loading = signal(true);
  error = signal(false);
  page = signal(0);
  totalPages = signal(1);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.get<PageResponse<Notice>>('/notifications', { page: this.page(), size: 20 }).subscribe({
      next: page => {
        this.rows.set(page.content || []);
        this.totalPages.set(page.totalPages || 1);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      }
    });
  }

  go(page: number): void {
    this.page.set(page);
    this.load();
  }

  when(value?: string): string {
    return relativeTime(value, (key, params) => this.i18n.instant(key, params));
  }

  open(item: Notice): void {
    if (!item.readAt) {
      this.api.post(`/notifications/${item.id}/read`, {}).subscribe({
        next: () => this.inbox.refresh(),
        error: () => undefined
      });
      this.rows.update(rows => rows.map(row => row.id === item.id ? { ...row, readAt: new Date().toISOString() } : row));
    }
    const target = notificationTarget(item);
    if (target) {
      void this.router.navigate(target.commands, { queryParams: target.queryParams });
    }
  }

  markAll(): void {
    this.api.post('/notifications/read-all', {}).subscribe({
      next: () => {
        this.inbox.refresh();
        this.load();
      },
      error: () => undefined
    });
  }
}
