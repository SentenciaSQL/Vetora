import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { TokenResponse, UserProfile } from '../models';
import { ApiService } from './api.service';
import { ThemeService, ThemeMode } from './theme.service';

const ACCESS = 'animalin.access';
const REFRESH = 'animalin.refresh';
const USER = 'animalin.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);
  private i18n = inject(TranslateService);
  private theme = inject(ThemeService);

  user = signal<UserProfile | null>(this.readUser());
  accessToken = signal<string | null>(localStorage.getItem(ACCESS));
  refreshToken = signal<string | null>(localStorage.getItem(REFRESH));

  get isAuthenticated(): boolean {
    return !!this.accessToken();
  }

  login(email: string, password: string, tenantSlug?: string): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/login', { email, password, tenantSlug }).pipe(
      tap(response => this.store(response))
    );
  }

  register(payload: Record<string, string>): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/register', payload).pipe(
      tap(response => this.store(response))
    );
  }

  refresh(): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/refresh', { refreshToken: this.refreshToken() }).pipe(
      tap(response => this.store(response))
    );
  }

  logout(): void {
    const refresh = this.refreshToken();
    if (refresh) {
      this.api.post('/auth/logout', { refreshToken: refresh }).subscribe();
    }
    localStorage.removeItem(ACCESS);
    localStorage.removeItem(REFRESH);
    localStorage.removeItem(USER);
    this.accessToken.set(null);
    this.refreshToken.set(null);
    this.user.set(null);
    void this.router.navigate(['/login']);
  }

  forgot(email: string) {
    return this.api.post('/auth/forgot-password', { email });
  }

  reset(token: string, password: string) {
    return this.api.post('/auth/reset-password', { token, password });
  }

  patchMe(payload: Partial<Pick<UserProfile, 'firstName' | 'lastName' | 'phone' | 'locale' | 'theme'>>) {
    return this.api.patch<UserProfile>('/auth/me', payload).pipe(tap(user => this.setUser(user)));
  }

  switchTenant(tenantSlug: string) {
    return this.api.post<TokenResponse>('/auth/switch-tenant', { tenantSlug }).pipe(
      tap(response => this.store(response))
    );
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
    return this.hasAnyRole('TENANT_ADMIN', 'VETERINARIAN', 'RECEPTIONIST');
  }

  isSuperAdmin(): boolean {
    return this.hasRole('SUPER_ADMIN');
  }

  homePath(): string {
    if (this.isSuperAdmin()) {
      return '/admin';
    }
    return '/dashboard';
  }

  private store(response: TokenResponse): void {
    localStorage.setItem(ACCESS, response.accessToken);
    localStorage.setItem(REFRESH, response.refreshToken);
    this.accessToken.set(response.accessToken);
    this.refreshToken.set(response.refreshToken);
    this.setUser(response.user);
  }

  private setUser(user: UserProfile): void {
    localStorage.setItem(USER, JSON.stringify(user));
    this.user.set(user);
    const locale = user.locale || 'es';
    this.i18n.use(locale);
    document.documentElement.lang = locale;
    if (user.theme === 'light' || user.theme === 'dark' || user.theme === 'system') {
      this.theme.set(user.theme as ThemeMode);
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
