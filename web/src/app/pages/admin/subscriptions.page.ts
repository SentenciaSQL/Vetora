import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { StatusBadgePipe } from '../../shared/ui/status-badge.pipe';

@Component({
  standalone: true,
  imports: [CommonModule, TranslatePipe, StatusBadgePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'nav.subscriptions' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ 'admin.plansSubtitle' | translate }}</p>
    <div class="mt-6 overflow-x-auto">
      <table class="w-full min-w-[40rem] text-left text-sm">
        <thead class="text-xs uppercase text-slate-400">
          <tr>
            <th class="py-2">{{ 'admin.tenants' | translate }}</th>
            <th>{{ 'admin.plan' | translate }}</th>
            <th>{{ 'common.status' | translate }}</th>
            <th>{{ 'reports.from' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (s of items(); track s.id) {
            <tr class="border-t border-slate-100 dark:border-white/10">
              <td class="py-3">{{ s.tenantName }}</td>
              <td>{{ s.planCode }}</td>
              <td><span [class]="s.status | statusBadge">{{ s.status }}</span></td>
              <td>{{ s.startedAt | date:'mediumDate' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `
})
export class AdminSubscriptionsPage implements OnInit {
  private api = inject(ApiService);
  items = signal<any[]>([]);
  ngOnInit() {
    this.api.get<any[]>('/admin/subscriptions').subscribe(s => this.items.set(s));
  }
}
