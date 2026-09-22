import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { concat, forkJoin, Observable, of } from 'rxjs';
import { last, switchMap } from 'rxjs/operators';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { BrandingService } from '../../../core/services/branding.service';
import { AuthService } from '../../../core/services/auth.service';
import { SessionInactivityService } from '../../../core/services/session-inactivity.service';
import { Branding } from '../../../core/models';
import { TENANT_CURRENCIES } from '../../../core/money';
import { BusinessHoursEditorComponent } from './business-hours-editor.component';
import { ImageUploadComponent } from '../../../shared/ui/image-upload.component';

const TIMEZONES = [
  'America/Santo_Domingo',
  'America/New_York',
  'America/Mexico_City',
  'America/Bogota',
  'America/Lima',
  'America/Santiago',
  'America/Argentina/Buenos_Aires',
  'Europe/Madrid',
  'UTC'
];

const DATE_FORMATS = ['dd/MM/yyyy', 'MM/dd/yyyy', 'yyyy-MM-dd'];

interface LogoStage {
  file: File | null;
  preview: string | null;
  remove: boolean;
}

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, BusinessHoursEditorComponent, ImageUploadComponent],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'settings.title' | translate }}</h1>
    <p class="mt-1 text-sm text-slate-500">{{ 'settings.subtitle' | translate }}</p>

    <form class="mt-6 max-w-3xl space-y-6" [formGroup]="form" (ngSubmit)="save()">
      <section class="card space-y-4">
        <div>
          <h2 class="font-medium">{{ 'settings.identity' | translate }}</h2>
          <p class="text-sm text-slate-500">{{ 'settings.identityHint' | translate }}</p>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-name">{{ 'settings.name' | translate }} <span class="text-rose-600">*</span></label>
          <input id="clinic-name" class="input mt-1" formControlName="name" [placeholder]="'settings.namePlaceholder' | translate" />
          @if (invalid('name')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-commercial">{{ 'settings.commercial' | translate }}</label>
          <input id="clinic-commercial" class="input mt-1" formControlName="commercialName" [placeholder]="'settings.commercialPlaceholder' | translate" />
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-email">{{ 'settings.email' | translate }} <span class="text-rose-600">*</span></label>
          <input id="clinic-email" class="input mt-1" type="email" formControlName="email" [placeholder]="'settings.emailPlaceholder' | translate" />
          @if (invalid('email')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.email' | translate }}</p> }
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-phone">{{ 'settings.phone' | translate }}</label>
          <input id="clinic-phone" class="input mt-1" formControlName="phone" [placeholder]="'settings.phonePlaceholder' | translate" />
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-website">{{ 'settings.website' | translate }}</label>
          <input id="clinic-website" class="input mt-1" formControlName="website" placeholder="https://" />
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-address">{{ 'settings.address' | translate }}</label>
          <textarea id="clinic-address" class="input mt-1" rows="2" formControlName="address"></textarea>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-description">{{ 'settings.description' | translate }}</label>
          <textarea id="clinic-description" class="input mt-1" rows="3" formControlName="description"></textarea>
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <div>
            <label class="text-sm font-medium" for="clinic-instagram">Instagram</label>
            <input id="clinic-instagram" class="input mt-1" formControlName="instagram" placeholder="@" />
          </div>
          <div>
            <label class="text-sm font-medium" for="clinic-facebook">Facebook</label>
            <input id="clinic-facebook" class="input mt-1" formControlName="facebook" />
          </div>
        </div>
        @if (auth.hasPermission('BRANDING_UPDATE')) {
          <div class="grid gap-4 lg:grid-cols-3">
            <app-image-upload [label]="'settings.logo' | translate" [hint]="'settings.logoHint' | translate" [src]="imageUrl('light')" (selected)="stage('light', $event)" (cleared)="clearImage('light')" />
            <app-image-upload [label]="'settings.logoDark' | translate" [hint]="'settings.logoDarkHint' | translate" [previewDark]="true" [src]="imageUrl('dark')" (selected)="stage('dark', $event)" (cleared)="clearImage('dark')" />
            <app-image-upload [label]="'settings.icon' | translate" [hint]="'settings.iconHint' | translate" [src]="imageUrl('icon')" (selected)="stage('icon', $event)" (cleared)="clearImage('icon')" />
          </div>
        }
      </section>

      <section class="card space-y-4">
        <div>
          <h2 class="font-medium">{{ 'settings.preferences' | translate }}</h2>
          <p class="text-sm text-slate-500">{{ 'settings.preferencesHint' | translate }}</p>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-locale">{{ 'settings.language' | translate }}</label>
          <select id="clinic-locale" class="input mt-1" formControlName="defaultLocale">
            <option value="es">{{ 'settings.spanish' | translate }}</option>
            <option value="en">{{ 'settings.english' | translate }}</option>
          </select>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-currency">{{ 'settings.currency' | translate }}</label>
          <select id="clinic-currency" class="input mt-1" formControlName="currency">
            @for (code of currencies; track code) {
              <option [value]="code">{{ 'currencies.' + code | translate }}</option>
            }
          </select>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-timezone">{{ 'settings.timezone' | translate }}</label>
          <select id="clinic-timezone" class="input mt-1" formControlName="timezone">
            @for (zone of timezones; track zone) {
              <option [value]="zone">{{ zone }}</option>
            }
          </select>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-date">{{ 'settings.dateFormat' | translate }}</label>
          <select id="clinic-date" class="input mt-1" formControlName="dateFormat">
            @for (format of dateFormats; track format) {
              <option [value]="format">{{ format }}</option>
            }
          </select>
        </div>
      </section>

      <section class="card space-y-4">
        <div>
          <h2 class="font-medium">{{ 'settings.ops' | translate }}</h2>
          <p class="text-sm text-slate-500">{{ 'settings.opsHint' | translate }}</p>
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-slot">{{ 'settings.slot' | translate }}</label>
          <input id="clinic-slot" class="input mt-1" type="number" formControlName="defaultAppointmentMin" />
        </div>
        <div>
          <label class="text-sm font-medium" for="clinic-cancel">{{ 'settings.cancelHours' | translate }}</label>
          <input id="clinic-cancel" class="input mt-1" type="number" formControlName="cancellationHours" />
        </div>
        <label class="flex items-center gap-2 text-sm"><input type="checkbox" formControlName="notifyEmail" /> {{ 'settings.notifyEmail' | translate }}</label>
        <label class="flex items-center gap-2 text-sm"><input type="checkbox" formControlName="notifyPush" /> {{ 'settings.notifyPush' | translate }}</label>
      </section>

      @if (canSave()) {
        <button class="btn-primary" type="submit">{{ 'common.save' | translate }}</button>
      }
    </form>
    <app-business-hours-editor />
  `
})
export class SettingsPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private branding = inject(BrandingService);
  auth = inject(AuthService);
  private session = inject(SessionInactivityService);
  private fb = inject(FormBuilder);
  currencies: string[] = [...TENANT_CURRENCIES];
  timezones = TIMEZONES;
  dateFormats = DATE_FORMATS;
  submitted = false;
  brand = signal<Branding | null>(null);
  logos = signal<Record<string, LogoStage>>({});
  form = this.fb.group({
    name: ['', Validators.required],
    commercialName: [''],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    website: [''],
    address: [''],
    description: [''],
    instagram: [''],
    facebook: [''],
    defaultLocale: ['es'],
    currency: ['DOP'],
    timezone: ['America/Santo_Domingo'],
    dateFormat: ['dd/MM/yyyy'],
    defaultAppointmentMin: [30],
    cancellationHours: [12],
    notifyEmail: [true],
    notifyPush: [true]
  });

  ngOnInit(): void {
    this.api.get<Branding>('/settings/branding').subscribe(value => {
      this.brand.set(value);
      this.form.patchValue({
        ...value,
        defaultLocale: value.primaryLanguage || 'es',
        currency: (value.currency || 'DOP').toUpperCase(),
        timezone: value.timezone || 'America/Santo_Domingo'
      });
      this.ensureOption('timezone', value.timezone);
      this.ensureCurrency(value.currency);
    });
    this.api.get<any>('/settings').subscribe(value => this.form.patchValue(value));
  }

  canSave(): boolean {
    return this.auth.hasPermission('BRANDING_UPDATE') || this.auth.hasPermission('SETTINGS_UPDATE');
  }

  invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  imageUrl(variant: string): string | null {
    const staged = this.logos()[variant];
    if (staged) {
      return staged.preview;
    }
    const current = this.brand();
    if (variant === 'dark') return current?.darkLogoUrl || null;
    if (variant === 'icon') return current?.iconUrl || null;
    return current?.logoUrl || null;
  }

  stage(variant: string, file: File): void {
    this.logos.update(current => ({
      ...current,
      [variant]: { file, preview: URL.createObjectURL(file), remove: false }
    }));
  }

  clearImage(variant: string): void {
    this.logos.update(current => ({
      ...current,
      [variant]: { file: null, preview: null, remove: true }
    }));
  }

  save(): void {
    this.submitted = true;
    if (!this.session.ensureActive() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const calls: Observable<unknown>[] = [];
    if (this.auth.hasPermission('BRANDING_UPDATE')) {
      calls.push(this.api.put<Branding>('/settings/branding', {
        name: value.name,
        commercialName: value.commercialName,
        email: value.email,
        phone: value.phone,
        website: value.website,
        address: value.address,
        description: value.description,
        instagram: value.instagram,
        facebook: value.facebook,
        timezone: value.timezone,
        currency: value.currency,
        defaultLocale: value.defaultLocale
      }));
    }
    if (this.auth.hasPermission('SETTINGS_UPDATE')) {
      calls.push(this.api.put('/settings', {
        dateFormat: value.dateFormat,
        defaultAppointmentMin: value.defaultAppointmentMin,
        cancellationHours: value.cancellationHours,
        notifyEmail: value.notifyEmail,
        notifyPush: value.notifyPush
      }));
    }
    const source = calls.length ? forkJoin(calls) : of([]);
    source.pipe(switchMap(results => {
      const brand = results.find(item => item && typeof item === 'object' && 'tenantId' in (item as object)) as Branding | undefined;
      if (brand) {
        this.brand.set(brand);
        this.branding.branding.set(brand);
      }
      return this.persistLogos();
    })).subscribe({
      next: brand => {
        if (brand) {
          this.brand.set(brand);
          this.branding.branding.set(brand);
        }
        this.logos.set({});
        this.toast.show('common.saved');
      },
      error: () => this.toast.show('common.error', true)
    });
  }

  private persistLogos(): Observable<Branding | null> {
    const staged = this.logos();
    const tasks: Observable<Branding>[] = [];
    for (const variant of ['light', 'dark', 'icon']) {
      const item = staged[variant];
      if (!item || !this.auth.hasPermission('BRANDING_UPDATE')) {
        continue;
      }
      if (item.file) {
        tasks.push(this.api.upload<Branding>('/settings/branding/logo', item.file, { variant }));
      } else if (item.remove) {
        tasks.push(this.api.delete<Branding>(`/settings/branding/logo/${variant}`));
      }
    }
    if (!tasks.length) {
      return this.api.get<Branding>('/settings/branding');
    }
    return concat(...tasks).pipe(
      last(),
      switchMap(() => this.api.get<Branding>('/settings/branding'))
    );
  }

  private ensureCurrency(value?: string | null): void {
    const code = (value || '').toUpperCase();
    if (code && !this.currencies.includes(code)) {
      this.currencies = [code, ...this.currencies];
      this.form.patchValue({ currency: code });
    }
  }

  private ensureOption(control: 'timezone', value?: string | null): void {
    if (value && !this.timezones.includes(value)) {
      this.timezones = [value, ...this.timezones];
      this.form.patchValue({ [control]: value });
    }
  }
}
