import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ToastHostComponent } from './shared/ui/toast-host.component';
import { ThemeService } from './core/services/theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastHostComponent],
  template: `
    <router-outlet />
    <app-toast-host />
  `
})
export class AppComponent {
  constructor() {
    inject(ThemeService);
    const i18n = inject(TranslateService);
    i18n.addLangs(['es', 'en']);
    i18n.setFallbackLang('es');
    const locale = localStorage.getItem('animalin.locale') || 'es';
    i18n.use(locale);
    document.documentElement.lang = locale;
  }
}
