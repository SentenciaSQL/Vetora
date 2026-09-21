import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ToastHostComponent } from './shared/ui/toast-host.component';
import { SessionWarningComponent } from './shared/ui/session-warning.component';
import { ThemeService } from './core/services/theme.service';
import { SessionInactivityService } from './core/services/session-inactivity.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastHostComponent, SessionWarningComponent],
  template: `
    <router-outlet />
    <app-session-warning />
    <app-toast-host />
  `
})
export class AppComponent {
  constructor() {
    inject(ThemeService);
    inject(SessionInactivityService);
    const i18n = inject(TranslateService);
    i18n.addLangs(['es', 'en']);
    i18n.setFallbackLang('es');
    const locale = localStorage.getItem('animalin.locale') || 'es';
    i18n.use(locale);
    document.documentElement.lang = locale;
  }
}
