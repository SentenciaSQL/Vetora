import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { ThemeMode, ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-theme-selector',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <label class="sr-only" for="theme-select">{{ 'theme.label' | translate }}</label>
    <select id="theme-select" class="rounded-xl border border-slate-200 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900"
            [value]="theme.mode()" (change)="change($any($event.target).value)">
      <option value="light">{{ 'theme.light' | translate }}</option>
      <option value="dark">{{ 'theme.dark' | translate }}</option>
      <option value="system">{{ 'theme.system' | translate }}</option>
    </select>
  `
})
export class ThemeSelectorComponent {
  theme = inject(ThemeService);
  private auth = inject(AuthService);

  change(mode: ThemeMode): void {
    this.theme.set(mode);
    if (this.auth.isAuthenticated) {
      this.auth.patchMe({ theme: mode }).subscribe();
    }
  }
}
