import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { PublicShellComponent } from './public-shell.component';

export type LegalKind = 'terms' | 'privacy' | 'refund';

interface LegalSection {
  title: string;
  paragraphs: string[];
}

const DOCUMENTS: Record<LegalKind, { title: string; description: string; intro: string; sections: LegalSection[] }> = {
  terms: {
    title: 'public.legal.terms.title',
    description: 'public.legal.terms.description',
    intro: 'public.legal.terms.intro',
    sections: [
      { title: 'public.legal.terms.serviceTitle', paragraphs: ['public.legal.terms.serviceBody'] },
      { title: 'public.legal.terms.audienceTitle', paragraphs: ['public.legal.terms.audienceBody'] },
      { title: 'public.legal.terms.accountsTitle', paragraphs: ['public.legal.terms.accountsBody'] },
      { title: 'public.legal.terms.plansTitle', paragraphs: ['public.legal.terms.plansBody', 'public.legal.terms.plansBody2'] },
      { title: 'public.legal.terms.cancelTitle', paragraphs: ['public.legal.terms.cancelBody'] },
      { title: 'public.legal.terms.useTitle', paragraphs: ['public.legal.terms.useBody'] }
    ]
  },
  privacy: {
    title: 'public.legal.privacy.title',
    description: 'public.legal.privacy.description',
    intro: 'public.legal.privacy.intro',
    sections: [
      { title: 'public.legal.privacy.infoTitle', paragraphs: ['public.legal.privacy.infoBody', 'public.legal.privacy.infoBody2'] },
      { title: 'public.legal.privacy.useTitle', paragraphs: ['public.legal.privacy.useBody'] },
      { title: 'public.legal.privacy.paymentsTitle', paragraphs: ['public.legal.privacy.paymentsBody'] },
      { title: 'public.legal.privacy.retentionTitle', paragraphs: ['public.legal.privacy.retentionBody'] }
    ]
  },
  refund: {
    title: 'public.legal.refund.title',
    description: 'public.legal.refund.description',
    intro: 'public.legal.refund.intro',
    sections: [
      { title: 'public.legal.refund.cancelTitle', paragraphs: ['public.legal.refund.cancelBody'] },
      { title: 'public.legal.refund.trialTitle', paragraphs: ['public.legal.refund.trialBody'] },
      { title: 'public.legal.refund.changesTitle', paragraphs: ['public.legal.refund.changesBody'] },
      { title: 'public.legal.refund.requestTitle', paragraphs: ['public.legal.refund.requestBody', 'public.legal.refund.requestBody2', 'public.legal.refund.requestBody3'] },
      { title: 'public.legal.refund.rightsTitle', paragraphs: ['public.legal.refund.rightsBody'] }
    ]
  }
};

@Component({
  standalone: true,
  imports: [RouterLink, TranslatePipe, PublicShellComponent],
  template: `
    <app-public-shell>
      <article class="mx-auto max-w-3xl">
        <h1 class="font-display text-4xl font-semibold tracking-tight text-brand-900">{{ doc().title | translate }}</h1>
        <p class="mt-4 text-slate-600">{{ doc().intro | translate }}</p>

        <div class="mt-6 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-100">
          <p class="font-semibold">{{ 'public.legal.pendingTitle' | translate }}</p>
          <ul class="mt-2 list-disc space-y-1 pl-5">
            <li>{{ 'public.legal.pendingName' | translate }}</li>
            <li>{{ 'public.legal.pendingAddress' | translate }}</li>
            <li>{{ 'public.legal.pendingNumber' | translate }}</li>
            <li>{{ 'public.legal.pendingLaw' | translate }}</li>
          </ul>
        </div>

        @for (section of doc().sections; track section.title) {
          <h2 class="mt-8 font-display text-xl font-semibold text-brand-900">{{ section.title | translate }}</h2>
          @for (paragraph of section.paragraphs; track paragraph) {
            <p class="mt-3 text-slate-600">{{ paragraph | translate }}</p>
          }
        }

        <h2 class="mt-8 font-display text-xl font-semibold text-brand-900">{{ 'public.legal.deletionTitle' | translate }}</h2>
        <p class="mt-3 text-slate-600">{{ 'public.legal.deletionBody' | translate }}</p>
        <p class="mt-2">
          <a class="font-medium text-brand-700 hover:underline" routerLink="/eliminar-cuenta">{{ 'public.legal.deletionLink' | translate }}</a>
        </p>

        <h2 class="mt-8 font-display text-xl font-semibold text-brand-900">{{ 'public.legal.contactTitle' | translate }}</h2>
        <p class="mt-3 text-slate-600">{{ 'public.legal.contactBody' | translate }}</p>
        <p class="mt-2">
          <a class="font-medium text-brand-700 hover:underline" href="mailto:supportlunaveta&#64;gmail.com">supportlunaveta&#64;gmail.com</a>
        </p>

        <p class="mt-8 text-sm text-slate-500">{{ 'public.legal.updated' | translate }}</p>
        <p class="mt-4 flex flex-wrap gap-x-4 gap-y-2 text-sm">
          <a class="text-brand-700 hover:underline" routerLink="/terms">{{ 'public.footer.terms' | translate }}</a>
          <a class="text-brand-700 hover:underline" routerLink="/privacy">{{ 'public.footer.privacy' | translate }}</a>
          <a class="text-brand-700 hover:underline" routerLink="/refund-policy">{{ 'public.footer.refund' | translate }}</a>
        </p>
      </article>
    </app-public-shell>
  `
})
export class PublicLegalPage implements OnInit {
  private route = inject(ActivatedRoute);
  private title = inject(Title);
  private meta = inject(Meta);
  private i18n = inject(TranslateService);
  private destroyRef = inject(DestroyRef);
  private kind = signal<LegalKind>('terms');
  doc = computed(() => DOCUMENTS[this.kind()]);

  ngOnInit(): void {
    this.route.data.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      const kind = data['kind'];
      this.kind.set(kind === 'privacy' || kind === 'refund' ? kind : 'terms');
      this.applyMeta();
    });
    this.i18n.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.applyMeta());
  }

  private applyMeta(): void {
    const document = this.doc();
    this.i18n.get([document.title, document.description]).subscribe(t => {
      const pageTitle = `${t[document.title]} | LunaVeta`;
      const description = t[document.description];
      this.title.setTitle(pageTitle);
      this.meta.updateTag({ name: 'description', content: description });
    });
  }
}
