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
      <h1 class="font-display text-2xl font-semibold">{{ 'services.title' | translate }}</h1>
      @if (auth.hasPermission('SERVICE_MANAGE')) {
        <button class="btn-primary" (click)="open=true">{{ 'services.new' | translate }}</button>
      }
    </div>
    <div class="card mt-6 overflow-x-auto p-0">
      <table class="min-w-full text-sm">
        <tbody>
          @for (s of rows(); track s.id) {
            <tr class="border-t border-slate-100 dark:border-white/5">
              <td class="px-4 py-3 font-medium">{{ s.nameEs }}</td>
              <td class="px-4 py-3 text-slate-500">{{ s.durationMin }} min</td>
              <td class="px-4 py-3">{{ s.price }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <input class="input" formControlName="nameEs" />
          <input class="input" type="number" formControlName="durationMin" />
          <input class="input" type="number" formControlName="price" />
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
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
  auth = inject(AuthService);
  rows = signal<any[]>([]);
  open = false;
  form = this.fb.group({
    nameEs: ['', Validators.required],
    durationMin: [30],
    price: [0],
    category: ['CONSULTATION']
  });
  ngOnInit() { this.api.get<any[]>('/services').subscribe(r => this.rows.set(r)); }
  save() {
    this.api.post('/services', this.form.value).subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.ngOnInit(); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
