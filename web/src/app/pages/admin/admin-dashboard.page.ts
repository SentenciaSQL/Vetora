import { Component, inject, OnInit, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { RouterLink } from '@angular/router';

@Component({
  standalone: true,
  imports: [TranslatePipe, StatCardComponent, RouterLink],
  template: `
    <div class="flex items-end justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'admin.title' | translate }}</h1>
        <p class="text-sm text-slate-500">{{ 'admin.subtitle' | translate }}</p>
      </div>
      <a routerLink="/admin/tenants" class="btn-primary">{{ 'nav.tenants' | translate }}</a>
    </div>
    <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <app-stat-card [label]="'admin.tenants' | translate" [value]="m().tenants || 0" />
      <app-stat-card [label]="'admin.active' | translate" [value]="m().activeTenants || 0" />
      <app-stat-card [label]="'admin.trial' | translate" [value]="m().trialTenants || 0" />
      <app-stat-card [label]="'admin.suspended' | translate" [value]="m().suspendedTenants || 0" />
      <app-stat-card [label]="'admin.users' | translate" [value]="m().users || 0" />
      <app-stat-card [label]="'admin.owners' | translate" [value]="m().owners || 0" />
      <app-stat-card [label]="'admin.pets' | translate" [value]="m().pets || 0" />
      <app-stat-card [label]="'admin.appointments' | translate" [value]="m().appointments || 0" />
    </div>
  `
})
export class AdminDashboardPage implements OnInit {
  private api = inject(ApiService);
  m = signal<any>({});
  ngOnInit() {
    this.api.get('/admin/metrics').subscribe(d => this.m.set(d));
  }
}
