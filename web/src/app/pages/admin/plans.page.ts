import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  standalone: true,
  imports: [TranslatePipe, FormsModule],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'nav.plans' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ 'admin.plansSubtitle' | translate }}</p>
    <div class="mt-6 grid gap-4 md:grid-cols-3">
      @for (p of plans(); track p.id) {
        <form class="card space-y-2" (ngSubmit)="save(p)">
          <p class="text-xs uppercase tracking-wide text-slate-400">{{ p.code }}</p>
          <h2 class="font-display text-xl font-semibold">{{ lang === 'en' ? p.nameEn : p.nameEs }}</h2>
          <p class="text-sm text-slate-500">{{ lang === 'en' ? p.descriptionEn : p.descriptionEs }}</p>
          <p class="text-2xl font-semibold">{{ p.monthlyPrice }} €</p>
          <label class="text-xs text-slate-500">{{ 'admin.users' | translate }}
            <input class="input mt-1" type="number" [(ngModel)]="p.maxUsers" name="users{{ p.id }}" />
          </label>
          <label class="text-xs text-slate-500">{{ 'nav.team' | translate }}
            <input class="input mt-1" type="number" [(ngModel)]="p.maxVeterinarians" name="vets{{ p.id }}" />
          </label>
          <label class="text-xs text-slate-500">{{ 'nav.branches' | translate }}
            <input class="input mt-1" type="number" [(ngModel)]="p.maxBranches" name="branches{{ p.id }}" />
          </label>
          <label class="flex items-center gap-2 text-xs"><input type="checkbox" [(ngModel)]="p.reportsEnabled" name="rep{{ p.id }}" /> {{ 'nav.reports' | translate }}</label>
          <label class="flex items-center gap-2 text-xs"><input type="checkbox" [(ngModel)]="p.messagingEnabled" name="msg{{ p.id }}" /> {{ 'nav.messages' | translate }}</label>
          <label class="flex items-center gap-2 text-xs"><input type="checkbox" [(ngModel)]="p.laboratoryEnabled" name="lab{{ p.id }}" /> {{ 'pets.tabs.labs' | translate }}</label>
          <button class="btn-primary w-full text-sm">{{ 'common.save' | translate }}</button>
        </form>
      }
    </div>
  `
})
export class AdminPlansPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private i18n = inject(TranslateService);
  plans = signal<any[]>([]);
  lang = 'es';
  ngOnInit() {
    this.lang = this.i18n.getCurrentLang() || 'es';
    this.i18n.onLangChange.subscribe(e => this.lang = e.lang);
    this.api.get<any[]>('/admin/plans').subscribe(p => this.plans.set(p));
  }
  save(plan: any) {
    this.api.put(`/admin/plans/${plan.id}`, {
      maxUsers: plan.maxUsers,
      maxVeterinarians: plan.maxVeterinarians,
      maxBranches: plan.maxBranches,
      reportsEnabled: plan.reportsEnabled,
      messagingEnabled: plan.messagingEnabled,
      laboratoryEnabled: plan.laboratoryEnabled
    }).subscribe({
      next: () => this.toast.show('common.saved'),
      error: () => this.toast.show('common.error', true)
    });
  }
}
