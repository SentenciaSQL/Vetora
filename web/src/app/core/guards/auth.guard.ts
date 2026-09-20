import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated) {
    return true;
  }
  return router.createUrlTree([auth.homePath()]);
};

export const clinicSignupGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated || auth.needsClinicSetup() || auth.isTenantOwner()) {
    return true;
  }
  return router.createUrlTree([auth.homePath()]);
};

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isStaff() || auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const medicalWriteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.hasPermission('MEDICAL_RECORD_WRITE') || auth.isSuperAdmin() || router.createUrlTree([auth.homePath()]);
};

export const billingAccessGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isSuperAdmin()) {
    return router.createUrlTree(['/admin']);
  }
  return auth.isStaff() || router.createUrlTree([auth.homePath()]);
};
