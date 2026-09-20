import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <h1 class="font-display text-2xl font-semibold">{{ 'auth.resetTitle' | translate }}</h1>
      <form class="mt-6 space-y-3" [formGroup]="form" (ngSubmit)="submit()">
        <input class="input" formControlName="token" [placeholder]="'auth.resetToken' | translate" />
        <input class="input" type="password" formControlName="password" [placeholder]="'auth.password' | translate" />
        <button class="btn-primary w-full" [disabled]="form.invalid">{{ 'auth.resetSubmit' | translate }}</button>
      </form>
      @if (done()) {
        <p class="mt-4 text-sm text-emerald-700">{{ 'auth.resetDone' | translate }}</p>
      }
      @if (error()) {
        <p class="mt-4 text-sm text-rose-600">{{ 'common.error' | translate }}</p>
      }
      <a routerLink="/login" class="mt-4 text-sm text-brand-700">{{ 'auth.login' | translate }}</a>
    </div>
  `
})
export class ResetPage implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  done = signal(false);
  error = signal(false);
  form = this.fb.group({
    token: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) this.form.patchValue({ token });
  }

  submit(): void {
    this.error.set(false);
    this.auth.reset(this.form.value.token!, this.form.value.password!).subscribe({
      next: () => this.done.set(true),
      error: () => this.error.set(true)
    });
  }
}
