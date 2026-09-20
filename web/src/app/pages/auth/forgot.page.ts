import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <h1 class="font-display text-2xl font-semibold">{{ 'auth.recover' | translate }}</h1>
      <form class="mt-6 space-y-3" [formGroup]="form" (ngSubmit)="submit()">
        <input class="input" type="email" formControlName="email" [placeholder]="'auth.email' | translate" />
        <button class="btn-primary w-full" [disabled]="form.invalid">{{ 'auth.sendLink' | translate }}</button>
      </form>
      @if (done()) {
        <p class="mt-4 text-sm text-emerald-700">{{ 'auth.sent' | translate }}</p>
      }
      <a routerLink="/login" class="mt-4 text-sm text-brand-700">{{ 'auth.login' | translate }}</a>
    </div>
  `
})
export class ForgotPage {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  done = signal(false);
  form = this.fb.group({ email: ['', [Validators.required, Validators.email]] });

  submit(): void {
    this.auth.forgot(this.form.value.email!).subscribe(() => this.done.set(true));
  }
}
