import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { PricingCardsComponent } from './pricing-cards.component';
import { PublicShellComponent } from './public-shell.component';

@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe, PublicShellComponent, PricingCardsComponent],
  template: `
    <app-public-shell>
      <section class="max-w-3xl">
        <p class="text-sm font-semibold uppercase tracking-[0.18em] text-brand-700">LunaVeta</p>
        <h1 class="mt-3 font-display text-4xl font-semibold tracking-tight text-brand-900 sm:text-5xl">{{ 'public.hero.title' | translate }}</h1>
        <p class="mt-4 text-lg text-slate-600">{{ 'public.hero.body' | translate }}</p>
        <div class="mt-8 flex flex-wrap gap-3">
          <a class="btn-primary" routerLink="/register-clinic">{{ 'public.cta.start' | translate }}</a>
          <a class="btn-secondary" href="#pricing">{{ 'public.cta.pricing' | translate }}</a>
        </div>
      </section>

      <section id="features" class="scroll-mt-24 mt-16">
        <h2 class="font-display text-3xl font-semibold tracking-tight text-brand-900">{{ 'public.features.title' | translate }}</h2>
        <div class="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          @for (feature of features; track feature.title) {
            <article class="card">
              <h3 class="font-display text-lg font-semibold text-brand-900">{{ feature.title | translate }}</h3>
              <p class="mt-2 text-slate-600">{{ feature.body | translate }}</p>
            </article>
          }
        </div>
      </section>

      <section id="pricing" class="scroll-mt-24 mt-16">
        <h2 class="font-display text-3xl font-semibold tracking-tight text-brand-900">{{ 'public.pricing.title' | translate }}</h2>
        <p class="mt-3 max-w-2xl text-slate-600">{{ 'public.pricing.subtitle' | translate }}</p>
        <div class="mt-8">
          <app-pricing-cards />
        </div>
      </section>

      <section id="about" class="scroll-mt-24 mt-16">
        <article class="card">
          <h2 class="font-display text-3xl font-semibold tracking-tight text-brand-900">{{ 'public.about.title' | translate }}</h2>
          <p class="mt-3 max-w-3xl text-slate-600">{{ 'public.about.body' | translate }}</p>
        </article>
      </section>

      <section class="mt-16 rounded-2xl bg-brand-800 px-6 py-10 text-white sm:px-10">
        <h2 class="font-display text-3xl font-semibold tracking-tight">{{ 'public.closing.title' | translate }}</h2>
        <p class="mt-3 max-w-2xl text-brand-50">{{ 'public.closing.body' | translate }}</p>
        <a class="mt-6 inline-flex items-center justify-center rounded-xl bg-white px-4 py-2.5 text-sm font-semibold text-brand-800 shadow-sm hover:bg-brand-50" routerLink="/register-clinic">{{ 'public.cta.start' | translate }}</a>
      </section>
    </app-public-shell>
  `
})
export class LandingPage implements OnInit {
  private title = inject(Title);
  private meta = inject(Meta);
  private i18n = inject(TranslateService);
  private destroyRef = inject(DestroyRef);

  readonly features = [
    { title: 'public.features.pets.title', body: 'public.features.pets.body' },
    { title: 'public.features.owners.title', body: 'public.features.owners.body' },
    { title: 'public.features.appointments.title', body: 'public.features.appointments.body' },
    { title: 'public.features.records.title', body: 'public.features.records.body' },
    { title: 'public.features.clinic.title', body: 'public.features.clinic.body' },
    { title: 'public.features.access.title', body: 'public.features.access.body' }
  ];

  ngOnInit(): void {
    this.applyMeta();
    this.i18n.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.applyMeta());
  }

  private applyMeta(): void {
    this.i18n.get(['public.meta.homeTitle', 'public.meta.homeDescription']).subscribe(t => {
      const pageTitle = t['public.meta.homeTitle'];
      const description = t['public.meta.homeDescription'];
      this.title.setTitle(pageTitle);
      this.meta.updateTag({ name: 'description', content: description });
      this.meta.updateTag({ property: 'og:type', content: 'website' });
      this.meta.updateTag({ property: 'og:url', content: 'https://lunaveta.com/' });
      this.meta.updateTag({ property: 'og:title', content: pageTitle });
      this.meta.updateTag({ property: 'og:description', content: description });
    });
  }
}
