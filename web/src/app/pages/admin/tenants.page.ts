import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { StatusBadgePipe } from '../../shared/ui/status-badge.pipe';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, TranslatePipe, StatusBadgePipe],
  template: `
    <div class="flex items-center justify-between">
      <h1 class="font-display text-2xl font-semibold">{{ 'nav.tenants' | translate }}</h1>
      <button class="btn-primary" (click)="open=true">{{ 'admin.createTenant' | translate }}</button>
    </div>
    <div class="card mt-6 overflow-x-auto p-0">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
          <tr>
            <th class="px-4 py-3">{{ 'settings.name' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.slug' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.plan' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.status' | translate }}</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody>
          @for (t of tenants(); track t.id) {
            <tr class="border-t border-slate-100 dark:border-white/5">
              <td class="px-4 py-3 font-medium">{{ t.name }}</td>
              <td class="px-4 py-3">{{ t.slug }}</td>
              <td class="px-4 py-3">
                <select class="input py-1 text-xs" [ngModel]="t.plan?.code" (ngModelChange)="changePlan(t, $event)">
                  <option value="BASIC">BASIC</option>
                  <option value="PROFESSIONAL">PROFESSIONAL</option>
                  <option value="PREMIUM">PREMIUM</option>
                </select>
              </td>
              <td class="px-4 py-3"><span [class]="t.status | statusBadge">{{ t.status }}</span></td>
              <td class="px-4 py-3 text-right">
                @if (t.status !== 'ACTIVE') {
                  <button class="btn-secondary text-xs" (click)="status(t.id, 'ACTIVE')">{{ 'admin.activate' | translate }}</button>
                }
                @if (t.status === 'ACTIVE') {
                  <button class="btn-secondary text-xs" (click)="status(t.id, 'SUSPENDED')">{{ 'admin.suspend' | translate }}</button>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg">{{ 'admin.createTenant' | translate }}</h2>
          <input class="input" formControlName="name" [placeholder]="'settings.name' | translate" />
          <input class="input" formControlName="slug" [placeholder]="'admin.slug' | translate" />
          <input class="input" formControlName="commercialName" [placeholder]="'settings.commercial' | translate" />
          <input class="input" formControlName="adminEmail" placeholder="admin@clinic.com" />
          <input class="input" formControlName="planCode" placeholder="PROFESSIONAL" />
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }
  `
})
export class AdminTenantsPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  tenants = signal<any[]>([]);
  open = false;
  form = this.fb.group({
    name: ['', Validators.required],
    slug: ['', Validators.required],
    commercialName: [''],
    adminEmail: [''],
    planCode: ['BASIC']
  });

  ngOnInit() { this.load(); }

  load() { this.api.get<any[]>('/admin/tenants').subscribe(t => this.tenants.set(t)); }

  status(id: number, status: string) {
    this.api.post(`/admin/tenants/${id}/status`, { status }).subscribe(() => this.load());
  }

  changePlan(tenant: any, planCode: string) {
    this.api.put(`/admin/tenants/${tenant.id}`, { planCode }).subscribe({
      next: () => this.load(),
      error: () => this.toast.show('common.error', true)
    });
  }

  save() {
    this.api.post('/admin/tenants', this.form.value).subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.load(); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
