import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'profile.title' | translate }}</h1>
    <form class="card mt-6 max-w-lg space-y-3" [formGroup]="form" (ngSubmit)="save()">
      <input class="input" formControlName="firstName" />
      <input class="input" formControlName="lastName" />
      <input class="input" formControlName="phone" />
      <button class="btn-primary">{{ 'common.save' | translate }}</button>
    </form>
    <form class="card mt-4 max-w-lg space-y-3" [formGroup]="pwd" (ngSubmit)="changePwd()">
      <h2 class="font-medium">{{ 'profile.password' | translate }}</h2>
      <input class="input" type="password" formControlName="currentPassword" [placeholder]="'profile.current' | translate" />
      <input class="input" type="password" formControlName="newPassword" [placeholder]="'profile.next' | translate" />
      <button class="btn-secondary">{{ 'common.save' | translate }}</button>
    </form>
  `
})
export class ProfilePage {
  private auth = inject(AuthService);
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  form = this.fb.group({
    firstName: [this.auth.user()?.firstName || '', Validators.required],
    lastName: [this.auth.user()?.lastName || '', Validators.required],
    phone: [this.auth.user()?.phone || '']
  });
  pwd = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  save() {
    this.auth.patchMe({
      firstName: this.form.value.firstName || '',
      lastName: this.form.value.lastName || '',
      phone: this.form.value.phone || ''
    }).subscribe({
      next: () => this.toast.show('common.saved'),
      error: () => this.toast.show('common.error', true)
    });
  }

  changePwd() {
    this.api.post('/auth/change-password', this.pwd.value).subscribe({
      next: () => this.toast.show('common.saved'),
      error: () => this.toast.show('common.error', true)
    });
  }
}
