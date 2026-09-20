import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminPlan, PaddleSyncResult } from '../../core/models';

export interface PlanDraft extends AdminPlan {
  formError?: string;
}

export function emptyPlanDraft(): PlanDraft {
  return {
    code: '',
    nameEs: '',
    nameEn: '',
    descriptionEs: '',
    descriptionEn: '',
    currency: 'USD',
    monthlyPrice: 0,
    annualPrice: null,
    paddleProductId: '',
    paddleMonthlyPriceId: '',
    paddleAnnualPriceId: '',
    active: true,
    maxUsers: 5,
    maxVeterinarians: 2,
    maxBranches: 1,
    maxStorageMb: 1024,
    maxMessagesMonth: 200,
    reportsEnabled: true,
    messagingEnabled: true,
    laboratoryEnabled: false,
    paddleSyncStatus: 'UNKNOWN',
    subscriberCount: 0
  };
}

export function flattenAdminPlan(plan: AdminPlan): PlanDraft {
  const limits = plan.limits || (plan as PlanDraft);
  return {
    ...emptyPlanDraft(),
    ...plan,
    maxUsers: limits.maxUsers,
    maxVeterinarians: limits.maxVeterinarians,
    maxBranches: limits.maxBranches,
    maxStorageMb: limits.maxStorageMb,
    maxMessagesMonth: limits.maxMessagesMonth,
    reportsEnabled: limits.reportsEnabled,
    messagingEnabled: limits.messagingEnabled,
    laboratoryEnabled: limits.laboratoryEnabled,
    formError: undefined
  };
}

export function validatePlanDraft(plan: PlanDraft, requireCode = true): string[] {
  const errors: string[] = [];
  if (requireCode && !plan.code?.trim()) {
    errors.push('El código del plan no puede estar vacío');
  }
  if (plan.monthlyPrice == null || Number(plan.monthlyPrice) <= 0) {
    errors.push('El precio mensual debe ser mayor que cero');
  }
  const hasAnnual = plan.annualPrice != null && plan.annualPrice !== ('' as unknown) && !Number.isNaN(Number(plan.annualPrice));
  if (hasAnnual && Number(plan.annualPrice) <= 0) {
    errors.push('El precio anual debe ser mayor que cero');
  }
  if (hasAnnual && Number(plan.monthlyPrice) > 0 && Number(plan.annualPrice) >= Number(plan.monthlyPrice) * 12) {
    errors.push('El precio anual debe ser menor que el precio mensual multiplicado por 12');
  }
  const currency = (plan.currency || 'USD').toUpperCase();
  if (currency !== 'USD') {
    errors.push('La moneda debe ser USD');
  }
  for (const [label, value] of [
    ['usuarios', plan.maxUsers],
    ['veterinarios', plan.maxVeterinarians],
    ['sucursales', plan.maxBranches],
    ['almacenamiento', plan.maxStorageMb],
    ['mensajes', plan.maxMessagesMonth]
  ] as const) {
    if (value == null || Number(value) < 0) {
      errors.push(`El límite de ${label} no puede ser negativo`);
    }
  }
  if (plan.paddleProductId && !String(plan.paddleProductId).startsWith('pro_')) {
    errors.push('El Paddle Product ID debe comenzar por pro_');
  }
  if (plan.paddleMonthlyPriceId && !String(plan.paddleMonthlyPriceId).startsWith('pri_')) {
    errors.push('El Paddle Monthly Price ID debe comenzar por pri_');
  }
  if (plan.paddleAnnualPriceId && !String(plan.paddleAnnualPriceId).startsWith('pri_')) {
    errors.push('El Paddle Annual Price ID debe comenzar por pri_');
  }
  if (plan.paddleMonthlyPriceId && plan.paddleAnnualPriceId
      && String(plan.paddleMonthlyPriceId) === String(plan.paddleAnnualPriceId)) {
    errors.push('Los Price ID mensual y anual no pueden ser iguales');
  }
  return errors;
}

export function annualSaleBlocked(plan: PlanDraft): boolean {
  return plan.annualPrice != null && Number(plan.annualPrice) > 0 && !String(plan.paddleAnnualPriceId || '').startsWith('pri_');
}

