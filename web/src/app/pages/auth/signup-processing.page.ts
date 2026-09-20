import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SignupService } from '../../core/services/signup.service';

@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 text-center">
      <h1 class="font-display text-2xl font-semibold">{{ 'signup.processingTitle' | translate }}</h1>
      <p class="mt-3 text-sm text-slate-500">{{ message() }}</p>
      <p class="mt-6">
        <a routerLink="/billing" class="text-brand-700 hover:underline">{{ 'nav.billing' | translate }}</a>
      </p>
    </div>
  `
})
export class SignupProcessingPage implements OnInit, OnDestroy {
  private signup = inject(SignupService);
  private router = inject(Router);
  message = signal('Estamos confirmando el pago con Paddle. Esto puede tardar unos segundos.');
  private timer?: ReturnType<typeof setInterval>;
  private attempts = 0;

  ngOnInit(): void {
    this.poll();
    this.timer = setInterval(() => this.poll(), 2500);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  private poll(): void {
    this.attempts += 1;
    this.signup.status().subscribe({
      next: status => {
        if (status.accessGranted || ['TRIAL', 'TRIALING', 'ACTIVE'].includes(status.tenantStatus || '')
            || ['TRIAL', 'TRIALING', 'ACTIVE'].includes(status.subscriptionStatus || '')) {
          void this.router.navigateByUrl('/signup/success');
          return;
        }
        if (['PAST_DUE', 'GRACE_PERIOD'].includes(status.subscriptionStatus || '')) {
          this.message.set('El pago quedó pendiente. Puede corregirlo desde facturación.');
        }
        if (this.attempts >= 12) {
          this.message.set('La confirmación está tardando más de lo habitual. Puede esperar o abrir facturación.');
        }
      }
    });
  }
}
