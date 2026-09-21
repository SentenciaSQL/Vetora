import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { CheckoutSession, InvitePreview, SignupConfig, SignupStatus, StaffInvite, TokenResponse } from '../models';
import { BillingCycle } from './billing.service';

@Injectable({ providedIn: 'root' })
export class SignupService {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  config(): Observable<SignupConfig> {
    return this.api.get<SignupConfig>('/public/signup-config');
  }

  slugAvailable(slug: string): Observable<{ slug: string; available: boolean }> {
    return this.api.get<{ slug: string; available: boolean }>('/public/slug-available', { slug });
  }

  suggestSlug(name: string): Observable<{ slug: string; available: boolean }> {
    return this.api.get<{ slug: string; available: boolean }>('/signup/suggest-slug', { name });
  }

  register(payload: Record<string, unknown>): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/register-clinic', payload).pipe(
      tap(response => this.auth.store(response, true))
    );
  }

  verifyEmail(token: string): Observable<SignupStatus> {
    return this.api.post<SignupStatus>('/auth/verify-email', { token });
  }

  resendVerification(email: string) {
    return this.api.post('/auth/resend-verification', { email });
  }

  completeClinic(payload: Record<string, unknown>): Observable<SignupStatus> {
    return this.api.post<SignupStatus>('/signup/clinic', payload).pipe(
      tap(status => this.syncUser(status))
    );
  }

  checkout(planId: number, billingCycle: BillingCycle): Observable<CheckoutSession> {
    return this.api.post<CheckoutSession>('/signup/checkout', { planId, billingCycle });
  }

  status(): Observable<SignupStatus> {
    return this.api.get<SignupStatus>('/signup/status').pipe(
      tap(status => this.syncUser(status))
    );
  }

  refreshOwnerSession(): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/signup/session', {}).pipe(
      tap(response => this.auth.store(response, false))
    );
  }

  listInvites(): Observable<StaffInvite[]> {
    return this.api.get<StaffInvite[]>('/employees/invites');
  }

  invite(payload: { email: string; role: string; firstName?: string; lastName?: string }): Observable<StaffInvite> {
    return this.api.post<StaffInvite>('/employees/invites', payload);
  }

  cancelInvite(id: number) {
    return this.api.post(`/employees/invites/${id}/cancel`, {});
  }

  invitePreview(token: string): Observable<InvitePreview> {
    return this.api.get<InvitePreview>('/auth/invite', { token });
  }

  acceptInvite(payload: Record<string, unknown>): Observable<TokenResponse> {
    return this.api.post<TokenResponse>('/auth/accept-invite', payload).pipe(
      tap(response => this.auth.store(response, true))
    );
  }

  private syncUser(status: SignupStatus): void {
    if (status.user) {
      this.auth.applyUser(status.user);
    }
  }
}
