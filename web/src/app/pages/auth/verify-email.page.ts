import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SignupService } from '../../core/services/signup.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6">
      <h1 class="font-display text-2xl font-semibold">{{ 'signup.verifyTitle' | translate }}</h1>
      <p class="mt-2 text-sm text-slate-500">{{ message() }}</p>
      @if (error()) {
        <p class="mt-3 text-sm text-rose-600">{{ error() }}</p>
      }
      <div class="mt-6 flex flex-wrap gap-3">
        <button class="btn-primary" [disabled]="busy()" (click)="resend()">{{ 'signup.resend' | translate }}</button>
        <a routerLink="/login" class="btn-secondary">{{ 'auth.login' | translate }}</a>
      </div>
    </div>
  `
})
export class VerifyEmailPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private signup = inject(SignupService);
  private auth = inject(AuthService);
  message = signal('Revisa tu correo y abre el enlace de verificación para continuar.');
  error = signal('');
  busy = signal(false);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.signup.verifyEmail(token).subscribe({
        next: () => {
          this.auth.reloadProfile().subscribe({
            next: () => void this.router.navigateByUrl('/register-clinic'),
            error: () => void this.router.navigateByUrl('/register-clinic')
          });
        },
        error: err => this.error.set(err.error?.message || 'El enlace no es válido o expiró')
      });
    }
  }

  resend(): void {
    const email = this.auth.user()?.email;
    if (!email || this.busy()) {
      this.message.set('Si el correo existe, enviaremos un nuevo enlace.');
      return;
    }
    this.busy.set(true);
    this.signup.resendVerification(email).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('Si el correo existe, enviaremos un nuevo enlace.');
      },
      error: () => {
        this.busy.set(false);
        this.message.set('Si el correo existe, enviaremos un nuevo enlace.');
      }
    });
  }
}
