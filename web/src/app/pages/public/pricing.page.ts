import { Component, inject, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

const PAGE_TITLE = 'Pricing — LunaVeta';
const PAGE_DESCRIPTION = 'Explore LunaVeta plans and pricing for veterinary clinic management.';

@Component({
  standalone: true,
  template: `
    <div class="mx-auto min-h-screen max-w-5xl px-5 py-6 sm:px-8">
      <header class="flex flex-wrap items-center justify-between gap-4">
        <a href="/" class="flex items-center gap-3 font-display text-lg font-semibold text-brand-800">
          <img src="/assets/branding/logo.svg" width="36" height="36" alt="" class="h-9 w-9 rounded-xl" />
          LunaVeta
        </a>
        <nav class="flex flex-wrap gap-4 text-sm" aria-label="Account">
          <a href="/register" class="font-medium text-brand-700 hover:underline">Register</a>
          <a href="/login" class="font-medium text-brand-700 hover:underline">Sign in</a>
        </nav>
      </header>

      <main>
        <h1 class="mt-10 font-display text-4xl font-semibold tracking-tight text-brand-900">Simple pricing for your veterinary clinic</h1>
        <p class="mt-3 max-w-2xl text-slate-600">Choose the plan that fits your clinic and manage your veterinary practice from anywhere.</p>

        <div class="mt-8 grid gap-4 lg:grid-cols-3">
          <article class="card flex flex-col">
            <h2 class="font-display text-xl font-semibold text-brand-900">Basic</h2>
            <p class="mt-3 font-display text-4xl font-semibold text-brand-900">$19 <span class="text-base font-medium text-slate-500">/ month</span></p>
            <p class="mt-2 text-sm text-slate-500">For clinics that are starting to digitize their practice. Or $190 per year in the web app. A 14-day trial is available on the first Basic monthly subscription.</p>
            <ul class="mt-4 list-disc space-y-1 pl-5 text-sm text-slate-600">
              <li>Pet and owner management</li>
              <li>Medical records</li>
              <li>Vaccination records</li>
              <li>Appointment management</li>
              <li>1 branch, up to 2 veterinarians and 5 staff accounts</li>
              <li>Reports</li>
              <li>Messaging, up to 200 messages per month</li>
              <li>1 GB of file storage</li>
              <li>Web and mobile access</li>
            </ul>
            <a class="btn-primary mt-6" href="/register">Register</a>
          </article>

          <article class="card flex flex-col ring-2 ring-brand-700">
            <p class="mb-3 w-fit rounded-full bg-brand-600 px-2.5 py-0.5 text-xs font-semibold text-white">Most popular</p>
            <h2 class="font-display text-xl font-semibold text-brand-900">Professional</h2>
            <p class="mt-3 font-display text-4xl font-semibold text-brand-900">$39 <span class="text-base font-medium text-slate-500">/ month</span></p>
            <p class="mt-2 text-sm text-slate-500">For clinics that need more capacity for daily operations. Or $390 per year in the web app.</p>
            <ul class="mt-4 list-disc space-y-1 pl-5 text-sm text-slate-600">
              <li>Everything in Basic</li>
              <li>Up to 3 branches, 8 veterinarians and 20 staff accounts</li>
              <li>Laboratory results</li>
              <li>Messaging, up to 2,000 messages per month</li>
              <li>10 GB of file storage</li>
              <li>Web and mobile access</li>
            </ul>
            <a class="btn-primary mt-6" href="/register">Register</a>
          </article>

          <article class="card flex flex-col">
            <h2 class="font-display text-xl font-semibold text-brand-900">Premium</h2>
            <p class="mt-3 font-display text-4xl font-semibold text-brand-900">$69 <span class="text-base font-medium text-slate-500">/ month</span></p>
            <p class="mt-2 text-sm text-slate-500">For clinics that need the highest capacity. Or $690 per year in the web app.</p>
            <ul class="mt-4 list-disc space-y-1 pl-5 text-sm text-slate-600">
              <li>Everything in Professional</li>
              <li>Up to 15 branches, 40 veterinarians and 100 staff accounts</li>
              <li>Messaging, up to 20,000 messages per month</li>
              <li>100 GB of file storage</li>
              <li>Web and mobile access</li>
            </ul>
            <a class="btn-primary mt-6" href="/register">Register</a>
          </article>
        </div>

        <p class="mt-4 text-sm text-slate-500">Prices are in USD. Pet owners are clients of a clinic and do not buy these plans or use a staff seat. Subscribing and changing a plan is done in the web app after you sign in. This page does not start a payment.</p>
      </main>

      <footer class="mt-10 flex flex-wrap gap-x-5 gap-y-2 border-t border-slate-200 pt-5 text-sm">
        <a href="/terms" class="text-brand-700 hover:underline">Terms &amp; Conditions</a>
        <a href="/privacy" class="text-brand-700 hover:underline">Privacy Policy</a>
        <a href="/refund" class="text-brand-700 hover:underline">Refund Policy</a>
        <a href="mailto:supportlunaveta@gmail.com" class="text-brand-700 hover:underline">Contact</a>
        <a href="/login" class="text-brand-700 hover:underline">Login</a>
      </footer>
    </div>
  `
})
export class PricingPage implements OnInit {
  private title = inject(Title);
  private meta = inject(Meta);

  ngOnInit(): void {
    this.title.setTitle(PAGE_TITLE);
    this.meta.updateTag({ name: 'description', content: PAGE_DESCRIPTION });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:url', content: 'https://lunaveta.com/pricing' });
    this.meta.updateTag({ property: 'og:title', content: PAGE_TITLE });
    this.meta.updateTag({ property: 'og:description', content: PAGE_DESCRIPTION });
  }
}
