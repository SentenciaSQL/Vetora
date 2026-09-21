import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SignupService } from '../../core/services/signup.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6">
      <h1 class="font-display text-2xl font-semibold">{{ 'signup.verifyTitle' | translate }}</h1>
      <p class="mt-2 text-sm text-slate-500">{{ message() | translate }}</p>
      @if (verified()) {
        <p class="mt-3 text-sm text-emerald-700">{{ 'signup.verifySuccess' | translate }}</p>
      }
      @if (error()) {
        <p class="mt-3 text-sm text-rose-600">{{ error() }}</p>
      }
      @if (!verified() && !auth.user()?.email) {
        <form class="mt-6 space-y-3" [formGroup]="resendForm" (ngSubmit)="resend()">
          <input class="input" type="email" formControlName="email" [placeholder]="'auth.email' | translate" />
          <button class="btn-primary w-full" [disabled]="resendForm.invalid || busy()">{{ 'signup.resend' | translate }}</button>
        </form>
      }
      <div class="mt-6 flex flex-wrap gap-3">
        @if (verified() && auth.isTenantOwner()) {
          <button class="btn-primary" (click)="continueClinic()">{{ 'signup.continueClinic' | translate }}</button>
        }
        @if (verified()) {
          <a routerLink="/login" class="btn-secondary">{{ 'auth.login' | translate }}</a>
        } @else {
          @if (auth.user()?.email) {
            <button class="btn-primary" [disabled]="busy()" (click)="resend()">{{ 'signup.resend' | translate }}</button>
          }
          <a routerLink="/login" class="btn-secondary">{{ 'auth.login' | translate }}</a>
        }
      </div>
    </div>
  `
})
export class VerifyEmailPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private signup = inject(SignupService);
  private fb = inject(FormBuilder);
  auth = inject(AuthService);
  message = signal('signup.verifyPending');
  error = signal('');
  busy = signal(false);
  verified = signal(false);
  resendForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      return;
    }
    this.signup.verifyEmail(token).subscribe({
      next: () => {
        this.verified.set(true);
        this.message.set('signup.verifySuccess');
        this.error.set('');
        this.auth.reloadProfile().subscribe({ error: () => undefined });
      },
      error: err => {
        this.verified.set(false);
        this.error.set(err.error?.message || 'El enlace no es válido o expiró');
      }
    });
  }

  continueClinic(): void {
    void this.router.navigateByUrl('/register-clinic');
  }

  resend(): void {
    const email = this.auth.user()?.email || this.resendForm.value.email;
    if (!email || this.busy()) {
      this.message.set('signup.resendGeneric');
      return;
    }
    this.busy.set(true);
    this.signup.resendVerification(email).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('signup.resendGeneric');
      },
      error: () => {
        this.busy.set(false);
        this.message.set('signup.resendGeneric');
      }
    });
  }
}
