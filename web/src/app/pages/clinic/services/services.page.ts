import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '../../../shared/ui/money.pipe';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/services/auth.service';
import { BrandingService } from '../../../core/services/branding.service';

const CATEGORIES = ['CONSULTATION', 'VACCINATION', 'CONTROL', 'SURGERY', 'GROOMING', 'HOSPITALIZATION', 'PREVENTION', 'AESTHETICS', 'LABORATORY', 'OTHER'];

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, MoneyPipe],
  template: `
    <div class="flex items-center justify-between gap-3">
      <h1 class="font-display text-2xl font-semibold">{{ 'services.title' | translate }}</h1>
      @if (auth.hasPermission('SERVICE_MANAGE')) {
        <button class="btn-primary" type="button" (click)="startCreate()">{{ 'services.new' | translate }}</button>
      }
    </div>
    <div class="card mt-6 overflow-x-auto p-0">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
          <tr>
            <th class="px-4 py-3">{{ 'services.name' | translate }}</th>
            <th class="px-4 py-3">{{ 'services.duration' | translate }}</th>
            <th class="px-4 py-3">{{ 'services.price' | translate }} ({{ currency() }})</th>
            <th class="px-4 py-3">{{ 'services.category' | translate }}</th>
            @if (auth.hasPermission('SERVICE_MANAGE')) {
              <th class="px-4 py-3 text-right">{{ 'common.actions' | translate }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (s of rows(); track s.id) {
            <tr class="border-t border-slate-100 dark:border-white/5">
              <td class="px-4 py-3 font-medium">{{ displayName(s) }}</td>
              <td class="px-4 py-3 text-slate-500">{{ s.durationMin }} min</td>
              <td class="px-4 py-3">{{ s.price | money:currency() }}</td>
              <td class="px-4 py-3">{{ categoryLabel(s.category) }}</td>
              @if (auth.hasPermission('SERVICE_MANAGE')) {
                <td class="px-4 py-3 text-right">
                  <button class="btn-secondary text-xs" type="button" (click)="startEdit(s)">{{ 'common.edit' | translate }}</button>
                </td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg font-semibold">{{ (editingId ? 'services.edit' : 'services.new') | translate }}</h2>
          <div>
            <label class="block text-sm font-medium" for="service-name">
              {{ 'services.name' | translate }} <span class="text-rose-600">*</span>
            </label>
            <input id="service-name" class="input mt-1" formControlName="nameEs" [placeholder]="'services.namePlaceholder' | translate" />
            @if (showError('nameEs')) {
              <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium" for="service-name-en">{{ 'services.nameEn' | translate }}</label>
            <input id="service-name-en" class="input mt-1" formControlName="nameEn" [placeholder]="'services.namePlaceholder' | translate" />
            <p class="mt-1 text-xs text-slate-500">{{ 'services.nameEnHint' | translate }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium" for="service-description">{{ 'services.description' | translate }}</label>
            <textarea id="service-description" class="input mt-1" rows="2" formControlName="descriptionEs"></textarea>
          </div>
          <div>
            <label class="block text-sm font-medium" for="service-description-en">{{ 'services.descriptionEn' | translate }}</label>
            <textarea id="service-description-en" class="input mt-1" rows="2" formControlName="descriptionEn"></textarea>
          </div>
          <div>
            <label class="block text-sm font-medium" for="service-duration">
              {{ 'services.duration' | translate }} <span class="text-rose-600">*</span>
            </label>
            <input id="service-duration" class="input mt-1" type="number" min="5" max="480" formControlName="durationMin" [placeholder]="'services.durationPlaceholder' | translate" />
            <p class="mt-1 text-xs text-slate-500">{{ 'services.durationHint' | translate }}</p>
            @if (showError('durationMin')) {
              <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium" for="service-price">
              {{ 'services.price' | translate }} ({{ currency() }}) <span class="text-rose-600">*</span>
            </label>
            <input id="service-price" class="input mt-1" type="number" min="0" step="0.01" formControlName="price" [placeholder]="'services.pricePlaceholder' | translate" />
            @if (showError('price')) {
              <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium" for="service-category">{{ 'services.category' | translate }}</label>
            <select id="service-category" class="input mt-1" formControlName="category">
              @for (category of categories; track category) {
                <option [value]="category">{{ 'services.categories.' + category | translate }}</option>
              }
            </select>
          </div>
          <label class="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" formControlName="active" />
            {{ 'services.active' | translate }}
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
export class ServicesPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  private i18n = inject(TranslateService);
  private branding = inject(BrandingService);
  auth = inject(AuthService);
  rows = signal<any[]>([]);
  open = false;
  submitted = false;
  editingId: number | null = null;
  categories = CATEGORIES;
  form = this.fb.group({
    nameEs: ['', Validators.required],
    nameEn: [''],
    descriptionEs: [''],
    descriptionEn: [''],
    durationMin: [30, [Validators.required, Validators.min(5), Validators.max(480)]],
    price: [0, [Validators.required, Validators.min(0)]],
    category: ['CONSULTATION'],
    active: [true]
  });

  ngOnInit() {
    this.branding.loadForSession();
    this.api.get<any[]>('/services').subscribe(r => this.rows.set(r));
  }

  currency(): string {
    return (this.branding.branding()?.currency || 'DOP').toUpperCase();
  }

  displayName(service: { nameEs?: string; nameEn?: string }): string {
    const lang = (this.i18n.getCurrentLang() || this.i18n.getFallbackLang() || 'es').toLowerCase();
    if (lang.startsWith('en') && service.nameEn) {
      return service.nameEn;
    }
    return service.nameEs || '';
  }

  categoryLabel(category?: string): string {
    if (!category) {
      return '';
    }
    const key = `services.categories.${category}`;
    const value = this.i18n.instant(key);
    return value === key ? category : value;
  }

  startCreate(): void {
    this.editingId = null;
    this.submitted = false;
    this.form.reset({ nameEs: '', nameEn: '', descriptionEs: '', descriptionEn: '', durationMin: 30, price: 0, category: 'CONSULTATION', active: true });
    this.open = true;
  }

  startEdit(service: any): void {
    this.editingId = service.id;
    this.submitted = false;
    this.form.reset({
      nameEs: service.nameEs || '',
      nameEn: service.nameEn || '',
      descriptionEs: service.descriptionEs || '',
      descriptionEn: service.descriptionEn || '',
      durationMin: service.durationMin ?? 30,
      price: service.price ?? 0,
      category: service.category || 'CONSULTATION',
      active: service.active !== false
    });
    this.open = true;
  }

  showError(name: 'nameEs' | 'durationMin' | 'price'): boolean {
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
      ? this.api.put(`/services/${this.editingId}`, body)
      : this.api.post('/services', body);
    request.subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.ngOnInit(); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
