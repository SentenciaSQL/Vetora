import { Component, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged, filter, Subject } from 'rxjs';
import { AuthService } from '../core/services/auth.service';
import { BrandingService } from '../core/services/branding.service';
import { BillingService } from '../core/services/billing.service';
import { MessageInboxService } from '../core/services/message-inbox.service';
import { ApiService } from '../core/services/api.service';
import { SearchResult } from '../core/models';
import { BrandMarkComponent } from '../shared/ui/brand-mark.component';
import { LanguageSelectorComponent } from '../shared/ui/language-selector.component';
import { ThemeSelectorComponent } from '../shared/ui/theme-selector.component';
import { NavIconComponent } from '../shared/ui/nav-icon.component';
import { RoleLabelPipe } from '../shared/ui/role-label.pipe';
import { UserAvatarComponent } from '../shared/ui/user-avatar.component';
import { TranslateService } from '@ngx-translate/core';
import { notificationTarget, relativeTime } from '../core/notification-link';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles?: string[];
  permission?: string;
}

@Component({
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, FormsModule, TranslatePipe, DatePipe,
    BrandMarkComponent, LanguageSelectorComponent, ThemeSelectorComponent, NavIconComponent,
    RoleLabelPipe, UserAvatarComponent
  ],
  template: `
    <div class="flex min-h-screen bg-sand-50 dark:bg-slate-950">
      <aside class="sticky top-0 hidden h-screen shrink-0 flex-col border-r border-slate-200/80 bg-white/90 backdrop-blur dark:border-white/10 dark:bg-slate-900/90 lg:flex"
             [class.w-64]="!collapsed()" [class.w-[4.5rem]]="collapsed()">
        <div class="flex items-center justify-between px-4 py-5">
          <app-brand-mark [showName]="!collapsed()" />
          <button type="button" class="rounded-lg p-1 text-slate-400 hover:bg-slate-100 dark:hover:bg-white/10" (click)="collapsed.set(!collapsed())"
                  [attr.aria-label]="(collapsed() ? 'shell.expand' : 'shell.collapse') | translate">
            ☰
          </button>
        </div>
        <nav class="flex-1 space-y-1 px-3">
          @for (item of visibleNav(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="bg-brand-50 text-brand-800 dark:bg-brand-900/40 dark:text-brand-100"
               class="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-white/5">
              <span class="grid h-8 w-8 place-items-center rounded-lg bg-slate-100 text-brand-700 dark:bg-white/10 dark:text-brand-100">
                <app-nav-icon [name]="item.icon" />
              </span>
              @if (!collapsed()) {
                <span class="min-w-0 flex-1 truncate">{{ item.label | translate }}</span>
              }
              @if (item.path === '/messages' && inbox.unreadMessages() > 0) {
                <span class="ml-auto grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] font-semibold text-white"
                      [attr.aria-label]="inbox.unreadMessages() + ' ' + ('messages.unreadShort' | translate)">
                  {{ inbox.badge(inbox.unreadMessages()) }}
                </span>
              }
            </a>
          }
        </nav>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="sticky top-0 z-30 flex items-center gap-3 border-b border-slate-200/80 bg-white/80 px-4 py-3 backdrop-blur dark:border-white/10 dark:bg-slate-900/80">
          <button type="button" class="rounded-xl p-2 lg:hidden" (click)="mobileOpen.set(!mobileOpen())" aria-label="Menu">☰</button>
          <div class="lg:hidden"><app-brand-mark [showName]="false" /></div>
          <div class="relative min-w-0 flex-1">
            <label class="sr-only" for="global-search">{{ 'common.search' | translate }}</label>
            <input id="global-search" class="input" [(ngModel)]="query" (ngModelChange)="onQuery($event)"
                   [placeholder]="'shell.searchPlaceholder' | translate" autocomplete="off" />
            @if (results()) {
              <div class="absolute z-40 mt-2 w-full overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl dark:border-white/10 dark:bg-slate-900">
                @if (!hasResults()) {
                  <p class="px-4 py-3 text-sm text-slate-500">{{ 'shell.noResults' | translate }}</p>
                } @else {
                  @for (group of groups; track group.key) {
                    @if ($any(results())[group.key]?.length) {
                      <p class="px-4 pt-3 text-xs font-semibold uppercase tracking-wide text-slate-400">{{ group.label | translate }}</p>
                      @for (item of $any(results())[group.key]; track item.id) {
                        <a [routerLink]="group.key === 'owners' ? ['/pets'] : group.path + item.id"
                           [queryParams]="group.key === 'owners' ? { owner: item.id } : null"
                           (click)="results.set(null)" class="block px-4 py-2 text-sm hover:bg-slate-50 dark:hover:bg-white/5">
                          {{ item.name }} <span class="text-slate-400">{{ item.owner || item.email || item.specialty }}</span>
                        </a>
                      }
                    }
                  }
                }
              </div>
            }
          </div>
          <div class="relative" data-menu>
            <button type="button" class="relative grid h-10 w-10 place-items-center rounded-xl border border-slate-200 text-slate-600 dark:border-slate-700 dark:text-slate-100"
                    (click)="toggleNotes()" [attr.aria-label]="'nav.notifications' | translate" [attr.title]="'nav.notifications' | translate">
              <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <path d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2c0 .5-.2 1-.6 1.4L4 17h5" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M9 17a3 3 0 0 0 6 0" stroke-linecap="round"/>
              </svg>
              @if (inbox.unreadNotifications() > 0) {
                <span class="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] font-semibold text-white"
                      [attr.aria-label]="inbox.unreadNotifications() + ' ' + ('nav.notifications' | translate)">{{ inbox.badge(inbox.unreadNotifications()) }}</span>
              }
            </button>
            @if (notesOpen()) {
              <div class="absolute right-0 z-40 mt-2 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl dark:border-white/10 dark:bg-slate-900">
                <div class="flex items-center justify-between px-4 py-3">
                  <p class="text-sm font-semibold">{{ 'notifications.title' | translate }}</p>
                  <button type="button" class="text-xs text-brand-700 dark:text-brand-200" (click)="markAllNotes()">{{ 'notifications.markRead' | translate }}</button>
                </div>
                <div class="max-h-80 divide-y divide-slate-100 overflow-y-auto dark:divide-white/10">
                  @if (!notes().length) {
                    <p class="px-4 py-6 text-sm text-slate-500">{{ 'notifications.empty' | translate }}</p>
                  }
                  @for (n of notes(); track n.id) {
                    <button type="button" class="flex w-full gap-2 px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-white/5" (click)="readNote(n)">
                      <span class="mt-1 h-2 w-2 shrink-0 rounded-full" [class.bg-brand-600]="!n.readAt" [class.bg-slate-200]="!!n.readAt"></span>
                      <span class="min-w-0">
                        <span class="block text-sm font-medium">{{ n.title || n.titleEs }}</span>
                        @if (n.body) { <span class="block truncate text-xs text-slate-500">{{ n.body }}</span> }
                        <span class="block text-xs text-slate-400">{{ when(n.createdAt) }}</span>
                      </span>
                    </button>
                  }
                </div>
                <a routerLink="/notifications" (click)="notesOpen.set(false)" class="block px-4 py-3 text-center text-sm text-brand-700 dark:text-brand-200">
                  {{ 'notifications.viewAll' | translate }}
                </a>
              </div>
            }
          </div>
          <app-language-selector />
          <app-theme-selector />
          @if ((auth.user()?.memberships?.length || 0) > 1) {
            <select class="input w-40 text-xs" [value]="auth.user()?.tenantSlug || ''" (change)="switchClinic($any($event.target).value)">
              @for (m of auth.user()?.memberships || []; track m.slug) {
                <option [value]="m.slug">{{ m.name }}</option>
              }
            </select>
          }
          <div class="relative" data-menu>
            <button type="button" class="flex items-center gap-2 rounded-xl px-1 py-1 hover:bg-slate-50 dark:hover:bg-white/5" (click)="toggleUser()">
              <app-user-avatar class="h-9 w-9" [url]="auth.user()?.avatarUrl" [name]="auth.user()?.fullName" />
              <span class="hidden text-left sm:block">
                <span class="block text-sm font-medium leading-tight">{{ auth.user()?.fullName }}</span>
                <span class="block text-xs text-slate-400">{{ (auth.user()?.role || auth.user()?.roles?.[0]) | roleLabel }}</span>
              </span>
              <span class="hidden text-slate-400 sm:inline" aria-hidden="true">▼</span>
            </button>
            @if (userMenuOpen()) {
              <div class="absolute right-0 z-40 mt-2 w-72 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl dark:border-white/10 dark:bg-slate-900">
                <div class="flex items-center gap-3 px-4 py-3">
                  <app-user-avatar class="h-10 w-10" [url]="auth.user()?.avatarUrl" [name]="auth.user()?.fullName" />
                  <div class="min-w-0">
                    <p class="truncate text-sm font-medium">{{ auth.user()?.fullName }}</p>
                    <p class="text-xs text-slate-400">{{ (auth.user()?.role || auth.user()?.roles?.[0]) | roleLabel }}</p>
                    <p class="truncate text-xs text-slate-500">{{ auth.user()?.email }}</p>
                  </div>
                </div>
                <div class="border-t border-slate-100 py-1 dark:border-white/10">
                  <a routerLink="/profile" class="block px-4 py-2 text-sm hover:bg-slate-50 dark:hover:bg-white/5" (click)="userMenuOpen.set(false)">{{ 'nav.profile' | translate }}</a>
                  @if (auth.canManageSettings()) {
                    <a routerLink="/settings" class="block px-4 py-2 text-sm hover:bg-slate-50 dark:hover:bg-white/5" (click)="userMenuOpen.set(false)">{{ 'nav.settings' | translate }}</a>
                  }
                </div>
                <div class="border-t border-slate-100 py-1 dark:border-white/10">
                  <button type="button" class="block w-full px-4 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" (click)="auth.logout()">{{ 'nav.logout' | translate }}</button>
                </div>
              </div>
            }
          </div>
        </header>

        @if (billing.isGracePeriod() && !billing.isSuspended()) {
          <a routerLink="/billing" class="block border-b border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-100">
            {{ 'billing.graceBanner' | translate:{ date: (billing.subscription()?.gracePeriodEndsAt | date:'mediumDate') } }}
          </a>
        }
        @if (billing.isSuspended()) {
          <a routerLink="/billing" class="block border-b border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-800 dark:border-rose-900 dark:bg-rose-950/50 dark:text-rose-100">
            {{ 'billing.suspendedBanner' | translate }}
          </a>
        }

        @if (mobileOpen()) {
          <div class="border-b border-slate-200 bg-white p-3 lg:hidden dark:border-white/10 dark:bg-slate-900">
            @for (item of visibleNav(); track item.path) {
              <a [routerLink]="item.path" (click)="mobileOpen.set(false)" class="flex items-center gap-3 rounded-xl px-3 py-2 text-sm">
                <app-nav-icon [name]="item.icon" />
                <span class="flex-1">{{ item.label | translate }}</span>
                @if (item.path === '/messages' && inbox.unreadMessages() > 0) {
                  <span class="grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] text-white"
                        [attr.aria-label]="inbox.unreadMessages() + ' ' + ('messages.unreadShort' | translate)">{{ inbox.badge(inbox.unreadMessages()) }}</span>
                }
              </a>
            }
          </div>
        }

        <main class="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6">
          <router-outlet />
        </main>
      </div>
    </div>
  `
})
export class ShellComponent implements OnInit {
  auth = inject(AuthService);
  branding = inject(BrandingService);
  billing = inject(BillingService);
  inbox = inject(MessageInboxService);
  private api = inject(ApiService);
  private router = inject(Router);
  collapsed = signal(false);
  mobileOpen = signal(false);
  query = '';
  results = signal<SearchResult | null>(null);
  notesOpen = signal(false);
  userMenuOpen = signal(false);
  notes = signal<any[]>([]);
  private search$ = new Subject<string>();
  private i18n = inject(TranslateService);

