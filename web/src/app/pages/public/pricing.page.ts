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
      <section id="pricing" class="scroll-mt-24">
        <h1 class="font-display text-4xl font-semibold tracking-tight text-brand-900">{{ 'public.pricing.title' | translate }}</h1>
        <p class="mt-3 max-w-2xl text-slate-600">{{ 'public.pricing.subtitle' | translate }}</p>
        <div class="mt-8">
          <app-pricing-cards />
        </div>
      </section>

      <section class="mt-16">
        <article class="card">
          <h2 class="font-display text-2xl font-semibold text-brand-900">{{ 'public.about.title' | translate }}</h2>
          <p class="mt-3 text-slate-600">{{ 'public.about.body' | translate }}</p>
        </article>
      </section>

      <section class="mt-10">
        <h2 class="font-display text-2xl font-semibold text-brand-900">{{ 'public.faq.title' | translate }}</h2>
        <div class="mt-4 space-y-4">
          @for (item of faq; track item.question) {
            <article class="card">
              <h3 class="font-display text-lg font-semibold text-brand-900">{{ item.question | translate }}</h3>
              <p class="mt-2 text-slate-600">{{ item.answer | translate }}</p>
              @if (item.link) {
                <p class="mt-2">
                  <a class="font-medium text-brand-700 hover:underline" routerLink="/refund-policy">{{ 'public.faq.billing.link' | translate }}</a>
                </p>
              }
            </article>
          }
        </div>
      </section>
    </app-public-shell>
  `
})
export class PricingPage implements OnInit {
  private title = inject(Title);
  private meta = inject(Meta);
  private i18n = inject(TranslateService);
  private destroyRef = inject(DestroyRef);

  readonly faq = [
    { question: 'public.faq.what.q', answer: 'public.faq.what.a' },
    { question: 'public.faq.mobile.q', answer: 'public.faq.mobile.a' },
    { question: 'public.faq.pets.q', answer: 'public.faq.pets.a' },
    { question: 'public.faq.languages.q', answer: 'public.faq.languages.a' },
    { question: 'public.faq.billing.q', answer: 'public.faq.billing.a', link: true }
  ];

  ngOnInit(): void {
    this.applyMeta();
    this.i18n.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.applyMeta());
  }

  private applyMeta(): void {
    this.i18n.get(['public.meta.pricingTitle', 'public.meta.pricingDescription']).subscribe(t => {
      const pageTitle = t['public.meta.pricingTitle'];
      const description = t['public.meta.pricingDescription'];
      this.title.setTitle(pageTitle);
      this.meta.updateTag({ name: 'description', content: description });
      this.meta.updateTag({ property: 'og:type', content: 'website' });
      this.meta.updateTag({ property: 'og:url', content: 'https://lunaveta.com/pricing' });
      this.meta.updateTag({ property: 'og:title', content: pageTitle });
      this.meta.updateTag({ property: 'og:description', content: description });
    });
  }
}
