import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div class="flex items-center justify-between">
      <h1 class="font-display text-2xl font-semibold">{{ 'team.title' | translate }}</h1>
      @if (auth.hasPermission('STAFF_MANAGE')) {
        <div class="flex gap-2">
          <button class="btn-secondary" (click)="openStaff=true">{{ 'team.staff' | translate }}</button>
          <button class="btn-primary" (click)="open=true">{{ 'team.new' | translate }}</button>
        </div>
      }
    </div>
    <div class="mt-6 grid gap-4 sm:grid-cols-2">
      @for (v of rows(); track v.id) {
        <div class="card">
          <p class="font-semibold">{{ v.fullName }}</p>
          <p class="text-sm text-slate-500">{{ v.specialty }} · {{ v.email }}</p>
        </div>
      }
    </div>
    @if (staff().length) {
      <h2 class="mt-8 font-display text-lg font-semibold">{{ 'team.staff' | translate }}</h2>
      <div class="mt-3 grid gap-4 sm:grid-cols-2">
        @for (e of staff(); track e.id) {
          <div class="card">
            <p class="font-semibold">{{ e.fullName }}</p>
            <p class="text-sm text-slate-500">{{ e.position }} · {{ e.email }}</p>
          </div>
        }
      </div>
    }
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <input class="input" formControlName="firstName" [placeholder]="'owners.firstName' | translate" />
          <input class="input" formControlName="lastName" [placeholder]="'owners.lastName' | translate" />
          <input class="input" formControlName="email" placeholder="email" />
          <input class="input" formControlName="specialty" [placeholder]="'team.specialty' | translate" />
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }
    @if (openStaff) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="openStaff=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="staffForm" (ngSubmit)="saveStaff()">
          <h2 class="font-display text-lg">{{ 'team.staff' | translate }}</h2>
          <input class="input" formControlName="firstName" [placeholder]="'owners.firstName' | translate" />
          <input class="input" formControlName="lastName" [placeholder]="'owners.lastName' | translate" />
          <input class="input" formControlName="email" [placeholder]="'owners.email' | translate" />
          <select class="input" formControlName="role">
            <option value="RECEPTIONIST">RECEPTIONIST</option>
            <option value="TENANT_ADMIN">TENANT_ADMIN</option>
          </select>
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="openStaff=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }
  `
})
export class TeamPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  auth = inject(AuthService);
  rows = signal<any[]>([]);
  staff = signal<any[]>([]);
  open = false;
  openStaff = false;
  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    specialty: ['']
  });
  staffForm = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    role: ['RECEPTIONIST']
  });
  ngOnInit() {
    this.api.get<any[]>('/veterinarians').subscribe(r => this.rows.set(r));
    if (this.auth.hasPermission('STAFF_MANAGE')) {
      this.api.get<any[]>('/employees').subscribe(r => this.staff.set(r || []));
    }
  }
  save() {
    this.api.post('/veterinarians', this.form.value).subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.ngOnInit(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }
  saveStaff() {
    this.api.post('/employees', this.staffForm.value).subscribe({
      next: () => { this.toast.show('common.saved'); this.openStaff = false; this.ngOnInit(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }
}
