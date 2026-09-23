import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { StatusLabelPipe } from '../../../shared/ui/status-label.pipe';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, StatusLabelPipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'reports.title' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ (auth.isSuperAdmin() ? 'reports.platformSubtitle' : 'reports.subtitle') | translate }}</p>
    <div class="card mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <label class="text-sm">{{ 'reports.from' | translate }}
        <input class="input mt-1" type="date" [(ngModel)]="from" />
      </label>
      <label class="text-sm">{{ 'reports.to' | translate }}
        <input class="input mt-1" type="date" [(ngModel)]="to" />
      </label>
      @if (auth.isSuperAdmin()) {
        <label class="text-sm">{{ 'nav.tenants' | translate }}
          <select class="input mt-1" [(ngModel)]="tenantId">
            <option value="">{{ 'common.all' | translate }}</option>
            @for (t of tenants(); track t.id) { <option [value]="t.id">{{ t.name }}</option> }
          </select>
        </label>
        <label class="text-sm">{{ 'admin.plan' | translate }}
          <select class="input mt-1" [(ngModel)]="planCode">
            <option value="">{{ 'admin.filters.allPlans' | translate }}</option>
            @for (p of plans(); track p.code) { <option [value]="p.code">{{ p.nameEs || p.name || p.code }}</option> }
          </select>
        </label>
        <label class="text-sm">{{ 'admin.cycle' | translate }}
          <select class="input mt-1" [(ngModel)]="billingCycle">
            <option value="">{{ 'common.all' | translate }}</option>
            <option value="MONTHLY">{{ 'billing.monthly' | translate }}</option>
            <option value="ANNUAL">{{ 'billing.annual' | translate }}</option>
          </select>
        </label>
        <label class="text-sm">{{ 'common.status' | translate }}
          <select class="input mt-1" [(ngModel)]="status">
            <option value="">{{ 'common.all' | translate }}</option>
            @for (s of platformStatuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
          </select>
        </label>
        <label class="text-sm">{{ 'admin.filters.country' | translate }}
          <input class="input mt-1" [(ngModel)]="country" [placeholder]="'admin.filters.allCountries' | translate" />
        </label>
        <label class="text-sm">{{ 'admin.eventType' | translate }}
          <input class="input mt-1" [(ngModel)]="eventType" />
        </label>
      } @else {
        <label class="text-sm">{{ 'reports.veterinarian' | translate }}
          <select class="input mt-1" [(ngModel)]="veterinarianId">
            <option value="">{{ 'common.all' | translate }}</option>
            @for (v of vets(); track v.id) { <option [value]="v.id">{{ v.fullName }}</option> }
          </select>
        </label>
        <label class="text-sm">{{ 'common.status' | translate }}
          <select class="input mt-1" [(ngModel)]="status">
            <option value="">{{ 'common.all' | translate }}</option>
            @for (s of statuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
          </select>
        </label>
      }
    </div>
    @if (auth.isSuperAdmin()) {
      <div class="mt-6 grid gap-4 sm:grid-cols-2">
        @for (report of platformReports; track report.type) {
          <div class="card flex items-center justify-between gap-3 hover:border-brand-200">
            <div>
              <p class="font-medium">{{ report.label | translate }}</p>
              <p class="text-xs text-slate-400">{{ 'reports.detailHint' | translate }}</p>
            </div>
            <div class="flex gap-2">
              <button type="button" class="btn-secondary text-xs" (click)="downloadPlatform(report.type, report.file + '.csv')">CSV</button>
              <button type="button" class="btn-secondary text-xs" (click)="downloadPlatform(report.type, report.file + '.xlsx', 'xlsx')">Excel</button>
            </div>
          </div>
        }
      </div>
    } @else {
      <div class="mt-6 grid gap-4 sm:grid-cols-2">
        <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/appointments.xlsx', 'citas.xlsx', true)">{{ 'reports.appointments' | translate }}</button>
        <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/consultations.csv', 'consultas.csv', true)">{{ 'reports.consultations' | translate }}</button>
        <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/vaccinations.csv', 'vacunas.csv')">{{ 'reports.vaccines' | translate }}</button>
        <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/owners.csv', 'propietarios.csv')">{{ 'reports.owners' | translate }}</button>
        <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/pets.csv', 'mascotas.csv')">{{ 'reports.pets' | translate }}</button>
      </div>
    }
  `
})
export class ReportsPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  vets = signal<any[]>([]);
  tenants = signal<any[]>([]);
  plans = signal<any[]>([]);
  from = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
  to = new Date().toISOString().slice(0, 10);
  veterinarianId = '';
  tenantId = '';
  planCode = '';
  billingCycle = '';
  country = '';
  eventType = '';
  status = '';
  statuses = ['REQUESTED', 'PENDING', 'CONFIRMED', 'ARRIVED', 'WAITING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];
  platformStatuses = ['ACTIVE', 'TRIAL', 'PAST_DUE', 'GRACE_PERIOD', 'SUSPENDED', 'CANCELED', 'PENDING_PAYMENT'];
  platformReports = [
    { type: 'tenants', file: 'veterinarias', label: 'reports.platform.tenants' },
    { type: 'subscriptions', file: 'suscripciones', label: 'reports.platform.subscriptions' },
    { type: 'revenue', file: 'ingresos', label: 'reports.platform.revenue' },
    { type: 'failed-payments', file: 'pagos-fallidos', label: 'reports.platform.failed' },
    { type: 'churn', file: 'altas-cancelaciones', label: 'reports.platform.churn' },
    { type: 'users', file: 'usuarios', label: 'reports.platform.users' },
    { type: 'pets', file: 'mascotas', label: 'reports.platform.pets' },
    { type: 'appointments', file: 'citas', label: 'reports.platform.appointments' },
    { type: 'activity', file: 'actividad', label: 'reports.platform.activity' },
    { type: 'features', file: 'uso', label: 'reports.platform.features' },
    { type: 'audit', file: 'auditoria', label: 'reports.platform.audit' }
  ];

  ngOnInit() {
    if (this.auth.isSuperAdmin()) {
      this.api.get<any[]>('/admin/tenants').subscribe(r => this.tenants.set(r || []));
      this.api.get<any[]>('/admin/plans').subscribe(r => this.plans.set(r || []));
    } else {
      this.api.get<any[]>('/veterinarians').subscribe(r => this.vets.set(r));
    }
  }

  download(path: string, filename: string, range = false) {
    const params: Record<string, string> = {};
    if (range) {
      params['from'] = new Date(this.from + 'T00:00:00').toISOString();
      params['to'] = new Date(this.to + 'T23:59:59').toISOString();
    }
    if (this.veterinarianId && path.includes('appointments')) params['veterinarianId'] = this.veterinarianId;
    if (this.status && path.includes('appointments')) params['status'] = this.status;
    this.saveBlob(path, filename, params);
  }

  downloadPlatform(type: string, filename: string, format?: string) {
    const params: Record<string, string> = {
      from: new Date(this.from + 'T00:00:00Z').toISOString(),
      to: new Date(this.to + 'T23:59:59Z').toISOString()
    };
    if (this.tenantId) params['tenantId'] = this.tenantId;
    if (this.planCode) params['planCode'] = this.planCode;
    if (this.billingCycle) params['billingCycle'] = this.billingCycle;
    if (this.status) params['status'] = this.status;
    if (this.country) params['country'] = this.country;
    if (this.eventType) params['eventType'] = this.eventType;
    if (format) params['format'] = format;
    this.saveBlob(`/admin/reports/${type}`, filename, params);
  }

  private saveBlob(path: string, filename: string, params: Record<string, string>) {
    this.api.blob(path, params).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }
}
