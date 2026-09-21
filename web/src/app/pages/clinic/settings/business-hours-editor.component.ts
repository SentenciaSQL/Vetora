import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { SessionInactivityService } from '../../../core/services/session-inactivity.service';
import { apiErrorMessage } from '../../../core/http-error';

interface Interval {
  open: string;
  close: string;
}

interface DayHours {
  dayOfWeek: number;
  closed: boolean;
  intervals: Interval[];
}

interface HoursException {
  id: number;
  date: string;
  closed: boolean;
  intervals: Interval[];
  description?: string;
}

interface HoursResponse {
  clinicId: number;
  branchId: number;
  branchName: string;
  timezone: string;
  configured: boolean;
  days: DayHours[];
  exceptions: HoursException[];
}

interface BranchRef {
  id: number;
  name: string;
}

interface AffectedAppointment {
  id: number;
  startAt: string;
  petName?: string;
  status?: string;
}

const EMPTY_DAYS: DayHours[] = [1, 2, 3, 4, 5, 6, 7].map(dayOfWeek => ({
  dayOfWeek,
  closed: true,
  intervals: []
}));

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
  selector: 'app-business-hours-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  template: `
    <section class="card mt-6 max-w-3xl space-y-4">
      <div>
        <h2 class="font-medium">{{ 'hours.title' | translate }}</h2>
        <p class="text-sm text-slate-500">{{ 'hours.subtitle' | translate }}</p>
      </div>
      @if (loading()) {
        <p class="text-sm text-slate-500">{{ 'common.loading' | translate }}</p>
      } @else if (error()) {
        <p class="text-sm text-rose-600">{{ error() }}</p>
        <button type="button" class="btn-secondary" (click)="load()">{{ 'hours.retry' | translate }}</button>
      } @else if (!branches().length) {
        <p class="text-sm text-slate-500">{{ 'hours.noBranches' | translate }}</p>
      } @else {
        <label class="block text-sm">{{ 'hours.branch' | translate }}
          <select class="input mt-1" [ngModel]="branchId()" (ngModelChange)="onBranch($event)">
            @for (branch of branches(); track branch.id) {
              <option [ngValue]="branch.id">{{ branch.name }}</option>
            }
          </select>
        </label>
        <label class="block text-sm">{{ 'hours.timezone' | translate }}
          <select class="input mt-1" [ngModel]="timezone" (ngModelChange)="timezone = $event">
            @for (zone of zones; track zone) {
              <option [ngValue]="zone">{{ zone }}</option>
            }
          </select>
        </label>
        @if (!configured()) {
          <p class="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
            {{ 'hours.unavailable' | translate }}
          </p>
        }
        @for (day of days; track day.dayOfWeek) {
          <div class="rounded-xl border border-slate-200 p-3 dark:border-white/10">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <label class="flex items-center gap-2 text-sm font-medium">
                <input type="checkbox" [ngModel]="!day.closed" (ngModelChange)="toggleDay(day, $event)" />
                {{ ('hours.day' + day.dayOfWeek) | translate }}
              </label>
              <button type="button" class="text-xs text-brand-700 hover:underline" (click)="copyOpen = day.dayOfWeek">
                {{ 'hours.copy' | translate }}
              </button>
            </div>
            @if (!day.closed) {
              @for (interval of day.intervals; track $index) {
                <div class="mt-2 flex flex-wrap items-center gap-2">
                  <input class="input w-28" type="time" [(ngModel)]="interval.open" />
                  <span class="text-slate-400">–</span>
                  <input class="input w-28" type="time" [(ngModel)]="interval.close" />
                  <button type="button" class="text-sm text-rose-600" (click)="removeInterval(day, $index)">{{ 'common.delete' | translate }}</button>
                </div>
              }
              <button type="button" class="mt-2 text-sm text-brand-700 hover:underline" (click)="addInterval(day)">
                {{ 'hours.addInterval' | translate }}
              </button>
              @if (overlapWarning(day)) {
                <p class="mt-2 text-sm text-rose-600">{{ 'hours.overlap' | translate }}</p>
              }
            }
          </div>
        }
        @if (canEdit) {
          <button type="button" class="btn-primary" [disabled]="saving()" (click)="save(false)">
            {{ 'common.save' | translate }}
          </button>
        }
        @if (saveError()) {
          <p class="text-sm text-rose-600">{{ saveError() }}</p>
        }

        <h3 class="pt-2 font-medium">{{ 'hours.exceptions' | translate }}</h3>
        @if (!exceptions().length) {
          <p class="text-sm text-slate-500">{{ 'hours.noExceptions' | translate }}</p>
        }
        @for (item of exceptions(); track item.id) {
          <div class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 px-3 py-2 text-sm dark:border-white/10">
            <span>{{ item.date }} · {{ item.closed ? ('hours.closed' | translate) : intervalLabel(item.intervals) }}
              @if (item.description) { · {{ item.description }} }
            </span>
            @if (canEdit) {
              <button type="button" class="text-rose-600" (click)="removeException(item)">{{ 'common.delete' | translate }}</button>
            }
          </div>
        }
        @if (canEdit) {
          <div class="grid gap-2 sm:grid-cols-2">
            <input class="input" type="date" [(ngModel)]="exceptionDate" />
            <input class="input" [placeholder]="'hours.exceptionNote' | translate" [(ngModel)]="exceptionNote" />
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" [(ngModel)]="exceptionClosed" /> {{ 'hours.closed' | translate }}
            </label>
            @if (!exceptionClosed) {
              <div class="flex items-center gap-2">
                <input class="input w-28" type="time" [(ngModel)]="exceptionOpen" />
                <input class="input w-28" type="time" [(ngModel)]="exceptionClose" />
              </div>
            }
            <button type="button" class="btn-secondary sm:col-span-2" (click)="addException()">{{ 'hours.addException' | translate }}</button>
          </div>
        }
      }
    </section>

    @if (copyOpen) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="copyOpen = 0">
        <div class="card max-w-md space-y-3" (click)="$event.stopPropagation()">
          <h3 class="font-medium">{{ 'hours.copyTitle' | translate }}</h3>
          @for (day of days; track day.dayOfWeek) {
            @if (day.dayOfWeek !== copyOpen) {
              <label class="flex items-center gap-2 text-sm">
                <input type="checkbox" [checked]="copyTargets.has(day.dayOfWeek)" (change)="toggleCopy(day.dayOfWeek)" />
                {{ ('hours.day' + day.dayOfWeek) | translate }}
              </label>
            }
          }
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="copyOpen = 0">{{ 'common.cancel' | translate }}</button>
            <button type="button" class="btn-primary" (click)="applyCopy()">{{ 'common.confirm' | translate }}</button>
          </div>
        </div>
      </div>
    }

    @if (affected().length) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4">
        <div class="card max-w-lg space-y-3">
          <h3 class="font-medium">{{ 'hours.affectedTitle' | translate }}</h3>
          <p class="text-sm text-slate-500">{{ 'hours.affectedBody' | translate }}</p>
          <ul class="max-h-48 space-y-1 overflow-auto text-sm">
            @for (item of affected(); track item.id) {
              <li>{{ item.startAt | date:'short' }} · {{ item.petName }} · {{ item.status }}</li>
            }
          </ul>
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="affected.set([])">{{ 'common.cancel' | translate }}</button>
            <button type="button" class="btn-primary" (click)="save(true)">{{ 'hours.saveAnyway' | translate }}</button>
          </div>
        </div>
      </div>
    }
  `
})
export class BusinessHoursEditorComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private session = inject(SessionInactivityService);

  branches = signal<BranchRef[]>([]);
  branchId = signal<number | null>(null);
  configured = signal(false);
  exceptions = signal<HoursException[]>([]);
  loading = signal(true);
  saving = signal(false);
  error = signal('');
  saveError = signal('');
  affected = signal<AffectedAppointment[]>([]);
  days: DayHours[] = EMPTY_DAYS.map(day => ({ ...day, intervals: [] }));
  timezone = 'America/Santo_Domingo';
  zones = TIMEZONES;
  copyOpen = 0;
  copyTargets = new Set<number>();
  exceptionDate = '';
  exceptionNote = '';
  exceptionClosed = true;
  exceptionOpen = '09:00';
  exceptionClose = '13:00';

  get canEdit(): boolean {
    return this.auth.hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')
      || this.auth.hasPermission('BRANCH_MANAGE')
      || this.auth.hasPermission('SETTINGS_UPDATE');
  }

  ngOnInit(): void {
    this.api.get<BranchRef[]>('/branches').subscribe({
      next: rows => {
        this.branches.set(rows || []);
        if (rows?.length) {
          this.branchId.set(rows[0].id);
          this.load();
        } else {
          this.loading.set(false);
        }
      },
      error: err => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'common.error'));
      }
    });
  }

  onBranch(id: number): void {
    this.branchId.set(id);
    this.load();
  }

  load(): void {
    const clinicId = this.auth.user()?.tenantId;
    const branchId = this.branchId();
    if (!clinicId || !branchId) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.api.get<HoursResponse>(`/clinics/${clinicId}/business-hours`, { branchId }).subscribe({
      next: hours => {
        this.timezone = hours.timezone || 'America/Santo_Domingo';
        if (!this.zones.includes(this.timezone)) {
          this.zones = [this.timezone, ...TIMEZONES];
        }
        this.configured.set(!!hours.configured);
        this.days = EMPTY_DAYS.map(base => {
          const found = hours.days?.find(day => day.dayOfWeek === base.dayOfWeek);
          return {
            dayOfWeek: base.dayOfWeek,
            closed: found?.closed ?? true,
            intervals: (found?.intervals || []).map(interval => ({ ...interval }))
          };
        });
        this.exceptions.set(hours.exceptions || []);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'common.error'));
      }
    });
  }

  toggleDay(day: DayHours, open: boolean): void {
    day.closed = !open;
    if (open && !day.intervals.length) {
      day.intervals = [{ open: '09:00', close: '18:00' }];
    }
    if (!open) {
      day.intervals = [];
    }
  }

  addInterval(day: DayHours): void {
    day.intervals = [...day.intervals, { open: '14:00', close: '18:00' }];
  }

  removeInterval(day: DayHours, index: number): void {
    day.intervals = day.intervals.filter((_, i) => i !== index);
    if (!day.intervals.length) {
      day.closed = true;
    }
  }

  overlapWarning(day: DayHours): boolean {
    const sorted = [...day.intervals].sort((a, b) => a.open.localeCompare(b.open));
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].open < sorted[i - 1].close) {
        return true;
      }
    }
    return false;
  }

  toggleCopy(day: number): void {
    if (this.copyTargets.has(day)) {
      this.copyTargets.delete(day);
    } else {
      this.copyTargets.add(day);
    }
  }

  applyCopy(): void {
    const source = this.days.find(day => day.dayOfWeek === this.copyOpen);
    if (!source) {
      this.copyOpen = 0;
      return;
    }
    this.days = this.days.map(day => this.copyTargets.has(day.dayOfWeek)
      ? { ...day, closed: source.closed, intervals: source.intervals.map(interval => ({ ...interval })) }
      : day);
    this.copyTargets = new Set();
    this.copyOpen = 0;
  }

  save(confirm: boolean): void {
    if (!this.session.ensureActive() || !this.canEdit) {
      return;
    }
    const clinicId = this.auth.user()?.tenantId;
    const branchId = this.branchId();
    if (!clinicId || !branchId) {
      return;
    }
    this.saving.set(true);
    this.saveError.set('');
    this.api.put(`/clinics/${clinicId}/business-hours?branchId=${branchId}`, {
      timezone: this.timezone,
      days: this.days,
      confirmAffectedAppointments: confirm
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.affected.set([]);
        this.toast.show('common.saved');
        this.load();
      },
      error: (err: { status?: number; error?: { details?: { affectedAppointments?: AffectedAppointment[] } } }) => {
        this.saving.set(false);
        const details = err.error?.details || {};
        if (err.status === 409 && details.affectedAppointments?.length) {
          this.affected.set(details.affectedAppointments);
          return;
        }
        this.saveError.set(apiErrorMessage(err, 'common.error'));
      }
    });
  }

  addException(): void {
    if (!this.session.ensureActive() || !this.canEdit || !this.exceptionDate) {
      return;
    }
    const clinicId = this.auth.user()?.tenantId;
    const branchId = this.branchId();
    if (!clinicId || !branchId) {
      return;
    }
    this.api.post(`/clinics/${clinicId}/business-hours/exceptions?branchId=${branchId}`, {
      date: this.exceptionDate,
      closed: this.exceptionClosed,
      description: this.exceptionNote,
      intervals: this.exceptionClosed ? [] : [{ open: this.exceptionOpen, close: this.exceptionClose }]
    }).subscribe({
      next: () => {
        this.toast.show('common.saved');
        this.exceptionDate = '';
        this.exceptionNote = '';
        this.load();
      },
      error: err => this.toast.show(apiErrorMessage(err, 'common.error'), true)
    });
  }

  removeException(item: HoursException): void {
    const clinicId = this.auth.user()?.tenantId;
    const branchId = this.branchId();
    if (!clinicId || !branchId) {
      return;
    }
    this.api.delete(`/clinics/${clinicId}/business-hours/exceptions/${item.id}?branchId=${branchId}`).subscribe({
      next: () => this.load(),
      error: err => this.toast.show(apiErrorMessage(err, 'common.error'), true)
    });
  }

  intervalLabel(intervals: Interval[]): string {
    return (intervals || []).map(item => `${item.open}–${item.close}`).join(', ');
  }
}
