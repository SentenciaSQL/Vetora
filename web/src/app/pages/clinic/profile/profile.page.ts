import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { SessionInactivityService } from '../../../core/services/session-inactivity.service';
import { apiErrorMessage } from '../../../core/http-error';
import { UserAvatarComponent } from '../../../shared/ui/user-avatar.component';
import { RoleLabelPipe } from '../../../shared/ui/role-label.pipe';

function matchesPassword(control: AbstractControl): ValidationErrors | null {
  const next = control.get('newPassword')?.value;
  const confirm = control.get('confirmPassword')?.value;
  if (!confirm) {
    return null;
  }
  return next === confirm ? null : { mismatch: true };
}

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, UserAvatarComponent, RoleLabelPipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'profile.title' | translate }}</h1>

    <section class="card mt-6 max-w-lg">
      <div class="flex flex-col items-center gap-3 text-center">
        <app-user-avatar class="h-24 w-24 text-2xl" [url]="preview() || auth.user()?.avatarUrl" [name]="auth.user()?.fullName" />
        <div>
          <p class="font-medium">{{ auth.user()?.fullName }}</p>
          <p class="text-sm text-slate-500">{{ (auth.user()?.role || auth.user()?.roles?.[0]) | roleLabel }}</p>
          <p class="text-sm text-slate-500">{{ auth.user()?.email }}</p>
        </div>
        <div class="flex flex-wrap justify-center gap-2">
          <button type="button" class="btn-secondary text-xs" (click)="file.click()">{{ 'profile.changePhoto' | translate }}</button>
          @if (auth.user()?.avatarUrl || preview()) {
            <button type="button" class="btn-secondary text-xs" (click)="removePhoto()">{{ 'profile.removePhoto' | translate }}</button>
          }
        </div>
        <input #file class="sr-only" type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" (change)="onPhoto($event)" />
      </div>
    </section>

    <form class="card mt-4 max-w-lg space-y-3" [formGroup]="form" (ngSubmit)="save()">
      <h2 class="font-medium">{{ 'profile.personal' | translate }}</h2>
      <div>
        <label class="text-sm font-medium" for="profile-first">{{ 'profile.firstName' | translate }} <span class="text-rose-600">*</span></label>
        <input id="profile-first" class="input mt-1" formControlName="firstName" [placeholder]="'profile.firstNamePlaceholder' | translate" />
        @if (form.controls.firstName.invalid && form.controls.firstName.touched) {
          <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p>
        }
      </div>
      <div>
        <label class="text-sm font-medium" for="profile-last">{{ 'profile.lastName' | translate }} <span class="text-rose-600">*</span></label>
        <input id="profile-last" class="input mt-1" formControlName="lastName" [placeholder]="'profile.lastNamePlaceholder' | translate" />
        @if (form.controls.lastName.invalid && form.controls.lastName.touched) {
          <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p>
        }
      </div>
      <div>
        <label class="text-sm font-medium" for="profile-email">{{ 'profile.email' | translate }} <span class="text-rose-600">*</span></label>
        <input id="profile-email" class="input mt-1 bg-slate-50 dark:bg-slate-950" [value]="auth.user()?.email || ''" readonly />
        <p class="mt-1 text-xs text-slate-500">{{ 'profile.emailReadonly' | translate }}</p>
      </div>
      <div>
        <label class="text-sm font-medium" for="profile-phone">{{ 'profile.phone' | translate }}</label>
        <input id="profile-phone" class="input mt-1" formControlName="phone" />
      </div>
      <button class="btn-primary" type="submit">{{ 'common.save' | translate }}</button>
    </form>

    <form class="card mt-4 max-w-lg space-y-3" [formGroup]="pwd" (ngSubmit)="changePwd()">
      <h2 class="font-medium">{{ 'profile.password' | translate }}</h2>
      <div>
        <label class="text-sm font-medium" for="pwd-current">{{ 'profile.current' | translate }} <span class="text-rose-600">*</span></label>
        <div class="relative mt-1">
          <input id="pwd-current" class="input pr-16" [type]="showCurrent() ? 'text' : 'password'" formControlName="currentPassword" autocomplete="current-password" />
          <button type="button" class="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center text-slate-500" (click)="showCurrent.set(!showCurrent())" [attr.aria-label]="(showCurrent() ? 'profile.hidePassword' : 'profile.showPassword') | translate">
            <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>
          </button>
        </div>
      </div>
      <div>
        <label class="text-sm font-medium" for="pwd-next">{{ 'profile.next' | translate }} <span class="text-rose-600">*</span></label>
        <div class="relative mt-1">
          <input id="pwd-next" class="input pr-16" [type]="showNext() ? 'text' : 'password'" formControlName="newPassword" autocomplete="new-password" />
          <button type="button" class="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center text-slate-500" (click)="showNext.set(!showNext())" [attr.aria-label]="(showNext() ? 'profile.hidePassword' : 'profile.showPassword') | translate">
            <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>
          </button>
        </div>
        @if (pwd.controls.newPassword.invalid && pwd.controls.newPassword.touched) {
          <p class="mt-1 text-xs text-rose-600">{{ 'validation.password' | translate }}</p>
        }
      </div>
      <div>
        <label class="text-sm font-medium" for="pwd-confirm">{{ 'profile.confirm' | translate }} <span class="text-rose-600">*</span></label>
        <div class="relative mt-1">
          <input id="pwd-confirm" class="input pr-16" [type]="showConfirm() ? 'text' : 'password'" formControlName="confirmPassword" autocomplete="new-password" />
          <button type="button" class="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center text-slate-500" (click)="showConfirm.set(!showConfirm())" [attr.aria-label]="(showConfirm() ? 'profile.hidePassword' : 'profile.showPassword') | translate">
            <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>
          </button>
        </div>
        @if (pwd.hasError('mismatch') && pwd.controls.confirmPassword.touched) {
          <p class="mt-1 text-xs text-rose-600">{{ 'profile.mismatch' | translate }}</p>
        }
      </div>
      <button class="btn-secondary" type="submit">{{ 'profile.password' | translate }}</button>
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
          <h2 class="font-display text-lg font-semibold text-rose-700 dark:text-rose-200">{{ 'profile.deleteQuestion' | translate }}</h2>
          <p class="text-sm text-slate-600 dark:text-slate-300">{{ 'profile.deleteWarning' | translate }}</p>
          <form class="space-y-3" [formGroup]="del" (ngSubmit)="deleteAccount()">
            <label class="block text-sm font-medium" for="delete-password">{{ 'profile.current' | translate }} <span class="text-rose-600">*</span>
              <input id="delete-password" class="input mt-1" type="password" formControlName="currentPassword" autocomplete="current-password" />
            </label>
            <label class="block text-sm font-medium" for="delete-confirm">{{ 'profile.deleteType' | translate }}
              <input id="delete-confirm" class="input mt-1" formControlName="confirmation" autocomplete="off" placeholder="ELIMINAR" />
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
  auth = inject(AuthService);
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private session = inject(SessionInactivityService);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  openDelete = false;
  deleting = signal(false);
  deleteError = signal('');
  preview = signal<string | null>(null);
  showCurrent = signal(false);
  showNext = signal(false);
  showConfirm = signal(false);
  form = this.fb.group({
    firstName: [this.auth.user()?.firstName || '', Validators.required],
    lastName: [this.auth.user()?.lastName || '', Validators.required],
    phone: [this.auth.user()?.phone || '']
  });
  pwd = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  }, { validators: matchesPassword });
  del = this.fb.group({
    currentPassword: ['', Validators.required],
    confirmation: ['', Validators.required]
  });

  save(): void {
    if (!this.session.ensureActive() || this.form.invalid) {
      this.form.markAllAsTouched();
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

  onPhoto(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    (event.target as HTMLInputElement).value = '';
    if (!file || !this.session.ensureActive()) {
      return;
    }
    const allowed = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp'
      || /\.(jpe?g|png|webp)$/i.test(file.name);
    if (!allowed || file.size > 5 * 1024 * 1024) {
      this.toast.show('uploads.invalid', true);
      return;
    }
    this.preview.set(URL.createObjectURL(file));
    this.auth.uploadAvatar(file).subscribe({
      next: () => {
        this.preview.set(null);
        this.toast.show('common.saved');
      },
      error: () => {
        this.preview.set(null);
        this.toast.show('uploads.invalid', true);
      }
    });
  }

  removePhoto(): void {
    if (!this.session.ensureActive()) {
      return;
    }
    this.preview.set(null);
    this.auth.clearAvatar().subscribe({
      next: () => this.toast.show('common.saved'),
      error: () => this.toast.show('common.error', true)
    });
  }

  changePwd(): void {
    if (!this.session.ensureActive() || this.pwd.invalid) {
      this.pwd.markAllAsTouched();
      return;
    }
    this.api.post('/auth/change-password', {
      currentPassword: this.pwd.value.currentPassword,
      newPassword: this.pwd.value.newPassword
    }).subscribe({
      next: () => {
        this.pwd.reset();
        this.toast.show('common.saved');
      },
      error: err => this.toast.show(apiErrorMessage(err, 'common.error'), true)
    });
  }

  closeDelete(): void {
    if (this.deleting()) {
      return;
    }
    this.openDelete = false;
    this.deleteError.set('');
    this.del.reset();
  }

  deleteAccount(): void {
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
        this.auth.endSession('REMOTE');
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
