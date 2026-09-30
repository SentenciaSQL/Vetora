import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { Appointment, PageResponse, Pet } from '../../../core/models';
import { specialtyLabel } from '../../../core/team-labels';
import { StatusBadgePipe } from '../../../shared/ui/status-badge.pipe';
import { StatusLabelPipe } from '../../../shared/ui/status-label.pipe';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, StatusBadgePipe, StatusLabelPipe, EmptyStateComponent],
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
        @if (canBook()) {
          <button class="btn-primary" (click)="openForm()">{{ bookLabel() | translate }}</button>
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
      @if (visible().length === 0) {
        <div class="card">
          <empty-state [title]="'calendar.empty' | translate" [subtitle]="canBook() ? ('calendar.emptyHint' | translate) : ''" />
          @if (canBook()) {
            <div class="pb-6 text-center">
              <button class="btn-primary" (click)="openForm()">{{ bookLabel() | translate }}</button>
            </div>
          }
        </div>
      }
      @for (a of visible(); track a.id) {
        <div class="card flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="font-semibold">{{ a.petName }} · {{ a.ownerName }}</p>
            <p class="text-sm text-slate-500">{{ a.startAt | date:'short' }} · {{ a.serviceName }} · {{ a.veterinarianName }}@if (appointmentSpecialty(a)) { · {{ appointmentSpecialty(a) }} }</p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <span [class]="a.status | statusBadge">{{ a.status | statusLabel }}</span>
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
          <h2 class="font-display text-lg">{{ bookLabel() | translate }}</h2>
          @if (!auth.isStaff() && pets().length === 0) {
            <p class="text-sm text-slate-500">{{ 'calendar.needPet' | translate }}</p>
            <a routerLink="/pets" [queryParams]="{ register: 1 }" class="btn-primary inline-flex">{{ 'pets.register' | translate }}</a>
          } @else {
            <label class="block text-sm font-medium">{{ 'nav.pets' | translate }}</label>
            <select class="input" formControlName="petId" (change)="onPetChange()">
              <option value="">{{ 'calendar.choosePet' | translate }}</option>
              @for (p of pets(); track p.id) {
                <option [value]="p.id">{{ p.name }}{{ p.tenantName ? ' · ' + p.tenantName : '' }}</option>
              }
            </select>
            @if (branches().length) {
              <label class="block text-sm font-medium">{{ 'nav.branches' | translate }}</label>
              <select class="input" formControlName="branchId" (change)="onBranchChange()">
                <option value="">{{ 'calendar.chooseBranch' | translate }}</option>
                @for (b of branches(); track b.id) { <option [value]="b.id">{{ b.name }}</option> }
              </select>
            }
            <label class="block text-sm font-medium">{{ 'nav.team' | translate }}</label>
            @if (loadingVeterinarians()) {
              <p class="text-sm text-slate-500">{{ 'calendar.loadingVets' | translate }}</p>
            }
            @if (!loadingVeterinarians() && selectedBranchId() && visibleVets().length === 0) {
              <p class="text-sm text-slate-500">{{ 'calendar.noVets' | translate }}</p>
            }
            <select class="input" formControlName="veterinarianId" [attr.disabled]="loadingVeterinarians() || !selectedBranchId() ? true : null">
              <option value="">{{ (auth.isStaff() ? 'calendar.chooseVet' : 'calendar.chooseVetRequired') | translate }}</option>
              @for (v of visibleVets(); track v.id) { <option [value]="v.id">{{ v.fullName }}@if (vetSpecialty(v)) { · {{ vetSpecialty(v) }} }</option> }
            </select>
            <label class="block text-sm font-medium">{{ 'nav.services' | translate }}</label>
            <select class="input" formControlName="serviceId">
              <option value="">{{ 'calendar.chooseService' | translate }}</option>
              @for (s of visibleServices(); track s.id) { <option [value]="s.id">{{ s.nameEs || s.name }}</option> }
            </select>
            <label class="block text-sm font-medium">{{ 'calendar.when' | translate }}</label>
            <input class="input" type="datetime-local" formControlName="startAt" />
            <input class="input" formControlName="reason" [placeholder]="'calendar.reason' | translate" />
            @if (formError()) {
              <p class="text-sm text-rose-600">{{ formError() }}</p>
            }
            <div class="flex justify-end gap-2">
              <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
              <button class="btn-primary" [disabled]="form.invalid || saving() || loadingVeterinarians() || vetRequiredMissing()">{{ auth.isStaff() ? ('common.save' | translate) : ('calendar.request' | translate) }}</button>
            </div>
          }
        </form>
      </div>
    }
  `
})
export class CalendarPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private i18n = inject(TranslateService);
  items = signal<Appointment[]>([]);
  pets = signal<Pet[]>([]);
  branches = signal<{ id: number; name: string }[]>([]);
  vets = signal<any[]>([]);
  services = signal<any[]>([]);
  open = false;
  saving = signal(false);
  loadingVeterinarians = signal(false);
  formError = signal('');
  selectedTenantId = signal<number | null>(null);
  selectedBranchId = signal<number | null>(null);
  view = signal<'day' | 'week' | 'month'>('week');
  anchor = signal(new Date());
  selected = signal<Appointment | null>(null);
  hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19];
  private vetRequest = 0;
  private branchRequest = 0;
  form = this.fb.group({
    petId: ['', Validators.required],
    branchId: [''],
    veterinarianId: [''],
    serviceId: [''],
    startAt: ['', Validators.required],
    reason: ['']
  });

  visibleVets = computed(() => {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return [];
    }
    return this.vets().filter((vet: any) => !vet.branchId || Number(vet.branchId) === branchId);
  });

  visibleServices = computed(() => {
    const tenantId = this.selectedTenantId();
    if (!tenantId) {
      return this.services();
    }
    return this.services().filter((service: any) => !service.tenantId || service.tenantId === tenantId);
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

  canBook(): boolean {
    return this.auth.isStaff() || this.auth.hasRole('PET_OWNER');
  }

  bookLabel(): string {
    return this.auth.isStaff() ? 'calendar.new' : 'calendar.request';
  }

  selectedPet(): Pet | undefined {
    const id = Number(this.form.controls.petId.value);
    return this.pets().find(pet => pet.id === id);
  }

  onPetChange(): void {
    const pet = this.selectedPet();
    const nextTenant = pet?.tenantId || null;
    const tenantChanged = nextTenant !== this.selectedTenantId();
    this.selectedTenantId.set(nextTenant);
    this.form.patchValue({ veterinarianId: '', serviceId: '' }, { emitEvent: false });
    if (this.auth.isStaff() || !tenantChanged) {
      if (!this.auth.isStaff() && this.selectedBranchId()) {
        this.vets.set([]);
        this.loadVeterinarians(this.selectedBranchId()!);
      }
      return;
    }
    this.clearVeterinarians();
    this.loadOwnerBranches();
  }

  onBranchChange(): void {
    const raw = this.form.controls.branchId.value;
    const branchId = raw ? Number(raw) : null;
    this.selectedBranchId.set(branchId && !Number.isNaN(branchId) ? branchId : null);
    this.form.patchValue({ veterinarianId: '' }, { emitEvent: false });
    this.vets.set([]);
    if (!this.selectedBranchId()) {
      this.vetRequest++;
      this.loadingVeterinarians.set(false);
      return;
    }
    this.loadVeterinarians(this.selectedBranchId()!);
  }

  vetSpecialty(vet: { specialty?: string; specialtyOther?: string }): string {
    return specialtyLabel(this.i18n, vet.specialty, vet.specialtyOther, vet.specialty);
  }

  appointmentSpecialty(appointment: Appointment): string {
    return specialtyLabel(this.i18n, appointment.veterinarianSpecialty, appointment.veterinarianSpecialtyOther, appointment.veterinarianSpecialty);
  }

  vetRequiredMissing(): boolean {
    return !this.auth.isStaff() && !!this.selectedBranchId() && !this.loadingVeterinarians() && this.visibleVets().length === 0;
  }

  ngOnInit() {
    if (!this.auth.isStaff()) {
      this.form.controls.veterinarianId.addValidators(Validators.required);
      this.form.controls.veterinarianId.updateValueAndValidity({ emitEvent: false });
    }
    this.reload();
    this.loadBookingCatalog();
    this.route.queryParamMap.subscribe(params => {
      if (params.get('book') === '1' && this.canBook()) {
        this.openForm();
      }
    });
  }

  openForm(): void {
    this.formError.set('');
    this.clearVeterinarians();
    this.open = true;
    this.loadBookingCatalog();
    if (!this.auth.isStaff() && this.selectedTenantId()) {
      this.loadOwnerBranches();
    }
  }

  loadBookingCatalog(): void {
    if (this.auth.isStaff()) {
      this.api.get<PageResponse<Pet>>('/pets', { size: 100 }).subscribe(r => this.pets.set(r.content || []));
      this.loadStaffBranches();
    } else {
      this.api.get<Pet[]>('/pets/mine').subscribe(r => this.pets.set(r || []));
    }
    this.api.get<any[]>('/services').subscribe({
      next: r => this.services.set(r || []),
      error: () => this.services.set([])
    });
  }

  private clearVeterinarians(): void {
    this.vetRequest++;
    this.selectedBranchId.set(null);
    this.vets.set([]);
    this.loadingVeterinarians.set(false);
    this.form.patchValue({ branchId: '', veterinarianId: '' }, { emitEvent: false });
  }

  private loadStaffBranches(): void {
    const request = ++this.branchRequest;
    this.api.get<{ id: number; name: string }[]>('/branches').subscribe({
      next: rows => this.applyBranches(request, rows || []),
      error: () => this.applyBranches(request, [])
    });
  }

  private loadOwnerBranches(): void {
    const tenantId = this.selectedTenantId();
    const request = ++this.branchRequest;
    this.branches.set([]);
    if (!tenantId) {
      return;
    }
    this.api.get<{ id: number; name: string }[]>(`/branches/tenant/${tenantId}`).subscribe({
      next: rows => this.applyBranches(request, rows || []),
      error: () => this.applyBranches(request, [])
    });
  }

  private applyBranches(request: number, rows: { id: number; name: string }[]): void {
    if (request !== this.branchRequest) {
      return;
    }
    this.branches.set(rows);
    const control = this.form.controls.branchId;
    control.setValidators(rows.length > 0 ? [Validators.required] : []);
    control.updateValueAndValidity({ emitEvent: false });
    if (rows.length === 1) {
      this.form.patchValue({ branchId: String(rows[0].id) });
      this.onBranchChange();
    }
  }

  private loadVeterinarians(branchId: number): void {
    const request = ++this.vetRequest;
    this.loadingVeterinarians.set(true);
    this.vets.set([]);
    const tenantId = this.selectedTenantId();
    const path = !this.auth.isStaff() && tenantId ? `/veterinarians/tenant/${tenantId}` : '/veterinarians';
    this.api.get<any[]>(path, { branchId }).subscribe({
      next: rows => {
        if (request !== this.vetRequest) {
          return;
        }
        this.vets.set(rows || []);
        this.loadingVeterinarians.set(false);
      },
      error: () => {
        if (request !== this.vetRequest) {
          return;
        }
        this.vets.set([]);
        this.loadingVeterinarians.set(false);
      }
    });
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
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set('');
    this.api.post('/appointments', {
      petId: Number(v.petId),
      branchId: v.branchId ? Number(v.branchId) : null,
      veterinarianId: v.veterinarianId ? Number(v.veterinarianId) : null,
      serviceId: v.serviceId ? Number(v.serviceId) : null,
      startAt: new Date(v.startAt!).toISOString(),
      reason: v.reason
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.show(this.auth.isStaff() ? 'common.saved' : 'calendar.requested');
        this.open = false;
        this.reload();
      },
      error: (e) => {
        this.saving.set(false);
        this.formError.set(e.error?.message || 'common.error');
        this.toast.show(e.error?.message || 'common.error', true);
      }
    });
  }
}
