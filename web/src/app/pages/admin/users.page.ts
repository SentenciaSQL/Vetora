import { Component, inject, OnInit, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { PageResponse } from '../../core/models';

@Component({
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'nav.users' | translate }}</h1>
    <p class="mt-1 text-sm text-slate-500">{{ 'admin.usersSubtitle' | translate }}</p>
    <div class="card mt-6 overflow-x-auto p-0">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
          <tr>
            <th class="px-4 py-3">{{ 'owners.name' | translate }}</th>
            <th class="px-4 py-3">{{ 'auth.email' | translate }}</th>
            <th class="px-4 py-3">{{ 'nav.profile' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (u of users(); track u.id) {
            <tr class="border-t border-slate-100 dark:border-white/5">
              <td class="px-4 py-3 font-medium">{{ u.fullName }}</td>
              <td class="px-4 py-3">{{ u.email }}</td>
              <td class="px-4 py-3 text-xs">{{ (u.roles || []).join(', ') }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `
})
export class AdminUsersPage implements OnInit {
  private api = inject(ApiService);
  users = signal<any[]>([]);
  ngOnInit() {
    this.api.get<any[]>('/admin/users').subscribe(u => this.users.set(u || []));
  }
}
