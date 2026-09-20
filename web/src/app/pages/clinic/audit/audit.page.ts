import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { PageResponse } from '../../../core/models';

@Component({
  standalone: true,
  imports: [TranslatePipe, DatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'nav.audit' | translate }}</h1>
    <p class="mt-1 text-sm text-slate-500">{{ 'admin.clinicAuditSubtitle' | translate }}</p>
    <div class="card mt-6 overflow-x-auto p-0">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
          <tr>
            <th class="px-4 py-3">{{ 'common.status' | translate }}</th>
            <th class="px-4 py-3">{{ 'owners.name' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.entity' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.when' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (a of rows(); track a.id) {
            <tr class="border-t border-slate-100 dark:border-white/5">
              <td class="px-4 py-3 font-medium">{{ a.action }}</td>
              <td class="px-4 py-3">{{ a.username }}</td>
              <td class="px-4 py-3">{{ a.entityType }} #{{ a.entityId }}</td>
              <td class="px-4 py-3">{{ a.createdAt | date:'short' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `
})
export class ClinicAuditPage implements OnInit {
  private api = inject(ApiService);
  rows = signal<any[]>([]);
  ngOnInit() {
    this.api.get<PageResponse<any>>('/audit', { size: 50 }).subscribe(p => this.rows.set(p.content || []));
  }
}
