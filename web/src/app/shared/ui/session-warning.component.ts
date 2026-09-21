import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { SessionInactivityService } from '../../core/services/session-inactivity.service';

@Component({
  selector: 'app-session-warning',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    @if (session.warningOpen()) {
      <div class="fixed inset-0 z-[90] grid place-items-center bg-black/40 p-4">
        <div class="card w-full max-w-md space-y-4" role="alertdialog" aria-labelledby="session-warning-title">
          <h2 id="session-warning-title" class="font-display text-lg font-semibold">{{ 'auth.sessionExpiringTitle' | translate }}</h2>
          <p class="text-sm text-slate-600 dark:text-slate-300">{{ 'auth.sessionExpiring' | translate }}</p>
          <div class="flex flex-wrap justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="session.expire('MANUAL')">{{ 'nav.logout' | translate }}</button>
            <button type="button" class="btn-primary" (click)="session.continueSession()">{{ 'auth.keepSession' | translate }}</button>
          </div>
        </div>
      </div>
    }
  `
})
export class SessionWarningComponent {
  session = inject(SessionInactivityService);
}
