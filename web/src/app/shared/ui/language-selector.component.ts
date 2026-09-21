import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-language-selector',
  standalone: true,
  imports: [FormsModule],
  template: `
    <label class="sr-only" for="lang-select">Language</label>
    <select id="lang-select"
            class="rounded-xl border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
            [ngModel]="i18n.getCurrentLang() || 'es'"
            (ngModelChange)="change($event)">
      <option value="es">ES</option>
      <option value="en">EN</option>
    </select>
  `
})
export class LanguageSelectorComponent {
  i18n = inject(TranslateService);
  private auth = inject(AuthService);

  change(locale: string): void {
    this.i18n.use(locale);
    document.documentElement.lang = locale;
    localStorage.setItem('animalin.locale', locale);
    if (!this.auth.isAuthenticated) {
      return;
    }
    this.auth.patchMe({ locale }).subscribe({ error: () => undefined });
  }
}
