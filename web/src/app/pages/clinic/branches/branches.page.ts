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
      <h1 class="font-display text-2xl font-semibold">{{ 'branches.title' | translate }}</h1>
      @if (auth.hasPermission('BRANCH_MANAGE')) {
        <button class="btn-primary" (click)="open=true">{{ 'branches.new' | translate }}</button>
      }
    </div>
    <div class="mt-6 space-y-3">
      @for (b of rows(); track b.id) {
        <div class="card">
          <p class="font-semibold">{{ b.name }}</p>
          <p class="text-sm text-slate-500">{{ b.address }} · {{ b.city }}</p>
        </div>
      }
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <input class="input" formControlName="name" />
          <input class="input" formControlName="address" />
          <input class="input" formControlName="city" />
          <input class="input" formControlName="phone" />
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
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
  form = this.fb.group({ name: ['', Validators.required], address: [''], city: [''], phone: [''], country: ['ES'] });
  ngOnInit() { this.api.get<any[]>('/branches').subscribe(r => this.rows.set(r)); }
  save() {
    this.api.post('/branches', this.form.value).subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.ngOnInit(); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
