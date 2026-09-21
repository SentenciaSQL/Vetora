import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { catchError, Observable, of, tap } from 'rxjs';
import { TokenResponse, UserProfile } from '../models';
import { ApiService } from './api.service';
import { ThemeService, ThemeMode } from './theme.service';

const ACCESS = 'animalin.access';
const REFRESH = 'animalin.refresh';
const USER = 'animalin.user';
const LAST_ACTIVITY = 'animalin.lastActivity';
const LIVE_TENANT = ['ACTIVE', 'TRIAL', 'TRIALING', 'PAST_DUE', 'GRACE_PERIOD', 'SUSPENDED', 'PAUSED', 'CANCELED', 'CANCELLED'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);
  private i18n = inject(TranslateService);
  private theme = inject(ThemeService);

  user = signal<UserProfile | null>(this.readUser());
  accessToken = signal<string | null>(localStorage.getItem(ACCESS));
  refreshToken = signal<string | null>(localStorage.getItem(REFRESH));

  constructor() {
    window.addEventListener('storage', event => this.onStorage(event));
  }

  get isAuthenticated(): boolean {
    return !!this.accessToken();
  }

  login(email: string, password: string, tenantSlug?: string): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/login', { email, password, tenantSlug }).pipe(
      tap(response => this.store(response, true))
    );
  }

  register(payload: Record<string, string>): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/register', payload).pipe(
      tap(response => this.store(response, true))
    );
  }

  refresh(): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/refresh', { refreshToken: this.refreshToken() }).pipe(
      tap(response => this.store(response, false))
    );
  }

  logout(): void {
    this.endSession('MANUAL');
    void this.router.navigate(['/login'], { replaceUrl: true });
  }

  endSession(reason = 'MANUAL'): void {
    const refresh = this.refreshToken();
    if (refresh && reason !== 'REMOTE') {
      this.api.post('/auth/logout', { refreshToken: refresh, reason }).subscribe({ error: () => undefined });
    }
    this.clearSession();
  }

  clearSession(): void {
    localStorage.removeItem(ACCESS);
    localStorage.removeItem(REFRESH);
    localStorage.removeItem(USER);
    localStorage.removeItem(LAST_ACTIVITY);
    this.accessToken.set(null);
    this.refreshToken.set(null);
    this.user.set(null);
  }

  forgot(email: string) {
    return this.api.post('/auth/forgot-password', { email });
  }

  reset(token: string, password: string) {
    return this.api.post('/auth/reset-password', { token, password });
  }

  patchMe(payload: Partial<Pick<UserProfile, 'firstName' | 'lastName' | 'phone' | 'locale' | 'theme'>>) {
    return this.api.patch<UserProfile>('/auth/me', payload).pipe(tap(user => this.applyUser(user)));
  }

  switchTenant(tenantSlug: string) {
    return this.api.post<TokenResponse>('/auth/switch-tenant', { tenantSlug }).pipe(
      tap(response => this.store(response, false))
    );
  }

  reloadProfile() {
    return this.api.get<UserProfile>('/auth/me').pipe(tap(user => this.applyUser(user)));
  }

  hydrate(): Observable<UserProfile | null> {
    if (!this.accessToken()) {
      return of(null);
    }
    return this.reloadProfile().pipe(
      catchError(() => of(this.user()))
    );
  }

  applyUser(user: UserProfile): void {
    localStorage.setItem(USER, JSON.stringify(user));
    this.user.set(user);
    const locale = user.locale || 'es';
    this.i18n.use(locale);
    document.documentElement.lang = locale;
    if (user.theme === 'light' || user.theme === 'dark' || user.theme === 'system') {
      this.theme.set(user.theme as ThemeMode);
    }
  }

  hasRole(role: string): boolean {
    const user = this.user();
    return !!user && (user.roles?.includes(role) || user.role === role);
  }

  hasAnyRole(...roles: string[]): boolean {
    return roles.some(role => this.hasRole(role));
  }

  hasPermission(permission: string): boolean {
    return !!this.user()?.permissions?.includes(permission);
  }

  isStaff(): boolean {
    return this.hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN', 'VETERINARIAN', 'RECEPTIONIST');
  }

  isTenantOwner(): boolean {
    return this.hasRole('TENANT_OWNER');
  }

  isSuperAdmin(): boolean {
    return this.hasRole('SUPER_ADMIN');
  }

  onboardingComplete(): boolean {
    const user = this.user();
    if (!user || !this.isTenantOwner()) {
      return true;
    }
    if (user.onboardingComplete || user.signupStatus === 'COMPLETED' || user.accessGranted) {
      return true;
    }
    return !!user.tenantStatus && LIVE_TENANT.includes(user.tenantStatus);
  }

  accessGranted(): boolean {
    const user = this.user();
    if (user?.accessGranted) {
      return true;
    }
    return ['ACTIVE', 'TRIAL', 'TRIALING', 'PAST_DUE', 'GRACE_PERIOD'].includes(user?.tenantStatus || '');
  }

  checkoutPending(): boolean {
    const user = this.user();
    if (!user || this.onboardingComplete() || user.accessGranted) {
      return false;
    }
    return !!user.checkoutPending;
  }

  isSuspended(): boolean {
    const status = this.user()?.tenantStatus;
    return status === 'SUSPENDED' || status === 'PAUSED';
  }

  needsClinicSetup(): boolean {
    const user = this.user();
    if (!this.isTenantOwner() || this.onboardingComplete()) {
      return false;
    }
    return !!user?.emailVerified && !user.tenantId;
  }

  needsPlanSelection(): boolean {
    const user = this.user();
    if (!this.isTenantOwner() || this.onboardingComplete() || this.checkoutPending()) {
      return false;
    }
    return !!user?.tenantId && (user.signupStatus === 'PENDING_PAYMENT' || !user.accessGranted);
  }

  shouldLeaveAppRoute(url: string): boolean {
    if (this.onboardingComplete() || this.accessGranted()) {
      return false;
    }
    if (this.isSuspended()) {
      return !url.startsWith('/billing') && !url.startsWith('/profile');
    }
    if (this.needsClinicSetup() || this.needsPlanSelection() || this.checkoutPending()) {
      return true;
    }
    return false;
  }

  homePath(): string {
    if (this.isSuperAdmin()) {
      return '/dashboard';
    }
    const user = this.user();
    if (user && user.emailVerified === false) {
      return '/verify-email';
    }
    if (this.isSuspended()) {
      return '/billing';
    }
    if (this.onboardingComplete() || this.accessGranted()) {
      return '/dashboard';
    }
    if (this.isTenantOwner()) {
      if (!user?.tenantId) {
        return '/register-clinic';
      }
      if (this.checkoutPending()) {
        return '/signup/processing';
      }
      if (this.needsPlanSelection()) {
        return '/register-clinic';
      }
    }
    return '/dashboard';
  }

  store(response: TokenResponse, resetActivity = false): void {
    localStorage.setItem(ACCESS, response.accessToken);
    localStorage.setItem(REFRESH, response.refreshToken);
    this.accessToken.set(response.accessToken);
    this.refreshToken.set(response.refreshToken);
    this.applyUser(response.user);
    if (resetActivity) {
      localStorage.setItem(LAST_ACTIVITY, String(Date.now()));
    }
  }

  private onStorage(event: StorageEvent): void {
    if (event.key === ACCESS) {
      this.accessToken.set(event.newValue);
    }
    if (event.key === REFRESH) {
      this.refreshToken.set(event.newValue);
    }
    if (event.key === USER) {
      if (!event.newValue) {
        this.user.set(null);
        return;
      }
      try {
        this.user.set(JSON.parse(event.newValue) as UserProfile);
      } catch {
        this.user.set(null);
      }
    }
  }

  private readUser(): UserProfile | null {
    const raw = localStorage.getItem(USER);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as UserProfile;
    } catch {
      return null;
    }
  }
}
