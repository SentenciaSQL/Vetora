import { Component, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-language-selector',
  standalone: true,
  template: `
    <label class="sr-only" for="lang-select">Language</label>
    <select id="lang-select" class="rounded-xl border border-slate-200 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900"
            [value]="i18n.getCurrentLang() || 'es'" (change)="change($any($event.target).value)">
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
    if (this.auth.isAuthenticated) {
      this.auth.patchMe({ locale }).subscribe();
    }
  }
}
