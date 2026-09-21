import { Injectable, NgZone, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { SessionConfig } from '../models';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { BillingService } from './billing.service';
import { BrandingService } from './branding.service';
import { ToastService } from './toast.service';

const LAST_ACTIVITY_KEY = 'animalin.lastActivity';
const CHANNEL_NAME = 'lunaveta-session';
const LOCAL_THROTTLE_MS = 10_000;
const CHECK_INTERVAL_MS = 15_000;
const DEFAULT_TIMEOUT_MINUTES = 30;
const DEFAULT_WARNING_MINUTES = 2;

const PUBLIC_PREFIXES = [
  '/login',
  '/register',
  '/forgot',
  '/reset-password',
  '/verify-email',
  '/accept-invite'
];

export type LogoutReason = 'INACTIVITY' | 'MANUAL' | 'REMOTE' | 'UNAUTHORIZED';

@Injectable({ providedIn: 'root' })
export class SessionInactivityService {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private billing = inject(BillingService);
  private branding = inject(BrandingService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private zone = inject(NgZone);

  warningOpen = signal(false);
  config = signal<SessionConfig>({
    inactivityTimeoutMinutes: DEFAULT_TIMEOUT_MINUTES,
    warningBeforeMinutes: DEFAULT_WARNING_MINUTES
  });

  private started = false;
  private closing = false;
  private lastActivityAt = 0;
  private lastLocalWrite = 0;
  private lastHeartbeat = 0;
  private checkTimer?: ReturnType<typeof setInterval>;
  private channel: BroadcastChannel | null = null;
  private listeners: Array<[string, EventListener]> = [];

  constructor() {
    this.loadStoredActivity();
    this.channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME);
    this.channel?.addEventListener('message', event => this.onChannel(event.data));
    window.addEventListener('storage', event => this.onStorage(event));
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(event => {
      const url = (event as NavigationEnd).urlAfterRedirects;
      this.onRoute(url);
    });
    queueMicrotask(() => {
      this.fetchConfig();
      this.onRoute(this.router.url);
    });
  }

  isExpired(): boolean {
    if (!this.auth.isAuthenticated) {
      return false;
    }
    this.loadStoredActivity();
    return this.idleMs() >= this.timeoutMs();
  }

  ensureActive(): boolean {
    this.evaluate();
    if (!this.auth.isAuthenticated) {
      return false;
    }
    if (this.isExpired()) {
      this.expire('INACTIVITY');
      return false;
    }
    return true;
  }

  continueSession(): void {
    if (this.isExpired()) {
      this.expire('INACTIVITY');
      return;
    }
    this.markActivity(true);
    this.warningOpen.set(false);
    this.api.post('/auth/activity', {}).subscribe({
      next: () => {
        this.lastHeartbeat = Date.now();
        this.auth.refresh().subscribe({
          error: err => this.expire(err?.error?.code === 'SESSION_INACTIVE' ? 'INACTIVITY' : 'UNAUTHORIZED')
        });
      },
      error: err => {
        if (err?.status === 401) {
          this.expire(err?.error?.code === 'SESSION_INACTIVE' ? 'INACTIVITY' : 'UNAUTHORIZED');
          return;
        }
      }
    });
  }

  expire(reason: LogoutReason = 'INACTIVITY'): void {
    if (this.closing) {
      return;
    }
    this.closing = true;
    this.stopTracking();
    this.lastActivityAt = 0;
    if (reason !== 'REMOTE') {
      this.broadcast({ type: 'logout', reason });
    }
    this.auth.endSession(reason);
    this.billing.subscription.set(null);
    this.billing.config.set(null);
    this.branding.branding.set(null);
    localStorage.removeItem(LAST_ACTIVITY_KEY);
    if (reason === 'INACTIVITY') {
      this.toast.show('auth.sessionExpired', true);
    }
    const queryParams = reason === 'INACTIVITY' ? { expired: 'inactivity' } : {};
    void this.router.navigate(['/login'], { queryParams, replaceUrl: true });
    this.closing = false;
  }

  private onRoute(url: string): void {
    if (!this.auth.isAuthenticated) {
      this.stopTracking();
      return;
    }
    this.loadStoredActivity();
    if (this.isExpired()) {
      this.expire('INACTIVITY');
      return;
    }
    if (this.isPublicPath(url)) {
      this.stopTracking();
      return;
    }
    this.startTracking();
    this.evaluate();
  }

  private startTracking(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.closing = false;
    this.warningOpen.set(false);
    this.loadStoredActivity();
    this.bindActivity();
    this.zone.runOutsideAngular(() => {
      this.checkTimer = setInterval(() => this.zone.run(() => this.evaluate()), CHECK_INTERVAL_MS);
    });
  }

  private stopTracking(): void {
    this.started = false;
    this.warningOpen.set(false);
    this.listeners.forEach(([name, listener]) => {
      if (name === 'visibilitychange') {
        document.removeEventListener(name, listener);
      } else {
        window.removeEventListener(name, listener);
      }
    });
    this.listeners = [];
    if (this.checkTimer) {
      clearInterval(this.checkTimer);
      this.checkTimer = undefined;
    }
  }

  private bindActivity(): void {
    const onActivity = () => this.markActivity(false);
    ['click', 'keydown', 'mousemove', 'scroll', 'touchstart'].forEach(name => {
      const listener: EventListener = () => onActivity();
      this.listeners.push([name, listener]);
      window.addEventListener(name, listener, { passive: true });
    });
    const visibility: EventListener = () => {
      if (document.visibilityState === 'visible') {
        this.evaluate();
      }
    };
    const focus: EventListener = () => this.evaluate();
    const pageshow: EventListener = () => this.evaluate();
    this.listeners.push(['visibilitychange', visibility], ['focus', focus], ['pageshow', pageshow]);
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('focus', focus);
    window.addEventListener('pageshow', pageshow);
  }

  private markActivity(force: boolean): void {
    if (!this.auth.isAuthenticated || this.isPublicPath(this.router.url) || this.closing) {
      return;
    }
    const now = Date.now();
    if (!force && now - this.lastLocalWrite < LOCAL_THROTTLE_MS) {
      return;
    }
    this.lastLocalWrite = now;
    this.lastActivityAt = now;
    localStorage.setItem(LAST_ACTIVITY_KEY, String(now));
    this.broadcast({ type: 'activity', at: now });
    if (this.warningOpen() && !this.isExpired()) {
      this.warningOpen.set(false);
    }
    this.heartbeat(false);
  }

  private heartbeat(force: boolean): void {
    const now = Date.now();
    const minutes = Math.max(1, Math.min(5, Math.floor(this.config().inactivityTimeoutMinutes / 6) || 5));
    const gap = minutes * 60_000;
    if (!force && now - this.lastHeartbeat < gap) {
      return;
    }
    this.lastHeartbeat = now;
    this.api.post('/auth/activity', {}).subscribe({
      error: err => {
        if (err?.status === 401) {
          this.expire(err?.error?.code === 'SESSION_INACTIVE' ? 'INACTIVITY' : 'UNAUTHORIZED');
        }
      }
    });
  }

  private evaluate(): void {
    if (!this.auth.isAuthenticated) {
      this.stopTracking();
      return;
    }
    this.loadStoredActivity();
    const idle = this.idleMs();
    if (idle >= this.timeoutMs()) {
      this.expire('INACTIVITY');
      return;
    }
    if (this.isPublicPath(this.router.url)) {
      this.stopTracking();
      return;
    }
    const remaining = this.timeoutMs() - idle;
    this.warningOpen.set(remaining <= this.warningMs());
  }

  private idleMs(): number {
    if (!this.lastActivityAt) {
      return this.timeoutMs();
    }
    return Math.max(0, Date.now() - this.lastActivityAt);
  }

  private timeoutMs(): number {
    return Math.max(1, this.config().inactivityTimeoutMinutes) * 60_000;
  }

  private warningMs(): number {
    return Math.max(1, this.config().warningBeforeMinutes) * 60_000;
  }

  private loadStoredActivity(): void {
    const raw = localStorage.getItem(LAST_ACTIVITY_KEY);
    const parsed = raw ? Number(raw) : 0;
    if (parsed > this.lastActivityAt) {
      this.lastActivityAt = parsed;
    }
  }

  private fetchConfig(): void {
    this.api.get<SessionConfig>('/public/session-config').subscribe({
      next: config => {
        if (config?.inactivityTimeoutMinutes > 0) {
          this.config.set({
            inactivityTimeoutMinutes: config.inactivityTimeoutMinutes,
            warningBeforeMinutes: config.warningBeforeMinutes > 0
              ? config.warningBeforeMinutes
              : DEFAULT_WARNING_MINUTES
          });
          this.evaluate();
        }
      },
      error: () => undefined
    });
  }

  private onChannel(data: { type?: string; at?: number; reason?: LogoutReason } | null): void {
    if (!data?.type) {
      return;
    }
    this.zone.run(() => {
      if (data.type === 'activity' && typeof data.at === 'number') {
        this.lastActivityAt = Math.max(this.lastActivityAt, data.at);
        if (this.started) {
          this.evaluate();
        }
      }
      if (data.type === 'logout') {
        this.handleRemoteLogout();
      }
    });
  }

  private onStorage(event: StorageEvent): void {
    if (event.key === LAST_ACTIVITY_KEY && event.newValue) {
      const at = Number(event.newValue);
      if (at > this.lastActivityAt) {
        this.lastActivityAt = at;
        if (this.started) {
          this.evaluate();
        }
      }
    }
    if (event.key === 'animalin.access' && !event.newValue) {
      this.handleRemoteLogout();
    }
  }

  private handleRemoteLogout(): void {
    if (this.isPublicPath(this.router.url) && !this.auth.accessToken() && !this.auth.refreshToken()) {
      this.stopTracking();
      return;
    }
    this.expire('REMOTE');
  }

  private broadcast(payload: { type: string; at?: number; reason?: string }): void {
    try {
      this.channel?.postMessage(payload);
    } catch {
      /* ignore */
    }
  }

  private isPublicPath(url: string): boolean {
    const path = (url || '').split('?')[0];
    if (path.startsWith('/register-clinic') && !this.auth.isAuthenticated) {
      return true;
    }
    return PUBLIC_PREFIXES.some(prefix => path === prefix || path.startsWith(`${prefix}/`));
  }
}
