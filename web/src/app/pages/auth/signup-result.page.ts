import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div class="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 text-center">
      <h1 class="font-display text-2xl font-semibold">{{ 'signup.successTitle' | translate }}</h1>
      <p class="mt-3 text-sm text-slate-500">{{ 'signup.successBody' | translate }}</p>
      <a [routerLink]="auth.homePath()" class="btn-primary mx-auto mt-6">
        {{ 'signup.goDashboard' | translate }}
      </a>
    </div>
  `
})
export class SignupResultPage {
  auth = inject(AuthService);
}
