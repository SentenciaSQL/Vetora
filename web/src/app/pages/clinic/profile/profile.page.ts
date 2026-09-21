import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { SessionInactivityService } from '../../../core/services/session-inactivity.service';
import { apiErrorMessage } from '../../../core/http-error';

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
    @if (!auth.isSuperAdmin()) {
      <section class="card mt-4 max-w-lg space-y-3 border-rose-200 dark:border-rose-900/60">
        <h2 class="font-medium text-rose-700 dark:text-rose-300">{{ 'profile.security' | translate }}</h2>
        <p class="text-sm text-slate-500">{{ 'profile.deleteHint' | translate }}</p>
        <button type="button" class="rounded-xl border border-rose-300 px-4 py-2 text-sm font-medium text-rose-700 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-200 dark:hover:bg-rose-950/40"
                (click)="openDelete = true">
          {{ 'profile.deleteAccount' | translate }}
        </button>
      </section>
    }
    @if (openDelete) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="closeDelete()">
        <div class="card max-w-lg space-y-3" (click)="$event.stopPropagation()">
          <h2 class="font-display text-lg font-semibold text-rose-700 dark:text-rose-200">{{ 'profile.deleteAccount' | translate }}</h2>
          <p class="text-sm text-slate-600 dark:text-slate-300">{{ 'profile.deleteWarning' | translate }}</p>
          <form class="space-y-3" [formGroup]="del" (ngSubmit)="deleteAccount()">
            <label class="block text-sm">{{ 'profile.current' | translate }}
              <input class="input mt-1" type="password" formControlName="currentPassword" autocomplete="current-password" />
            </label>
            <label class="block text-sm">{{ 'profile.deleteType' | translate }}
              <input class="input mt-1" formControlName="confirmation" autocomplete="off" />
            </label>
            @if (deleteError()) {
              <p class="text-sm text-rose-600">{{ deleteError() }}</p>
            }
            <div class="flex justify-end gap-2">
              <button type="button" class="btn-secondary" (click)="closeDelete()">{{ 'common.cancel' | translate }}</button>
              <button type="submit" class="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                      [disabled]="del.invalid || deleting()">{{ 'profile.deleteConfirm' | translate }}</button>
            </div>
          </form>
        </div>
      </div>
    }
  `
})
export class ProfilePage {
  private authService = inject(AuthService);
  auth = this.authService;
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private session = inject(SessionInactivityService);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  openDelete = false;
  deleting = signal(false);
  deleteError = signal('');
  form = this.fb.group({
    firstName: [this.auth.user()?.firstName || '', Validators.required],
    lastName: [this.auth.user()?.lastName || '', Validators.required],
    phone: [this.auth.user()?.phone || '']
  });
  pwd = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });
  del = this.fb.group({
    currentPassword: ['', Validators.required],
    confirmation: ['', Validators.required]
  });

  save() {
    if (!this.session.ensureActive()) {
      return;
    }
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
    if (!this.session.ensureActive()) {
      return;
    }
    this.api.post('/auth/change-password', this.pwd.value).subscribe({
      next: () => this.toast.show('common.saved'),
      error: err => this.toast.show(apiErrorMessage(err, 'common.error'), true)
    });
  }

  closeDelete() {
    if (this.deleting()) {
      return;
    }
    this.openDelete = false;
    this.deleteError.set('');
    this.del.reset();
  }

  deleteAccount() {
    if (!this.session.ensureActive() || this.del.invalid || this.deleting()) {
      this.del.markAllAsTouched();
      return;
    }
    this.deleting.set(true);
    this.deleteError.set('');
    this.api.post('/account/deletion', {
      currentPassword: this.del.value.currentPassword,
      confirmation: this.del.value.confirmation
    }).subscribe({
      next: () => {
        this.deleting.set(false);
        this.openDelete = false;
        this.authService.endSession('REMOTE');
        this.toast.show('profile.deleteDone');
        void this.router.navigate(['/login'], { queryParams: { accountDeleted: '1' }, replaceUrl: true });
      },
      error: err => {
        this.deleting.set(false);
        this.deleteError.set(apiErrorMessage(err, 'common.error'));
      }
    });
  }
}
