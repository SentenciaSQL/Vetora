import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <h1 class="font-display text-2xl font-semibold">{{ 'auth.createOwner' | translate }}</h1>
      <form class="mt-6 space-y-3" [formGroup]="form" (ngSubmit)="submit()">
        <input class="input" formControlName="firstName" [placeholder]="'auth.firstName' | translate" />
        <input class="input" formControlName="lastName" [placeholder]="'auth.lastName' | translate" />
        <input class="input" type="email" formControlName="email" [placeholder]="'auth.email' | translate" />
        <input class="input" type="password" formControlName="password" [placeholder]="'auth.password' | translate" />
        @if (error()) { <p class="text-sm text-rose-600">{{ error() }}</p> }
        <button class="btn-primary w-full" [disabled]="form.invalid">{{ 'auth.register' | translate }}</button>
      </form>
      <a routerLink="/login" class="mt-4 text-sm text-brand-700">{{ 'auth.hasAccount' | translate }}</a>
    </div>
  `
})
export class RegisterPage {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  error = signal('');
  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    locale: ['es']
  });

  submit(): void {
    this.auth.register(this.form.getRawValue() as Record<string, string>).subscribe({
      next: () => void this.router.navigateByUrl(this.auth.homePath()),
      error: err => this.error.set(err.error?.message || 'common.error')
    });
  }
}
