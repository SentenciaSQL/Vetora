import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

interface PublicPlanCard {
  id: string;
  name: string;
  price: number;
  popular: boolean;
  audienceKey: string;
  noteKey: string;
  features: string[];
}

@Component({
  selector: 'app-pricing-cards',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div class="grid gap-4 lg:grid-cols-3">
      @for (plan of plans; track plan.id) {
        <article class="card flex h-full flex-col" [class.ring-2]="plan.popular" [class.ring-brand-700]="plan.popular">
          @if (plan.popular) {
            <p class="mb-3 w-fit rounded-full bg-brand-600 px-2.5 py-0.5 text-xs font-semibold text-white">{{ 'public.pricing.popular' | translate }}</p>
          }
          <h3 class="font-display text-xl font-semibold text-brand-900">{{ plan.name }}</h3>
          <p class="mt-3 font-display text-4xl font-semibold text-brand-900">
            \${{ plan.price }}
            <span class="text-base font-medium text-slate-500">{{ 'public.pricing.perMonth' | translate }}</span>
          </p>
          <p class="mt-1 text-xs font-semibold text-slate-500">{{ 'public.pricing.usdMonthly' | translate }}</p>
          <p class="mt-3 text-sm text-slate-600">{{ plan.audienceKey | translate }}</p>
          <p class="mt-2 text-sm text-slate-500">{{ plan.noteKey | translate }}</p>
          <ul class="mt-4 flex-1 list-disc space-y-1 pl-5 text-sm text-slate-600">
            @for (feature of plan.features; track feature) {
              <li>{{ feature | translate }}</li>
            }
          </ul>
          <a class="btn-primary mt-6" routerLink="/register-clinic">{{ 'public.cta.start' | translate }}</a>
        </article>
      }
    </div>
    <p class="mt-4 text-sm text-slate-500">{{ 'public.pricing.disclaimer' | translate }}</p>
  `
})
export class PricingCardsComponent {
  readonly plans: PublicPlanCard[] = [
    {
      id: 'basic',
      name: 'Basic',
      price: 19,
      popular: false,
      audienceKey: 'public.pricing.basic.audience',
      noteKey: 'public.pricing.basic.note',
      features: [
        'public.pricing.basic.f1',
        'public.pricing.basic.f2',
        'public.pricing.basic.f3',
        'public.pricing.basic.f4',
        'public.pricing.basic.f5',
        'public.pricing.basic.f6',
        'public.pricing.basic.f7',
        'public.pricing.basic.f8',
        'public.pricing.basic.f9'
      ]
    },
    {
      id: 'professional',
      name: 'Professional',
      price: 39,
      popular: true,
      audienceKey: 'public.pricing.professional.audience',
      noteKey: 'public.pricing.professional.note',
      features: [
        'public.pricing.professional.f1',
        'public.pricing.professional.f2',
        'public.pricing.professional.f3',
        'public.pricing.professional.f4',
        'public.pricing.professional.f5',
        'public.pricing.professional.f6'
      ]
    },
    {
      id: 'premium',
      name: 'Premium',
      price: 69,
      popular: false,
      audienceKey: 'public.pricing.premium.audience',
      noteKey: 'public.pricing.premium.note',
      features: [
        'public.pricing.premium.f1',
        'public.pricing.premium.f2',
        'public.pricing.premium.f3',
        'public.pricing.premium.f4',
        'public.pricing.premium.f5'
      ]
    }
  ];
}
