import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';
import { BrandMarkComponent } from '../../shared/ui/brand-mark.component';
import { LanguageSelectorComponent } from '../../shared/ui/language-selector.component';
import { ThemeSelectorComponent } from '../../shared/ui/theme-selector.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, BrandMarkComponent, LanguageSelectorComponent, ThemeSelectorComponent],
  template: `
    <div class="grid min-h-screen lg:grid-cols-2">
      <section class="relative hidden flex-col justify-between bg-brand-800 p-10 text-white lg:flex">
        <app-brand-mark />
        <div>
          <p class="text-sm uppercase tracking-[0.2em] text-brand-100">{{ 'app.name' | translate }}</p>
          <h1 class="mt-4 max-w-md font-display text-4xl font-semibold leading-tight">{{ 'app.tagline' | translate }}</h1>
          <p class="mt-4 max-w-md text-brand-50/80">{{ 'auth.subtitle' | translate }}</p>
        </div>
        <p class="text-sm text-brand-100/70">© {{ year }} {{ 'app.name' | translate }}</p>
      </section>
      <section class="flex flex-col justify-center px-6 py-12 sm:px-12">
        <div class="mb-8 flex items-center justify-between">
          <div class="lg:hidden"><app-brand-mark /></div>
          <div class="ml-auto flex gap-2"><app-language-selector /><app-theme-selector /></div>
        </div>
        <div class="mx-auto w-full max-w-md">
          <h2 class="font-display text-2xl font-semibold">{{ 'auth.welcome' | translate }}</h2>
          <p class="mt-1 text-sm text-slate-500">{{ branding.displayName() }}</p>
          <form class="mt-8 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <div>
              <label class="mb-1 block text-sm font-medium" for="email">{{ 'auth.email' | translate }}</label>
              <input id="email" class="input" type="email" formControlName="email" autocomplete="username" />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium" for="password">{{ 'auth.password' | translate }}</label>
              <input id="password" class="input" type="password" formControlName="password" autocomplete="current-password" />
            </div>
            @if (error()) {
              <p class="text-sm text-rose-600">{{ error() | translate }}</p>
            }
            <button class="btn-primary w-full" [disabled]="form.invalid || loading">{{ 'auth.submit' | translate }}</button>
          </form>
          <div class="mt-4 flex justify-between text-sm">
            <a routerLink="/forgot" class="text-brand-700 hover:underline">{{ 'auth.forgot' | translate }}</a>
            <a routerLink="/register" class="text-brand-700 hover:underline">{{ 'auth.register' | translate }}</a>
          </div>
        </div>
      </section>
    </div>
  `
})
export class LoginPage implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  branding = inject(BrandingService);
  error = signal('');
  loading = false;
  year = new Date().getFullYear();
  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (slug) {
      this.branding.loadPublic(slug).subscribe(b => this.branding.branding.set(b));
    } else {
      this.branding.branding.set({ name: 'Vetora', commercialName: 'Vetora', logoUrl: '/assets/branding/logo.png' });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading = true;
    this.error.set('');
    const slug = this.route.snapshot.paramMap.get('slug') || undefined;
    this.auth.login(this.form.value.email!, this.form.value.password!, slug).subscribe({
      next: () => {
        this.branding.loadForSession();
        void this.router.navigateByUrl(this.auth.homePath());
      },
      error: () => {
        this.loading = false;
        this.error.set('auth.invalid');
      }
    });
  }
}
