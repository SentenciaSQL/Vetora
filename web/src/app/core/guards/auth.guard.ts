import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { SessionInactivityService } from '../services/session-inactivity.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!auth.isAuthenticated) {
    return router.createUrlTree(['/login']);
  }
  if (!session.ensureActive()) {
    return false;
  }
  return true;
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!auth.isAuthenticated) {
    return true;
  }
  if (!session.ensureActive()) {
    return true;
  }
  return router.createUrlTree([auth.homePath()]);
};

export const clinicSignupGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!auth.isAuthenticated) {
    return true;
  }
  if (!session.ensureActive()) {
    return false;
  }
  if (auth.onboardingComplete() || auth.accessGranted()) {
    return router.createUrlTree([auth.isSuspended() ? '/billing' : '/dashboard']);
  }
  if (auth.checkoutPending()) {
    return router.createUrlTree(['/signup/processing']);
  }
  if (auth.needsClinicSetup() || auth.needsPlanSelection() || auth.isTenantOwner()) {
    return true;
  }
  return router.createUrlTree([auth.homePath()]);
};

export const onboardingGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!auth.isAuthenticated || !session.ensureActive()) {
    return false;
  }
  if (auth.isSuperAdmin()) {
    return true;
  }
  const url = state.url.split('?')[0];
  if (auth.isSuspended() && !isAllowedWhileSuspended(url)) {
    return router.createUrlTree(['/billing']);
  }
  const destination = auth.homePath();
  if (destination !== url && auth.shouldLeaveAppRoute(url)) {
    return router.createUrlTree([destination]);
  }
  return true;
};

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!session.ensureActive()) {
    return false;
  }
  return auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!session.ensureActive()) {
    return false;
  }
  return auth.isStaff() || auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const medicalWriteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!session.ensureActive()) {
    return false;
  }
  return auth.hasPermission('MEDICAL_RECORD_WRITE') || auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const settingsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!session.ensureActive()) {
    return false;
  }
  return auth.canManageSettings() || router.createUrlTree([auth.homePath()]);
};

export const auditGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!session.ensureActive()) {
    return false;
  }
  return auth.hasPermission('STAFF_MANAGE') || auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const billingAccessGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const session = inject(SessionInactivityService);
  const router = inject(Router);
  if (!session.ensureActive()) {
    return false;
  }
  if (auth.isSuperAdmin()) {
    return router.createUrlTree(['/dashboard']);
  }
  return auth.isStaff() || router.createUrlTree([auth.homePath()]);
};

function isAllowedWhileSuspended(url: string): boolean {
  return url.startsWith('/billing') || url.startsWith('/profile');
}
