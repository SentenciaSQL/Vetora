import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { Appointment, PageResponse, Pet } from '../../../core/models';
import { StatusBadgePipe } from '../../../shared/ui/status-badge.pipe';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, StatusBadgePipe, EmptyStateComponent],
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'calendar.title' | translate }}</h1>
        <p class="mt-1 text-sm text-slate-500">{{ 'calendar.subtitle' | translate }}</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <button class="btn-secondary" (click)="shift(-1)">‹</button>
        <button class="btn-secondary" (click)="goToday()">{{ 'common.today' | translate }}</button>
        <button class="btn-secondary" (click)="shift(1)">›</button>
        <button class="btn-secondary" [class.bg-brand-50]="view()==='day'" (click)="view.set('day')">{{ 'common.day' | translate }}</button>
        <button class="btn-secondary" [class.bg-brand-50]="view()==='week'" (click)="view.set('week')">{{ 'common.week' | translate }}</button>
        <button class="btn-secondary" [class.bg-brand-50]="view()==='month'" (click)="view.set('month')">{{ 'common.month' | translate }}</button>
        @if (auth.isStaff()) {
          <button class="btn-primary" (click)="open=true">{{ 'calendar.new' | translate }}</button>
        }
      </div>
    </div>
    <p class="mt-3 text-sm font-medium text-slate-500">{{ rangeLabel() }}</p>
    @if (view() !== 'month') {
      <div class="card mt-4 overflow-x-auto p-0">
        <div class="grid min-w-[720px]" [style.gridTemplateColumns]="'4.5rem repeat(' + dayColumns().length + ', minmax(0,1fr))'">
          <div class="border-b border-slate-100 p-2 text-xs text-slate-400 dark:border-white/5"></div>
          @for (day of dayColumns(); track day.toISOString()) {
            <div class="border-b border-l border-slate-100 p-2 text-center text-xs font-semibold dark:border-white/5">
              {{ day | date:'EEE d' }}
            </div>
          }
          @for (hour of hours; track hour) {
            <div class="border-t border-slate-100 px-2 py-3 text-xs text-slate-400 dark:border-white/5">{{ hour }}:00</div>
            @for (day of dayColumns(); track day) {
              <div class="relative min-h-14 border-l border-t border-slate-100 dark:border-white/5">
                @for (a of slotsAt(day, hour); track a.id) {
                  <button type="button" class="absolute inset-x-1 top-1 rounded-lg bg-brand-600 px-2 py-1 text-left text-[11px] text-white"
                          (click)="selected.set(a)">
                    {{ a.petName }}
                  </button>
                }
              </div>
            }
          }
        </div>
      </div>
    }
    <div class="mt-4 space-y-2">
      @if (visible().length === 0) { <empty-state [title]="'calendar.empty' | translate" /> }
      @for (a of visible(); track a.id) {
        <div class="card flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="font-semibold">{{ a.petName }} · {{ a.ownerName }}</p>
            <p class="text-sm text-slate-500">{{ a.startAt | date:'short' }} · {{ a.serviceName }} · {{ a.veterinarianName }}</p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <span [class]="a.status | statusBadge">{{ a.status }}</span>
            @if (auth.isStaff() && (a.status === 'PENDING' || a.status === 'REQUESTED')) {
              <button class="btn-secondary text-xs" (click)="status(a.id,'CONFIRMED')">{{ 'calendar.confirm' | translate }}</button>
            }
            @if (auth.isStaff() && a.status === 'CONFIRMED') {
              <button class="btn-secondary text-xs" (click)="status(a.id,'ARRIVED')">{{ 'calendar.arrived' | translate }}</button>
              <button class="btn-secondary text-xs" (click)="status(a.id,'WAITING')">{{ 'calendar.waiting' | translate }}</button>
            }
            @if (auth.isStaff() && (a.status === 'ARRIVED' || a.status === 'WAITING')) {
              <button class="btn-primary text-xs" (click)="status(a.id,'IN_PROGRESS')">{{ 'calendar.start' | translate }}</button>
              <a class="btn-secondary text-xs" [routerLink]="['/consultations/new']" [queryParams]="{ petId: a.petId, appointmentId: a.id }">{{ 'consultations.new' | translate }}</a>
            }
            @if (auth.isStaff() && a.status === 'IN_PROGRESS') {
              <button class="btn-primary text-xs" (click)="status(a.id,'COMPLETED')">{{ 'calendar.complete' | translate }}</button>
            }
            @if (auth.isStaff() && a.status !== 'COMPLETED' && a.status !== 'CANCELLED' && a.status !== 'NO_SHOW') {
              <button class="btn-secondary text-xs" (click)="status(a.id,'CANCELLED')">{{ 'common.cancel' | translate }}</button>
              <button class="btn-secondary text-xs" (click)="status(a.id,'NO_SHOW')">{{ 'calendar.noShow' | translate }}</button>
            }
            @if (!auth.isStaff() && (a.status === 'REQUESTED' || a.status === 'PENDING' || a.status === 'CONFIRMED')) {
              <button class="btn-secondary text-xs" (click)="status(a.id,'CANCELLED')">{{ 'common.cancel' | translate }}</button>
            }
          </div>
        </div>
      }
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg">{{ 'calendar.new' | translate }}</h2>
          <select class="input" formControlName="petId">
            @for (p of pets(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
          </select>
          <select class="input" formControlName="veterinarianId">
            @for (v of vets(); track v.id) { <option [value]="v.id">{{ v.fullName }}</option> }
          </select>
          <select class="input" formControlName="serviceId">
            @for (s of services(); track s.id) { <option [value]="s.id">{{ s.nameEs }}</option> }
          </select>
          <input class="input" type="datetime-local" formControlName="startAt" />
          <input class="input" formControlName="reason" [placeholder]="'calendar.reason' | translate" />
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }
  `
})
export class CalendarPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  private fb = inject(FormBuilder);
  items = signal<Appointment[]>([]);
  pets = signal<Pet[]>([]);
  vets = signal<any[]>([]);
  services = signal<any[]>([]);
  open = false;
  view = signal<'day' | 'week' | 'month'>('week');
  anchor = signal(new Date());
  selected = signal<Appointment | null>(null);
  hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19];
  form = this.fb.group({
    petId: ['', Validators.required],
    veterinarianId: ['', Validators.required],
    serviceId: ['', Validators.required],
    startAt: ['', Validators.required],
    reason: ['']
  });

  visible = computed(() => {
    const from = this.rangeStart().getTime();
    const to = this.rangeEnd().getTime();
    return this.items().filter(a => {
      const t = new Date(a.startAt).getTime();
      return t >= from && t < to;
    });
  });

  rangeLabel = computed(() => {
    const from = this.rangeStart();
    const to = new Date(this.rangeEnd().getTime() - 1);
    return `${from.toLocaleDateString()} – ${to.toLocaleDateString()}`;
  });

  dayColumns = computed(() => {
    const start = this.rangeStart();
    const days = this.view() === 'day' ? 1 : 7;
    return Array.from({ length: days }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      return d;
    });
  });

  slotsAt(day: Date, hour: number) {
    return this.visible().filter(a => {
      const start = new Date(a.startAt);
      return start.getDate() === day.getDate() && start.getMonth() === day.getMonth() && start.getHours() === hour;
    });
  }

  ngOnInit() {
    this.reload();
    if (this.auth.isStaff()) {
      this.api.get<PageResponse<Pet>>('/pets', { size: 100 }).subscribe(r => this.pets.set(r.content || []));
      this.api.get<any[]>('/veterinarians').subscribe(r => this.vets.set(r));
      this.api.get<any[]>('/services').subscribe(r => this.services.set(r));
    }
  }

  rangeStart() {
    const d = new Date(this.anchor());
    d.setHours(0, 0, 0, 0);
    if (this.view() === 'week') {
      const day = d.getDay() || 7;
      d.setDate(d.getDate() - day + 1);
    }
    if (this.view() === 'month') {
      d.setDate(1);
    }
    return d;
  }

  rangeEnd() {
    const d = this.rangeStart();
    if (this.view() === 'month') {
      d.setMonth(d.getMonth() + 1);
    } else {
      d.setDate(d.getDate() + (this.view() === 'week' ? 7 : 1));
    }
    return d;
  }

  shift(delta: number) {
    const d = new Date(this.anchor());
    d.setDate(d.getDate() + delta * (this.view() === 'month' ? 30 : this.view() === 'week' ? 7 : 1));
    this.anchor.set(d);
    this.reload();
  }

  goToday() {
    this.anchor.set(new Date());
    this.reload();
  }

  reload() {
    if (!this.auth.isStaff()) {
      this.api.get<Appointment[]>('/appointments/mine').subscribe(r => this.items.set(r));
      return;
    }
    const from = new Date(this.rangeStart());
    from.setDate(from.getDate() - 1);
    const to = new Date(this.rangeEnd());
    to.setDate(to.getDate() + 1);
    this.api.get<Appointment[]>('/appointments', { from: from.toISOString(), to: to.toISOString() }).subscribe(r => this.items.set(r));
  }

  status(id: number, status: string) {
    this.api.post(`/appointments/${id}/status`, { status }).subscribe({
      next: () => this.reload(),
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  save() {
    const v = this.form.getRawValue();
    this.api.post('/appointments', {
      petId: Number(v.petId),
      veterinarianId: Number(v.veterinarianId),
      serviceId: Number(v.serviceId),
      startAt: new Date(v.startAt!).toISOString(),
      reason: v.reason
    }).subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.reload(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }
}