  nav: NavItem[] = [
    { path: '/dashboard', label: 'nav.dashboard', icon: 'home' },
    { path: '/admin/tenants', label: 'nav.tenants', icon: 'tenants', roles: ['SUPER_ADMIN'] },
    { path: '/admin/plans', label: 'nav.plans', icon: 'plans', roles: ['SUPER_ADMIN'] },
    { path: '/admin/subscriptions', label: 'nav.subscriptions', icon: 'subscriptions', roles: ['SUPER_ADMIN'] },
    { path: '/admin/users', label: 'nav.users', icon: 'users', roles: ['SUPER_ADMIN'] },
    { path: '/admin/audit', label: 'nav.audit', icon: 'audit', roles: ['SUPER_ADMIN'] },
    { path: '/owners', label: 'nav.owners', icon: 'owners', roles: ['TENANT_OWNER', 'TENANT_ADMIN', 'RECEPTIONIST', 'VETERINARIAN'] },
    { path: '/pets', label: 'nav.pets', icon: 'pets' },
    { path: '/calendar', label: 'nav.calendar', icon: 'calendar' },
    { path: '/consultations/new', label: 'nav.consultations', icon: 'consultations', permission: 'MEDICAL_RECORD_WRITE' },
    { path: '/team', label: 'nav.team', icon: 'team', roles: ['TENANT_OWNER', 'TENANT_ADMIN'] },
    { path: '/branches', label: 'nav.branches', icon: 'branches', roles: ['TENANT_OWNER', 'TENANT_ADMIN'] },
    { path: '/services', label: 'nav.services', icon: 'services', roles: ['TENANT_OWNER', 'TENANT_ADMIN'] },
    { path: '/messages', label: 'nav.messages', icon: 'messages' },
    { path: '/billing', label: 'nav.billing', icon: 'subscriptions', roles: ['TENANT_OWNER', 'TENANT_ADMIN'] },
    { path: '/reports', label: 'nav.reports', icon: 'reports', permission: 'REPORT_VIEW' },
    { path: '/settings', label: 'nav.settings', icon: 'settings', roles: ['TENANT_OWNER', 'TENANT_ADMIN'] },
    { path: '/audit', label: 'nav.audit', icon: 'audit', roles: ['TENANT_OWNER', 'TENANT_ADMIN'] },
    { path: '/profile', label: 'nav.profile', icon: 'profile' }
  ];

