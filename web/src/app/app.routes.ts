import { Routes } from '@angular/router';
import { authGuard, guestGuard, medicalWriteGuard, staffGuard, superAdminGuard, billingAccessGuard, clinicSignupGuard, onboardingGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/auth/login.page').then(m => m.LoginPage)
  },
  {
    path: 'login/:slug',
    loadComponent: () => import('./pages/auth/login.page').then(m => m.LoginPage)
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/register.page').then(m => m.RegisterPage)
  },
  {
    path: 'register-clinic',
    canActivate: [clinicSignupGuard],
    loadComponent: () => import('./pages/auth/register-clinic.page').then(m => m.RegisterClinicPage)
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./pages/auth/verify-email.page').then(m => m.VerifyEmailPage)
  },
  {
    path: 'signup/processing',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/auth/signup-processing.page').then(m => m.SignupProcessingPage)
  },
  {
    path: 'signup/success',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/auth/signup-result.page').then(m => m.SignupResultPage)
  },
  {
    path: 'accept-invite',
    loadComponent: () => import('./pages/auth/accept-invite.page').then(m => m.AcceptInvitePage)
  },
  {
    path: 'eliminar-cuenta',
    loadComponent: () => import('./pages/public/delete-account.page').then(m => m.DeleteAccountPage)
  },
  {
    path: 'forgot',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/forgot.page').then(m => m.ForgotPage)
  },
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/reset.page').then(m => m.ResetPage)
  },
  {
    path: '',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/clinic/dashboard/dashboard.page').then(m => m.DashboardPage) },
      { path: 'owners', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/owners/owners.page').then(m => m.OwnersPage) },
      { path: 'pets', loadComponent: () => import('./pages/clinic/pets/pets.page').then(m => m.PetsPage) },
      { path: 'pets/:id', loadComponent: () => import('./pages/clinic/pets/pet-profile.page').then(m => m.PetProfilePage) },
      { path: 'calendar', loadComponent: () => import('./pages/clinic/calendar/calendar.page').then(m => m.CalendarPage) },
      { path: 'consultations/new', canActivate: [staffGuard, medicalWriteGuard], loadComponent: () => import('./pages/clinic/consultations/consultation.page').then(m => m.ConsultationPage) },
      { path: 'messages', loadComponent: () => import('./pages/clinic/messages/messages.page').then(m => m.MessagesPage) },
      { path: 'reports', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/reports/reports.page').then(m => m.ReportsPage) },
      { path: 'settings', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/settings/settings.page').then(m => m.SettingsPage) },
      { path: 'billing', canActivate: [billingAccessGuard], loadComponent: () => import('./pages/clinic/billing/billing.page').then(m => m.BillingPage) },
      { path: 'audit', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/audit/audit.page').then(m => m.ClinicAuditPage) },
      { path: 'team', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/team/team.page').then(m => m.TeamPage) },
      { path: 'branches', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/branches/branches.page').then(m => m.BranchesPage) },
      { path: 'services', canActivate: [staffGuard], loadComponent: () => import('./pages/clinic/services/services.page').then(m => m.ServicesPage) },
      { path: 'profile', loadComponent: () => import('./pages/clinic/profile/profile.page').then(m => m.ProfilePage) },
      { path: 'admin', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'admin/tenants', canActivate: [superAdminGuard], loadComponent: () => import('./pages/admin/tenants.page').then(m => m.AdminTenantsPage) },
      { path: 'admin/plans', canActivate: [superAdminGuard], loadComponent: () => import('./pages/admin/plans.page').then(m => m.AdminPlansPage) },
      { path: 'admin/subscriptions', canActivate: [superAdminGuard], loadComponent: () => import('./pages/admin/subscriptions.page').then(m => m.AdminSubscriptionsPage) },
      { path: 'admin/users', canActivate: [superAdminGuard], loadComponent: () => import('./pages/admin/users.page').then(m => m.AdminUsersPage) },
      { path: 'admin/audit', canActivate: [superAdminGuard], loadComponent: () => import('./pages/admin/audit.page').then(m => m.AdminAuditPage) }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
