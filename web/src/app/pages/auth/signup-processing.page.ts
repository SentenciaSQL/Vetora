import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SignupService } from '../../core/services/signup.service';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../core/http-error';

@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 text-center">
      <h1 class="font-display text-2xl font-semibold">{{ 'signup.processingTitle' | translate }}</h1>
      <p class="mt-3 text-sm text-slate-500">{{ message() }}</p>
      @if (error()) {
        <p class="mt-3 text-sm text-rose-600">{{ error() }}</p>
      }
      @if (timedOut()) {
        <div class="mt-6 flex flex-col items-center gap-3">
          <button type="button" class="btn-primary" (click)="retry()">{{ 'signup.retryConfirm' | translate }}</button>
          <p class="text-sm text-slate-500">{{ 'signup.contactSupport' | translate }}</p>
        </div>
      }
      <p class="mt-6 flex justify-center gap-4 text-sm">
        <a routerLink="/billing" class="text-brand-700 underline hover:text-brand-800">{{ 'nav.billing' | translate }}</a>
        <a routerLink="/login" class="text-brand-700 underline hover:text-brand-800">{{ 'auth.hasAccount' | translate }}</a>
      </p>
    </div>
  `
})
export class SignupProcessingPage implements OnInit, OnDestroy {
  private signup = inject(SignupService);
  private auth = inject(AuthService);
  private router = inject(Router);
  message = signal('Estamos confirmando su suscripción. Esto puede tardar unos segundos.');
  error = signal('');
  timedOut = signal(false);
  private timer?: ReturnType<typeof setInterval>;
  private attempts = 0;

  ngOnInit(): void {
    if (this.auth.onboardingComplete() || this.auth.accessGranted()) {
      void this.router.navigateByUrl('/dashboard');
      return;
    }
    this.poll();
    this.timer = setInterval(() => this.poll(), 2500);
  }

  ngOnDestroy(): void {
    this.stop();
  }

  retry(): void {
    this.timedOut.set(false);
    this.attempts = 0;
    this.error.set('');
    this.message.set('Estamos confirmando su suscripción. Esto puede tardar unos segundos.');
    this.poll();
    if (!this.timer) {
      this.timer = setInterval(() => this.poll(), 2500);
    }
  }

  private poll(): void {
    this.attempts += 1;
    this.signup.status().subscribe({
      next: status => {
        const ready = status.accessGranted
          || status.onboardingComplete
          || ['TRIAL', 'TRIALING', 'ACTIVE'].includes(status.tenantStatus || '')
          || ['TRIAL', 'TRIALING', 'ACTIVE'].includes(status.subscriptionStatus || '');
        if (ready) {
          this.stop();
          this.signup.refreshOwnerSession().subscribe({
            next: () => {
              this.auth.reloadProfile().subscribe({
                next: () => void this.router.navigateByUrl('/dashboard'),
                error: () => void this.router.navigateByUrl('/dashboard')
              });
            },
            error: () => void this.router.navigateByUrl('/dashboard')
          });
          return;
        }
        if (['PAST_DUE', 'GRACE_PERIOD'].includes(status.subscriptionStatus || '')) {
          this.message.set('El pago quedó pendiente. Puede corregirlo desde facturación.');
        }
        if (this.attempts >= 16) {
          this.timedOut.set(true);
          this.message.set('La confirmación está tardando más de lo habitual. Puede reintentar o contactar a soporte.');
          this.stop();
        }
      },
      error: err => {
        this.error.set(apiErrorMessage(err, 'No se pudo consultar el estado de la suscripción'));
        if (this.attempts >= 16) {
          this.timedOut.set(true);
          this.stop();
        }
      }
    });
  }

  private stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }
}
