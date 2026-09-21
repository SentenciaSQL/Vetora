import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, Observable, shareReplay, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiErrorBody, TokenResponse } from '../models';
import { AuthService } from '../services/auth.service';
import { SessionInactivityService } from '../services/session-inactivity.service';

export const AUTH_RETRIED = new HttpContextToken(() => false);

let refresh$: Observable<TokenResponse> | null = null;

function isAppApi(url: string): boolean {
  if (/paddle\.(com|io|js)/i.test(url)) {
    return false;
  }
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url.includes('/api/v1') || (!!environment.apiUrl && url.startsWith(environment.apiUrl));
  }
  return true;
}

function isAuthCall(url: string): boolean {
  return url.includes('/auth/login')
    || url.includes('/auth/register')
    || url.includes('/auth/register-clinic')
    || url.includes('/auth/refresh')
    || url.includes('/auth/logout')
    || url.includes('/auth/forgot-password')
    || url.includes('/auth/reset-password')
    || url.includes('/auth/verify-email')
    || url.includes('/auth/resend-verification')
    || url.includes('/auth/invite')
    || url.includes('/auth/accept-invite')
    || url.includes('/public/')
    || url.includes('/assets/');
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const injector = inject(Injector);
  const token = auth.accessToken();
  const skipAuth = isAuthCall(req.url) || !isAppApi(req.url);
  const authorized = token && !skipAuth
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      const body = error.error as ApiErrorBody | undefined;
      if (error.status === 403 && body?.code === 'EMAIL_NOT_VERIFIED' && !router.url.startsWith('/verify-email')) {
        void router.navigate(['/verify-email']);
        return throwError(() => error);
      }
      if (error.status === 403 && body?.code === 'TENANT_SUBSCRIPTION_SUSPENDED' && !router.url.startsWith('/billing')) {
        void router.navigate(['/billing']);
        return throwError(() => error);
      }
      if (error.status === 403 || error.status === 400 || error.status === 404 || error.status === 409
          || error.status === 422 || error.status === 500) {
        return throwError(() => error);
      }
      if (error.status !== 401 || skipAuth) {
        return throwError(() => error);
      }
      const session = injector.get(SessionInactivityService);
      if (body?.code === 'SESSION_INACTIVE') {
        session.expire('INACTIVITY');
        return throwError(() => error);
      }
      if (req.context.get(AUTH_RETRIED)) {
        session.expire('UNAUTHORIZED');
        return throwError(() => error);
      }
      if (session.isExpired()) {
        session.expire('INACTIVITY');
        return throwError(() => error);
      }
      if (!auth.refreshToken()) {
        session.expire('UNAUTHORIZED');
        return throwError(() => error);
      }
      if (!refresh$) {
        refresh$ = auth.refresh().pipe(
          catchError(refreshError => {
            const refreshBody = (refreshError as HttpErrorResponse).error as ApiErrorBody | undefined;
            session.expire(refreshBody?.code === 'SESSION_INACTIVE' ? 'INACTIVITY' : 'UNAUTHORIZED');
            return throwError(() => refreshError);
          }),
          shareReplay(1),
          finalize(() => {
            refresh$ = null;
          })
        );
      }
      return refresh$.pipe(
        switchMap(() => next(authorized.clone({
          setHeaders: { Authorization: `Bearer ${auth.accessToken()}` },
          context: req.context.set(AUTH_RETRIED, true)
        })))
      );
    })
  );
};
