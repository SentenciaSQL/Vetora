import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SignupService } from '../../core/services/signup.service';
import { InvitePreview } from '../../core/models';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6">
      <h1 class="font-display text-2xl font-semibold">{{ 'signup.inviteTitle' | translate }}</h1>
      @if (preview(); as invite) {
        <p class="mt-2 text-sm text-slate-500">{{ invite.tenantName }} · {{ invite.role }}</p>
        @if (invite.expired || invite.accepted) {
          <p class="mt-4 text-sm text-rose-600">{{ 'signup.inviteInvalid' | translate }}</p>
        } @else {
          <form class="mt-6 space-y-3" [formGroup]="form" (ngSubmit)="submit()">
            <label class="block text-sm font-medium">{{ 'auth.firstName' | translate }}
              <input class="input mt-1" formControlName="firstName" [placeholder]="'team.firstNamePlaceholder' | translate" />
            </label>
            <label class="block text-sm font-medium">{{ 'auth.lastName' | translate }}
              <input class="input mt-1" formControlName="lastName" [placeholder]="'team.lastNamePlaceholder' | translate" />
            </label>
            <label class="block text-sm font-medium">{{ 'auth.password' | translate }}
              <input class="input mt-1" type="password" formControlName="password" />
            </label>
            <label class="block text-sm font-medium">{{ 'signup.confirmPassword' | translate }}
              <input class="input mt-1" type="password" formControlName="confirmPassword" />
            </label>
            @if (error()) { <p class="text-sm text-rose-600">{{ error() }}</p> }
            <button class="btn-primary w-full" [disabled]="busy()">{{ 'signup.acceptInvite' | translate }}</button>
          </form>
        }
      }
      <a routerLink="/login" class="mt-4 text-sm text-brand-700">{{ 'auth.login' | translate }}</a>
    </div>
  `
})
export class AcceptInvitePage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private signup = inject(SignupService);
  private fb = inject(FormBuilder);
  preview = signal<InvitePreview | null>(null);
  error = signal('');
  busy = signal(false);
  form = this.fb.group({
    firstName: [''],
    lastName: [''],
    password: ['', [Validators.minLength(8)]],
    confirmPassword: ['']
  });

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.error.set('Invitación no válida');
      return;
    }
    this.signup.invitePreview(token).subscribe({
      next: preview => this.preview.set(preview),
      error: () => this.error.set('Invitación no válida')
    });
  }

  submit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.signup.acceptInvite({ token, ...this.form.getRawValue() }).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: err => {
        this.busy.set(false);
        this.error.set(err.error?.message || 'No se pudo aceptar la invitación');
      }
    });
  }
}
