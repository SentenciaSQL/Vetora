import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ApiErrorBody } from '../models';

let refreshing = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.accessToken();
  const isAuthCall = req.url.includes('/auth/login')
    || req.url.includes('/auth/register')
    || req.url.includes('/auth/refresh')
    || req.url.includes('/auth/forgot-password')
    || req.url.includes('/auth/reset-password')
    || req.url.includes('/public/')
    || req.url.includes('/assets/');

  const authorized = token && !isAuthCall
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      const body = error.error as ApiErrorBody | undefined;
      if (error.status === 403 && body?.code === 'TENANT_SUBSCRIPTION_SUSPENDED' && !router.url.startsWith('/billing')) {
        void router.navigate(['/billing']);
        return throwError(() => error);
      }
      if (error.status !== 401 || isAuthCall || refreshing || !auth.refreshToken()) {
        return throwError(() => error);
      }
      refreshing = true;
      return auth.refresh().pipe(
        switchMap(() => {
          refreshing = false;
          const retry = req.clone({ setHeaders: { Authorization: `Bearer ${auth.accessToken()}` } });
          return next(retry);
        }),
        catchError(refreshError => {
          refreshing = false;
          auth.logout();
          return throwError(() => refreshError);
        })
      );
    })
  );
};
