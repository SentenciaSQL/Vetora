import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { BrandingService } from '../../../core/services/branding.service';
import { AuthService } from '../../../core/services/auth.service';
import { Branding } from '../../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'settings.title' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ 'settings.subtitle' | translate }}</p>
    <form class="card mt-6 max-w-2xl space-y-4" [formGroup]="form" (ngSubmit)="save()">
      <input class="input" formControlName="name" [placeholder]="'settings.name' | translate" />
      <input class="input" formControlName="commercialName" [placeholder]="'settings.commercial' | translate" />
      <input class="input" formControlName="email" />
      <input class="input" formControlName="phone" />
      <input class="input" formControlName="website" />
      <textarea class="input" formControlName="address"></textarea>
      <div class="grid grid-cols-2 gap-3">
        <input class="input" formControlName="instagram" placeholder="Instagram" />
        <input class="input" formControlName="facebook" placeholder="Facebook" />
      </div>
      @if (auth.hasPermission('BRANDING_UPDATE')) {
        <div class="grid gap-3 sm:grid-cols-3">
          <label class="text-sm">{{ 'settings.logo' | translate }}
            <input class="mt-1 block w-full text-sm" type="file" accept="image/*" (change)="upload($event, 'light')" />
          </label>
          <label class="text-sm">{{ 'settings.logoDark' | translate }}
            <input class="mt-1 block w-full text-sm" type="file" accept="image/*" (change)="upload($event, 'dark')" />
          </label>
          <label class="text-sm">{{ 'settings.icon' | translate }}
            <input class="mt-1 block w-full text-sm" type="file" accept="image/*" (change)="upload($event, 'icon')" />
          </label>
        </div>
        <button class="btn-primary">{{ 'common.save' | translate }}</button>
      }
    </form>
    <form class="card mt-6 max-w-2xl space-y-4" [formGroup]="ops" (ngSubmit)="saveOps()">
      <h2 class="font-medium">{{ 'settings.ops' | translate }}</h2>
      <label class="text-sm">{{ 'settings.slot' | translate }}
        <input class="input mt-1" type="number" formControlName="defaultAppointmentMin" />
      </label>
      <label class="text-sm">{{ 'settings.cancelHours' | translate }}
        <input class="input mt-1" type="number" formControlName="cancellationHours" />
      </label>
      <label class="text-sm">{{ 'settings.dateFormat' | translate }}
        <input class="input mt-1" formControlName="dateFormat" />
      </label>
      <label class="flex items-center gap-2 text-sm"><input type="checkbox" formControlName="notifyEmail" /> {{ 'settings.notifyEmail' | translate }}</label>
      <label class="flex items-center gap-2 text-sm"><input type="checkbox" formControlName="notifyPush" /> {{ 'settings.notifyPush' | translate }}</label>
      @if (auth.hasPermission('SETTINGS_UPDATE')) {
        <button class="btn-primary">{{ 'common.save' | translate }}</button>
      }
    </form>
  `
})
export class SettingsPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private branding = inject(BrandingService);
  auth = inject(AuthService);
  private fb = inject(FormBuilder);
  form = this.fb.group({
    name: [''], commercialName: [''], email: [''], phone: [''], website: [''], address: [''], instagram: [''], facebook: ['']
  });
  ops = this.fb.group({
    dateFormat: ['dd/MM/yyyy'],
    defaultAppointmentMin: [30],
    cancellationHours: [12],
    notifyEmail: [true],
    notifyPush: [true]
  });

  ngOnInit() {
    this.api.get<Branding>('/settings/branding').subscribe(b => this.form.patchValue(b));
    this.api.get<any>('/settings').subscribe(s => this.ops.patchValue(s));
  }

  save() {
    this.api.put<Branding>('/settings/branding', this.form.value).subscribe({
      next: (b) => { this.branding.branding.set(b); this.toast.show('common.saved'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  upload(event: Event, variant: string) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.upload<Branding>('/settings/branding/logo', file, { variant }).subscribe({
      next: (b) => { this.branding.branding.set(b); this.toast.show('common.saved'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveOps() {
    this.api.put('/settings', this.ops.value).subscribe({
      next: () => this.toast.show('common.saved'),
      error: () => this.toast.show('common.error', true)
    });
  }
}