export function payloadFromDraft(plan: PlanDraft) {
  return {
    nameEs: plan.nameEs,
    nameEn: plan.nameEn,
    descriptionEs: plan.descriptionEs,
    descriptionEn: plan.descriptionEn,
    currency: plan.currency || 'USD',
    monthlyPrice: Number(plan.monthlyPrice),
    annualPrice: plan.annualPrice === null || plan.annualPrice === undefined || plan.annualPrice === ('' as unknown)
      ? null
      : Number(plan.annualPrice),
    paddleProductId: plan.paddleProductId || null,
    paddleMonthlyPriceId: plan.paddleMonthlyPriceId || null,
    paddleAnnualPriceId: plan.paddleAnnualPriceId || null,
    maxUsers: Number(plan.maxUsers),
    maxVeterinarians: Number(plan.maxVeterinarians),
    maxBranches: Number(plan.maxBranches),
    maxStorageMb: Number(plan.maxStorageMb),
    maxMessagesMonth: Number(plan.maxMessagesMonth),
    reportsEnabled: !!plan.reportsEnabled,
    messagingEnabled: !!plan.messagingEnabled,
    laboratoryEnabled: !!plan.laboratoryEnabled,
    active: !!plan.active
  };
}

@Component({
  standalone: true,
  imports: [CommonModule, TranslatePipe, FormsModule],
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'nav.plans' | translate }}</h1>
        <p class="text-sm text-slate-500">{{ 'admin.plansSubtitle' | translate }}</p>
      </div>
      <button type="button" class="btn-primary" [disabled]="busy()" (click)="startCreate()">{{ 'admin.newPlan' | translate }}</button>
    </div>

    @if (creating()) {
      <form class="card mt-6 space-y-3" (ngSubmit)="create()">
        <h2 class="font-display text-lg font-semibold">{{ 'admin.newPlan' | translate }}</h2>
        <div class="grid gap-3 md:grid-cols-2">
          <label class="text-xs text-slate-500">{{ 'admin.planCode' | translate }}
            <input class="input mt-1" [(ngModel)]="draft().code" name="newCode" />
          </label>
          <label class="text-xs text-slate-500">{{ 'admin.currency' | translate }}
            <input class="input mt-1" [(ngModel)]="draft().currency" name="newCurrency" />
          </label>
        </div>
        <ng-container *ngTemplateOutlet="fields; context: { $implicit: draft(), prefix: 'new' }"></ng-container>
        @if (draft().formError) {
          <p class="text-sm text-rose-600">{{ draft().formError }}</p>
        }
        <div class="flex gap-2">
          <button type="submit" class="btn-primary" [disabled]="busy()">{{ 'common.create' | translate }}</button>
          <button type="button" class="btn-secondary" (click)="creating.set(false)">{{ 'common.cancel' | translate }}</button>
        </div>
      </form>
    }

    <div class="mt-6 grid gap-4 xl:grid-cols-2">
      @for (p of plans(); track p.id) {
        <form class="card space-y-3" (ngSubmit)="save(p)">
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="text-xs uppercase tracking-wide text-slate-400">{{ p.code }} · {{ p.subscriberCount || 0 }} {{ 'admin.subscribers' | translate }}</p>
              <h2 class="font-display text-xl font-semibold">{{ lang === 'en' ? p.nameEn : p.nameEs }}</h2>
            </div>
            <span class="rounded-full px-2 py-1 text-xs"
                  [class.bg-emerald-100]="p.paddleSyncStatus === 'IN_SYNC'"
                  [class.bg-amber-100]="p.paddleSyncStatus === 'DRIFT' || p.paddleSyncStatus === 'UNKNOWN'"
                  [class.bg-rose-100]="p.paddleSyncStatus === 'ERROR'">
              {{ ('admin.sync.' + (p.paddleSyncStatus || 'UNKNOWN')) | translate }}
            </span>
          </div>
          <p class="text-2xl font-semibold">{{ p.monthlyPrice | number:'1.2-2' }} {{ p.currency || 'USD' }}
            <span class="text-sm font-normal text-slate-500">{{ 'billing.perMonth' | translate }}</span>
          </p>
          @if (p.annualPrice) {
            <p class="text-sm text-slate-500">{{ p.annualPrice | number:'1.2-2' }} {{ p.currency }} {{ 'billing.perYear' | translate }}</p>
          }
          <ng-container *ngTemplateOutlet="fields; context: { $implicit: p, prefix: p.id }"></ng-container>
          @if (p.formError) {
            <p class="text-sm text-rose-600">{{ p.formError }}</p>
          }
          @if (annualSaleBlocked(p)) {
            <p class="text-xs text-amber-700">{{ 'admin.annualIdRequired' | translate }}</p>
          }
          <div class="flex flex-wrap gap-2">
            <button type="submit" class="btn-primary text-sm" [disabled]="busy()">{{ 'common.save' | translate }}</button>
            <button type="button" class="btn-secondary text-sm" [disabled]="busy()" (click)="validate(p)">{{ 'admin.validatePaddle' | translate }}</button>
            <button type="button" class="btn-secondary text-sm" [disabled]="busy()" (click)="rotate(p, 'MONTHLY')">{{ 'admin.updateMonthlyPrice' | translate }}</button>
            <button type="button" class="btn-secondary text-sm" [disabled]="busy() || p.annualPrice == null" (click)="rotate(p, 'ANNUAL')">{{ 'admin.updateAnnualPrice' | translate }}</button>
          </div>
        </form>
      }
    </div>

    <ng-template #fields let-p let-prefix="prefix">
      <div class="grid gap-3 md:grid-cols-2">
        <label class="text-xs text-slate-500">{{ 'admin.nameEs' | translate }}
          <input class="input mt-1" [(ngModel)]="p.nameEs" [name]="'nameEs' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'admin.nameEn' | translate }}
          <input class="input mt-1" [(ngModel)]="p.nameEn" [name]="'nameEn' + prefix" />
        </label>
        <label class="text-xs text-slate-500 md:col-span-2">{{ 'admin.descriptionEs' | translate }}
          <textarea class="input mt-1" rows="2" [(ngModel)]="p.descriptionEs" [name]="'descEs' + prefix"></textarea>
        </label>
        <label class="text-xs text-slate-500 md:col-span-2">{{ 'admin.descriptionEn' | translate }}
          <textarea class="input mt-1" rows="2" [(ngModel)]="p.descriptionEn" [name]="'descEn' + prefix"></textarea>
        </label>
        <label class="text-xs text-slate-500">{{ 'admin.monthlyPrice' | translate }}
          <input class="input mt-1" type="number" min="0" step="0.01" [(ngModel)]="p.monthlyPrice" [name]="'month' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'admin.annualPrice' | translate }}
          <input class="input mt-1" type="number" min="0" step="0.01" [(ngModel)]="p.annualPrice" [name]="'year' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'admin.users' | translate }}
          <input class="input mt-1" type="number" min="0" [(ngModel)]="p.maxUsers" [name]="'users' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'nav.team' | translate }}
          <input class="input mt-1" type="number" min="0" [(ngModel)]="p.maxVeterinarians" [name]="'vets' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'nav.branches' | translate }}
          <input class="input mt-1" type="number" min="0" [(ngModel)]="p.maxBranches" [name]="'branches' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'admin.storageMb' | translate }}
          <input class="input mt-1" type="number" min="0" [(ngModel)]="p.maxStorageMb" [name]="'storage' + prefix" />
        </label>
        <label class="text-xs text-slate-500">{{ 'admin.messagesMonth' | translate }}
          <input class="input mt-1" type="number" min="0" [(ngModel)]="p.maxMessagesMonth" [name]="'msgs' + prefix" />
        </label>
        <label class="text-xs text-slate-500">Paddle Product ID
          <input class="input mt-1 font-mono text-xs" [(ngModel)]="p.paddleProductId" [name]="'pro' + prefix" />
        </label>
        <label class="text-xs text-slate-500">Paddle Monthly Price ID
          <input class="input mt-1 font-mono text-xs" [(ngModel)]="p.paddleMonthlyPriceId" [name]="'priM' + prefix" />
        </label>
        <label class="text-xs text-slate-500">Paddle Annual Price ID
          <input class="input mt-1 font-mono text-xs" [(ngModel)]="p.paddleAnnualPriceId" [name]="'priY' + prefix" />
        </label>
      </div>
      <div class="flex flex-wrap gap-4 text-xs">
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="p.reportsEnabled" [name]="'rep' + prefix" /> {{ 'nav.reports' | translate }}</label>
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="p.messagingEnabled" [name]="'msg' + prefix" /> {{ 'nav.messages' | translate }}</label>
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="p.laboratoryEnabled" [name]="'lab' + prefix" /> {{ 'pets.tabs.labs' | translate }}</label>
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="p.active" [name]="'act' + prefix" /> {{ 'admin.active' | translate }}</label>
      </div>
    </ng-template>
  `
})
export class AdminPlansPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private i18n = inject(TranslateService);
  plans = signal<PlanDraft[]>([]);
  draft = signal<PlanDraft>(emptyPlanDraft());
  creating = signal(false);
  busy = signal(false);
  lang = 'es';
  readonly annualSaleBlocked = annualSaleBlocked;

  ngOnInit() {
    this.lang = this.i18n.getCurrentLang() || 'es';
    this.i18n.onLangChange.subscribe(e => this.lang = e.lang);
    this.reload();
  }

  reload() {
    this.api.get<AdminPlan[]>('/admin/plans').subscribe(plans => this.plans.set(plans.map(flattenAdminPlan)));
  }

  startCreate() {
    this.draft.set(emptyPlanDraft());
    this.creating.set(true);
  }

  create() {
    const plan = this.draft();
    const errors = validatePlanDraft(plan, true);
    if (errors.length) {
      this.draft.set({ ...plan, formError: errors[0] });
      return;
    }
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.post<AdminPlan>('/admin/plans', {
      ...payloadFromDraft(plan),
      code: plan.code.trim().toUpperCase(),
      syncToPaddle: false
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.creating.set(false);
        this.toast.show('common.saved');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        this.draft.set({ ...plan, formError: err.error?.message || 'common.error' });
        this.toast.showHttpError(err);
      }
    });
  }

  save(plan: PlanDraft) {
    const errors = validatePlanDraft(plan, false);
    if (errors.length) {
      plan.formError = errors[0];
      return;
    }
    if (this.busy() || !plan.id) {
      return;
    }
    this.busy.set(true);
    this.api.put(`/admin/plans/${plan.id}`, payloadFromDraft(plan)).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('common.saved');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        plan.formError = err.error?.message || 'common.error';
        this.toast.showHttpError(err);
      }
    });
  }

  validate(plan: PlanDraft) {
    if (this.busy() || !plan.id) {
      return;
    }
    const errors = validatePlanDraft(plan, false);
    if (errors.length) {
      plan.formError = errors[0];
      return;
    }
    this.busy.set(true);
    this.api.put<AdminPlan>(`/admin/plans/${plan.id}`, payloadFromDraft(plan)).subscribe({
      next: () => this.api.post<PaddleSyncResult>(`/admin/plans/${plan.id}/paddle/validate`, {}).subscribe({
        next: result => {
          this.busy.set(false);
          this.toast.show(result.inSync ? 'admin.paddleInSync' : 'admin.paddleDrift');
          this.reload();
        },
        error: err => {
          this.busy.set(false);
          plan.formError = err.error?.message || 'common.error';
          this.toast.showHttpError(err);
        }
      }),
      error: err => {
        this.busy.set(false);
        this.toast.showHttpError(err);
      }
    });
  }

  rotate(plan: PlanDraft, cycle: 'MONTHLY' | 'ANNUAL') {
    if (this.busy() || !plan.id) {
      return;
    }
    const amount = cycle === 'MONTHLY' ? Number(plan.monthlyPrice) : Number(plan.annualPrice);
    if (amount == null || Number.isNaN(amount) || amount <= 0) {
      plan.formError = cycle === 'MONTHLY' ? 'El precio mensual debe ser mayor que cero' : 'El precio anual debe ser mayor que cero';
      return;
    }
    if (cycle === 'ANNUAL' && Number(plan.monthlyPrice) > 0 && amount >= Number(plan.monthlyPrice) * 12) {
      plan.formError = 'El precio anual debe ser menor que el precio mensual multiplicado por 12';
      return;
    }
    if (!confirm(this.i18n.instant('admin.rotatePriceConfirm'))) {
      return;
    }
    this.busy.set(true);
    this.api.post(`/admin/plans/${plan.id}/paddle/prices`, { cycle, amount, confirm: true }).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('admin.priceRotated');
        this.reload();
      },
      error: err => {
        this.busy.set(false);
        plan.formError = err.error?.message || 'common.error';
        this.toast.showHttpError(err);
      }
    });
  }
}