  groups = [
    { key: 'pets', label: 'shell.pets', path: '/pets/' },
    { key: 'owners', label: 'shell.owners', path: '/pets?owner=' },
    { key: 'veterinarians', label: 'shell.vets', path: '/team' }
  ];

  visibleNav = computed(() => this.nav.filter(item => {
    if (this.billing.isSuspended() && !['/billing', '/profile'].includes(item.path)) {
      return false;
    }
    if (item.roles && !this.auth.hasAnyRole(...item.roles)) {
      return false;
    }
    if (item.permission && !this.auth.hasPermission(item.permission) && !this.auth.isSuperAdmin()) {
      return false;
    }
    if (this.auth.isSuperAdmin() && ['/owners', '/pets', '/calendar', '/messages'].includes(item.path)) {
      return false;
    }
    return true;
  }));

  ngOnInit(): void {
    if (!this.auth.isSuperAdmin()) {
      this.inbox.start();
    }
    this.branding.loadForSession();
    if (this.auth.isStaff() && !this.auth.isSuperAdmin()) {
      this.billing.loadSubscription().subscribe(sub => {
        if (sub.suspended && !this.router.url.startsWith('/billing') && !this.router.url.startsWith('/profile')) {
          void this.router.navigate(['/billing']);
        }
      });
    }
    this.search$.pipe(debounceTime(280), distinctUntilChanged()).subscribe(q => {
      if (!q || q.length < 2 || !this.auth.isStaff()) {
        this.results.set(null);
        return;
      }
      this.api.get<SearchResult>('/search', { q }).subscribe(r => this.results.set(r));
    });
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.mobileOpen.set(false);
      this.results.set(null);
    });
  }

  onQuery(value: string): void {
    this.search$.next(value.trim());
  }

  hasResults(): boolean {
    const r = this.results();
    return !!r && (r.pets.length + r.owners.length + r.veterinarians.length) > 0;
  }

  @HostListener('document:keydown.escape')
  closeMenus(): void {
    this.notesOpen.set(false);
    this.userMenuOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  closeOnOutside(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target?.closest('[data-menu]')) {
      this.notesOpen.set(false);
      this.userMenuOpen.set(false);
    }
  }

  toggleNotes(): void {
    this.userMenuOpen.set(false);
    this.notesOpen.update(open => !open);
    if (this.notesOpen()) {
      this.api.get<any>('/notifications', { size: 8 }).subscribe(page => {
        this.notes.set(page.content || page || []);
      });
    }
  }

  toggleUser(): void {
    this.notesOpen.set(false);
    this.userMenuOpen.update(open => !open);
  }

  when(value?: string): string {
    return relativeTime(value, (key, params) => this.i18n.instant(key, params));
  }

  markAllNotes(): void {
    this.api.post('/notifications/read-all', {}).subscribe({
      next: () => {
        this.inbox.refresh();
        this.notes.update(items => items.map(item => ({ ...item, readAt: item.readAt || new Date().toISOString() })));
      },
      error: () => undefined
    });
  }

  readNote(n: any): void {
    this.api.post(`/notifications/${n.id}/read`, {}).subscribe({
      next: () => this.inbox.refresh(),
      error: () => undefined
    });
    this.notesOpen.set(false);
    const target = notificationTarget(n);
    if (target) {
      void this.router.navigate(target.commands, { queryParams: target.queryParams });
    }
  }

  switchClinic(slug: string): void {
    if (!slug || slug === this.auth.user()?.tenantSlug) {
      return;
    }
    this.auth.switchTenant(slug).subscribe(() => window.location.reload());
  }
}
