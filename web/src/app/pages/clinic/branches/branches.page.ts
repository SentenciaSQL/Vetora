import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/services/auth.service';

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

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div class="flex items-center justify-between gap-3">
      <h1 class="font-display text-2xl font-semibold">{{ 'branches.title' | translate }}</h1>
      @if (auth.hasPermission('BRANCH_MANAGE')) {
        <button class="btn-primary" type="button" (click)="startCreate()">{{ 'branches.new' | translate }}</button>
      }
    </div>
    <div class="mt-6 space-y-3">
      @for (b of rows(); track b.id) {
        <div class="card flex items-start justify-between gap-3">
          <div>
            <p class="font-semibold">{{ b.name }}</p>
            <p class="text-sm text-slate-500">{{ b.address }} · {{ b.city }}</p>
            @if (b.phone || b.email) {
              <p class="text-sm text-slate-500">{{ b.phone }} {{ b.email }}</p>
            }
          </div>
          @if (auth.hasPermission('BRANCH_MANAGE')) {
            <button class="btn-secondary text-xs" type="button" (click)="startEdit(b)">{{ 'common.edit' | translate }}</button>
          }
        </div>
      }
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg font-semibold">{{ (editingId ? 'branches.edit' : 'branches.new') | translate }}</h2>
          <div>
            <label class="block text-sm font-medium" for="branch-name">
              {{ 'branches.name' | translate }} <span class="text-rose-600">*</span>
            </label>
            <input id="branch-name" class="input mt-1" formControlName="name" [placeholder]="'branches.namePlaceholder' | translate" />
            @if (showError('name')) {
              <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium" for="branch-address">{{ 'branches.address' | translate }}</label>
            <input id="branch-address" class="input mt-1" formControlName="address" [placeholder]="'branches.addressPlaceholder' | translate" />
          </div>
          <div>
            <label class="block text-sm font-medium" for="branch-city">{{ 'branches.city' | translate }}</label>
            <input id="branch-city" class="input mt-1" formControlName="city" />
          </div>
          <div>
            <label class="block text-sm font-medium" for="branch-country">{{ 'branches.country' | translate }}</label>
            <input id="branch-country" class="input mt-1" formControlName="country" />
          </div>
          <div>
            <label class="block text-sm font-medium" for="branch-phone">{{ 'branches.phone' | translate }}</label>
            <input id="branch-phone" class="input mt-1" formControlName="phone" [placeholder]="'branches.phonePlaceholder' | translate" />
          </div>
          <div>
            <label class="block text-sm font-medium" for="branch-email">{{ 'branches.email' | translate }}</label>
            <input id="branch-email" class="input mt-1" type="email" formControlName="email" [placeholder]="'branches.emailPlaceholder' | translate" />
            @if (showError('email')) {
              <p class="mt-1 text-xs text-rose-600">{{ 'validation.email' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium" for="branch-timezone">{{ 'branches.timezone' | translate }}</label>
            <select id="branch-timezone" class="input mt-1" formControlName="timezone">
              @for (zone of timezones; track zone) {
                <option [value]="zone">{{ zone }}</option>
              }
            </select>
          </div>
          <label class="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" formControlName="active" />
            {{ 'branches.active' | translate }}
          </label>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary" type="submit">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }
  `
})
export class BranchesPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  auth = inject(AuthService);
  rows = signal<any[]>([]);
  open = false;
  submitted = false;
  editingId: number | null = null;
  timezones = TIMEZONES;
  form = this.fb.group({
    name: ['', Validators.required],
    address: [''],
    city: [''],
    country: ['ES'],
    phone: [''],
    email: ['', Validators.email],
    timezone: ['America/Santo_Domingo'],
    active: [true]
  });

  ngOnInit() {
    this.api.get<any[]>('/branches').subscribe(r => this.rows.set(r));
  }

  startCreate(): void {
    this.editingId = null;
    this.submitted = false;
    this.form.reset({
      name: '', address: '', city: '', country: 'ES', phone: '', email: '',
      timezone: 'America/Santo_Domingo', active: true
    });
    this.open = true;
  }

  startEdit(branch: any): void {
    this.editingId = branch.id;
    this.submitted = false;
    this.form.reset({
      name: branch.name || '',
      address: branch.address || '',
      city: branch.city || '',
      country: branch.country || '',
      phone: branch.phone || '',
      email: branch.email || '',
      timezone: branch.timezone || 'America/Santo_Domingo',
      active: branch.active !== false
    });
    this.open = true;
  }

  showError(name: 'name' | 'email'): boolean {
    const control = this.form.controls[name];
    return (this.submitted || control.touched) && control.invalid;
  }

  save(): void {
    this.submitted = true;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.form.getRawValue();
    const request = this.editingId
      ? this.api.put(`/branches/${this.editingId}`, body)
      : this.api.post('/branches', body);
    request.subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.ngOnInit(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }
}
