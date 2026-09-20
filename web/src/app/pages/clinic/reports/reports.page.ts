import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'reports.title' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ 'reports.subtitle' | translate }}</p>
    <div class="card mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <label class="text-sm">{{ 'reports.from' | translate }}
        <input class="input mt-1" type="date" [(ngModel)]="from" />
      </label>
      <label class="text-sm">{{ 'reports.to' | translate }}
        <input class="input mt-1" type="date" [(ngModel)]="to" />
      </label>
      <label class="text-sm">{{ 'reports.veterinarian' | translate }}
        <select class="input mt-1" [(ngModel)]="veterinarianId">
          <option value="">{{ 'common.all' | translate }}</option>
          @for (v of vets(); track v.id) { <option [value]="v.id">{{ v.fullName }}</option> }
        </select>
      </label>
      <label class="text-sm">{{ 'common.status' | translate }}
        <select class="input mt-1" [(ngModel)]="status">
          <option value="">{{ 'common.all' | translate }}</option>
          @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
        </select>
      </label>
    </div>
    <div class="mt-6 grid gap-4 sm:grid-cols-2">
      <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/appointments.xlsx', 'citas.xlsx', true)">{{ 'reports.appointments' | translate }}</button>
      <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/consultations.csv', 'consultas.csv', true)">{{ 'reports.consultations' | translate }}</button>
      <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/vaccinations.csv', 'vacunas.csv')">{{ 'reports.vaccines' | translate }}</button>
      <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/owners.csv', 'propietarios.csv')">{{ 'reports.owners' | translate }}</button>
      <button type="button" class="card text-left hover:border-brand-200" (click)="download('/reports/pets.csv', 'mascotas.csv')">{{ 'reports.pets' | translate }}</button>
    </div>
  `
})
export class ReportsPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  vets = signal<any[]>([]);
  from = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
  to = new Date().toISOString().slice(0, 10);
  veterinarianId = '';
  status = '';
  statuses = ['REQUESTED', 'PENDING', 'CONFIRMED', 'ARRIVED', 'WAITING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];

  ngOnInit() {
    this.api.get<any[]>('/veterinarians').subscribe(r => this.vets.set(r));
  }

  download(path: string, filename: string, range = false) {
    const params: Record<string, string> = {};
    if (range) {
      params['from'] = new Date(this.from + 'T00:00:00').toISOString();
      params['to'] = new Date(this.to + 'T23:59:59').toISOString();
    }
    if (this.veterinarianId && path.includes('appointments')) params['veterinarianId'] = this.veterinarianId;
    if (this.status && path.includes('appointments')) params['status'] = this.status;
    this.api.blob(path, params).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.toast.show('common.error', true)
    });
  }
}
