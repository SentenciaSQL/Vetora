import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged, filter, Subject } from 'rxjs';
import { AuthService } from '../core/services/auth.service';
import { BrandingService } from '../core/services/branding.service';
import { ApiService } from '../core/services/api.service';
import { SearchResult } from '../core/models';
import { BrandMarkComponent } from '../shared/ui/brand-mark.component';
import { LanguageSelectorComponent } from '../shared/ui/language-selector.component';
import { ThemeSelectorComponent } from '../shared/ui/theme-selector.component';
import { NavIconComponent } from '../shared/ui/nav-icon.component';

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
    RouterOutlet, RouterLink, RouterLinkActive, FormsModule, TranslatePipe,
    BrandMarkComponent, LanguageSelectorComponent, ThemeSelectorComponent, NavIconComponent
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
                <span>{{ item.label | translate }}</span>
              }
            </a>
          }
        </nav>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="sticky top-0 z-30 flex items-center gap-3 border-b border-slate-200/80 bg-white/80 px-4 py-3 backdrop-blur dark:border-white/10 dark:bg-slate-900/80">
          <button type="button" class="rounded-xl p-2 lg:hidden" (click)="mobileOpen.set(!mobileOpen())" aria-label="Menu">☰</button>
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
          <button type="button" class="relative rounded-xl border border-slate-200 px-3 py-2 text-sm dark:border-slate-700" (click)="loadNotes()">
            {{ 'nav.notifications' | translate }}
            @if (unread() > 0) {
              <span class="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] text-white">{{ unread() }}</span>
            }
          </button>
          <app-language-selector />
          <app-theme-selector />
          @if ((auth.user()?.memberships?.length || 0) > 1) {
            <select class="input w-40 text-xs" [value]="auth.user()?.tenantSlug || ''" (change)="switchClinic($any($event.target).value)">
              @for (m of auth.user()?.memberships || []; track m.slug) {
                <option [value]="m.slug">{{ m.name }}</option>
              }
            </select>
          }
          <div class="hidden items-center gap-2 sm:flex">
            <div class="text-right">
              <p class="text-sm font-medium">{{ auth.user()?.fullName }}</p>
              <p class="text-xs text-slate-400">{{ auth.user()?.role || auth.user()?.roles?.[0] }}</p>
            </div>
            <button type="button" class="btn-secondary" (click)="auth.logout()">{{ 'nav.logout' | translate }}</button>
          </div>
        </header>

        @if (mobileOpen()) {
          <div class="border-b border-slate-200 bg-white p-3 lg:hidden dark:border-white/10 dark:bg-slate-900">
            @for (item of visibleNav(); track item.path) {
              <a [routerLink]="item.path" (click)="mobileOpen.set(false)" class="flex items-center gap-3 rounded-xl px-3 py-2 text-sm">
                <app-nav-icon [name]="item.icon" />
                <span>{{ item.label | translate }}</span>
              </a>
            }
          </div>
        }

        @if (notesOpen()) {
          <div class="border-b border-slate-200 bg-white px-4 py-3 text-sm dark:border-white/10 dark:bg-slate-900">
            @for (n of notes(); track n.id) {
              <button type="button" class="block w-full py-1 text-left" (click)="readNote(n)">
                {{ n.title || n.titleEs }}
              </button>
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
  private api = inject(ApiService);
  private router = inject(Router);
  collapsed = signal(false);
  mobileOpen = signal(false);
  query = '';
  results = signal<SearchResult | null>(null);
  unread = signal(0);
  notesOpen = signal(false);
  notes = signal<any[]>([]);
  private search$ = new Subject<string>();

  nav: NavItem[] = [
    { path: '/dashboard', label: 'nav.dashboard', icon: 'home' },
    { path: '/admin', label: 'nav.admin', icon: 'admin', roles: ['SUPER_ADMIN'] },
    { path: '/admin/tenants', label: 'nav.tenants', icon: 'tenants', roles: ['SUPER_ADMIN'] },
    { path: '/admin/plans', label: 'nav.plans', icon: 'plans', roles: ['SUPER_ADMIN'] },
    { path: '/admin/subscriptions', label: 'nav.subscriptions', icon: 'subscriptions', roles: ['SUPER_ADMIN'] },
    { path: '/admin/users', label: 'nav.users', icon: 'users', roles: ['SUPER_ADMIN'] },
    { path: '/admin/audit', label: 'nav.audit', icon: 'audit', roles: ['SUPER_ADMIN'] },
    { path: '/owners', label: 'nav.owners', icon: 'owners', roles: ['TENANT_ADMIN', 'RECEPTIONIST', 'VETERINARIAN'] },
    { path: '/pets', label: 'nav.pets', icon: 'pets' },
    { path: '/calendar', label: 'nav.calendar', icon: 'calendar' },
    { path: '/consultations/new', label: 'nav.consultations', icon: 'consultations', permission: 'MEDICAL_RECORD_WRITE' },
    { path: '/team', label: 'nav.team', icon: 'team', roles: ['TENANT_ADMIN'] },
    { path: '/branches', label: 'nav.branches', icon: 'branches', roles: ['TENANT_ADMIN'] },
    { path: '/services', label: 'nav.services', icon: 'services', roles: ['TENANT_ADMIN'] },
    { path: '/messages', label: 'nav.messages', icon: 'messages' },
    { path: '/reports', label: 'nav.reports', icon: 'reports', permission: 'REPORT_VIEW' },
    { path: '/settings', label: 'nav.settings', icon: 'settings', roles: ['TENANT_ADMIN'] },
    { path: '/audit', label: 'nav.audit', icon: 'audit', roles: ['TENANT_ADMIN'] },
    { path: '/profile', label: 'nav.profile', icon: 'profile' }
  ];

  groups = [
    { key: 'pets', label: 'shell.pets', path: '/pets/' },
    { key: 'owners', label: 'shell.owners', path: '/pets?owner=' },
    { key: 'veterinarians', label: 'shell.vets', path: '/team' }
  ];

  visibleNav = computed(() => this.nav.filter(item => {
    if (item.roles && !this.auth.hasAnyRole(...item.roles)) {
      return false;
    }
    if (item.permission && !this.auth.hasPermission(item.permission) && !this.auth.isSuperAdmin()) {
      return false;
    }
    if (this.auth.isSuperAdmin() && ['/owners', '/pets', '/calendar'].includes(item.path)) {
      return false;
    }
    return true;
  }));

  ngOnInit(): void {
    this.branding.loadForSession();
    if (this.auth.isStaff()) {
      this.api.get<{ count: number }>('/notifications/unread-count').subscribe(r => this.unread.set(r.count || 0));
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

  loadNotes(): void {
    this.notesOpen.set(!this.notesOpen());
    if (this.notesOpen()) {
      this.api.get<any>('/notifications', { size: 8 }).subscribe(page => {
        this.notes.set(page.content || page || []);
      });
    }
  }

  readNote(n: any): void {
    this.api.post(`/notifications/${n.id}/read`, {}).subscribe(() => {
      this.unread.update(v => Math.max(0, v - 1));
      this.loadNotes();
    });
  }

  switchClinic(slug: string): void {
    if (!slug || slug === this.auth.user()?.tenantSlug) {
      return;
    }
    this.auth.switchTenant(slug).subscribe(() => window.location.reload());
  }
}
