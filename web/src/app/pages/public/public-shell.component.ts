import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageSelectorComponent } from '../../shared/ui/language-selector.component';

@Component({
  selector: 'app-public-shell',
  standalone: true,
  imports: [RouterLink, TranslatePipe, LanguageSelectorComponent],
  template: `
    <div class="min-h-screen bg-sand-50 text-slate-800 dark:bg-slate-950 dark:text-slate-100">
      <header class="sticky top-0 z-20 border-b border-slate-200/80 bg-sand-50/95 backdrop-blur dark:border-slate-800 dark:bg-slate-950/95">
        <div class="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-5 py-4 sm:px-8">
          <a routerLink="/" class="flex items-center gap-3 font-display text-lg font-semibold text-brand-800 dark:text-brand-100">
            <img src="/assets/branding/logo.svg" width="36" height="36" alt="" class="h-9 w-9 rounded-xl" />
            LunaVeta
          </a>
          <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <nav class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm" [attr.aria-label]="'public.nav.label' | translate">
              <a routerLink="/pricing" class="font-medium text-brand-700 hover:underline">{{ 'public.nav.pricing' | translate }}</a>
              <a routerLink="/register" class="font-medium text-brand-700 hover:underline">{{ 'public.nav.register' | translate }}</a>
              <a routerLink="/login" class="font-medium text-brand-700 hover:underline">{{ 'public.nav.signIn' | translate }}</a>
            </nav>
            <app-language-selector [full]="true" />
          </div>
        </div>
      </header>

      <main class="mx-auto max-w-6xl px-5 py-10 sm:px-8 sm:py-14">
        <ng-content />
      </main>

      <footer class="border-t border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div class="mx-auto grid max-w-6xl gap-8 px-5 py-10 sm:px-8 md:grid-cols-3">
          <div>
            <p class="font-display text-lg font-semibold text-brand-800 dark:text-brand-100">LunaVeta</p>
            <p class="mt-2 max-w-xs text-sm text-slate-600">{{ 'public.footer.tagline' | translate }}</p>
            <p class="mt-3 text-sm text-slate-600">
              {{ 'public.footer.contact' | translate }}:
              <a class="font-medium text-brand-700 hover:underline" href="mailto:supportlunaveta&#64;gmail.com">supportlunaveta&#64;gmail.com</a>
            </p>
          </div>
          <div>
            <p class="text-sm font-semibold text-brand-900">{{ 'public.footer.product' | translate }}</p>
            <ul class="mt-3 space-y-2 text-sm">
              <li><a class="text-brand-700 hover:underline" routerLink="/pricing">{{ 'public.nav.pricing' | translate }}</a></li>
              <li><a class="text-brand-700 hover:underline" routerLink="/login">{{ 'public.footer.login' | translate }}</a></li>
            </ul>
          </div>
          <nav [attr.aria-label]="'public.footer.legal' | translate">
            <p class="text-sm font-semibold text-brand-900">{{ 'public.footer.legal' | translate }}</p>
            <ul class="mt-3 space-y-2 text-sm">
              <li><a class="text-brand-700 hover:underline" routerLink="/terms">{{ 'public.footer.terms' | translate }}</a></li>
              <li><a class="text-brand-700 hover:underline" routerLink="/privacy">{{ 'public.footer.privacy' | translate }}</a></li>
              <li><a class="text-brand-700 hover:underline" routerLink="/refund-policy">{{ 'public.footer.refund' | translate }}</a></li>
            </ul>
          </nav>
        </div>
      </footer>
    </div>
  `
})
export class PublicShellComponent {}
