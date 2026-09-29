import { Component, inject, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

const PAGE_TITLE = 'LunaVeta — Veterinary Clinic Management Software';
const PAGE_DESCRIPTION = 'LunaVeta helps veterinary clinics manage pets, owners, medical records, vaccinations, appointments, communications, and daily operations.';

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
          <a href="/pricing" class="font-medium text-brand-700 hover:underline">Pricing</a>
          <a href="/login" class="font-medium text-brand-700 hover:underline">Sign in</a>
        </nav>
      </header>

      <main>
        <section class="mt-12 max-w-2xl">
          <h1 class="font-display text-4xl font-semibold tracking-tight text-brand-900 sm:text-5xl">LunaVeta</h1>
          <p class="mt-3 text-xl font-semibold text-brand-800">Veterinary clinic management made simple.</p>
          <p class="mt-3 text-slate-600">LunaVeta is a cloud-based SaaS platform for veterinary clinics that helps manage pets, owners, medical records, vaccinations, appointments, communications, and everyday clinic operations.</p>
          <div class="mt-6 flex flex-wrap gap-3">
            <a class="btn-primary" href="/pricing">View Pricing</a>
            <a class="btn-secondary" href="/login">Sign In</a>
          </div>
        </section>

        <section class="mt-10 grid gap-4 md:grid-cols-2">
          <article class="card md:col-span-2">
            <h2 class="font-display text-lg font-semibold text-brand-900">What is LunaVeta?</h2>
            <p class="mt-2 text-slate-600">LunaVeta is a cloud-based SaaS platform for veterinary clinics that helps manage pets, owners, medical records, vaccinations, appointments, communications, and everyday clinic operations.</p>
          </article>

          <article class="card md:col-span-2">
            <h2 class="font-display text-lg font-semibold text-brand-900">Key features</h2>
            <ul class="mt-2 list-disc space-y-1 pl-5 text-slate-600">
              <li>Veterinary clinic management</li>
              <li>Pet and owner management</li>
              <li>Medical records</li>
              <li>Vaccinations</li>
              <li>Appointments</li>
              <li>Notifications and communications</li>
              <li>Web and mobile access</li>
              <li>Spanish and English support</li>
            </ul>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Veterinary clinic management</h2>
            <p class="mt-2 text-slate-600">Run branches, services, business hours, and the clinic team from one place. Each plan sets how many branches, veterinarians, and staff accounts the clinic can use. Reports are included on every plan.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Pet and owner management</h2>
            <p class="mt-2 text-slate-600">Register pets and the people who own them. Pet owners are clients of the clinic. They do not count toward the staff accounts included in a plan.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Medical records</h2>
            <p class="mt-2 text-slate-600">Keep each pet’s clinical history on the pet profile, including consultations recorded by the clinic.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Vaccinations</h2>
            <p class="mt-2 text-slate-600">Record the vaccine name, brand, lot, applied date, next dose, and notes. A vaccination already saved can be corrected or removed.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Appointments</h2>
            <p class="mt-2 text-slate-600">Schedule an appointment with a veterinarian, a service, and a reason. Staff can update the reason, change the date, or cancel.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Notifications and communications</h2>
            <p class="mt-2 text-slate-600">LunaVeta stores in-app notifications, and can send push notifications on mobile, when an appointment changes, when a vaccination is recorded, and when a vaccine reminder is due. Clinics and pet owners can also exchange messages. The number of messages included each month depends on the plan. Messaging is available on every plan.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Web and mobile access</h2>
            <p class="mt-2 text-slate-600">Staff use the web app and the mobile app. Pet owners can follow their pets in the mobile app. Buying, changing, or canceling a subscription is done in the web app.</p>
          </article>

          <article class="card">
            <h2 class="font-display text-lg font-semibold text-brand-900">Spanish and English support</h2>
            <p class="mt-2 text-slate-600">The web app and the mobile app can be used in Spanish or English.</p>
          </article>
        </section>
      </main>

      <footer class="mt-10 flex flex-wrap gap-x-5 gap-y-2 border-t border-slate-200 pt-5 text-sm">
        <a href="/pricing" class="text-brand-700 hover:underline">Pricing</a>
        <a href="/terms" class="text-brand-700 hover:underline">Terms &amp; Conditions</a>
        <a href="/privacy" class="text-brand-700 hover:underline">Privacy Policy</a>
        <a href="/refund" class="text-brand-700 hover:underline">Refund Policy</a>
        <a href="mailto:supportlunaveta@gmail.com" class="text-brand-700 hover:underline">Contact</a>
        <a href="/login" class="text-brand-700 hover:underline">Login</a>
      </footer>
    </div>
  `
})
export class LandingPage implements OnInit {
  private title = inject(Title);
  private meta = inject(Meta);

  ngOnInit(): void {
    this.title.setTitle(PAGE_TITLE);
    this.meta.updateTag({ name: 'description', content: PAGE_DESCRIPTION });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:url', content: 'https://lunaveta.com/' });
    this.meta.updateTag({ property: 'og:title', content: PAGE_TITLE });
    this.meta.updateTag({ property: 'og:description', content: PAGE_DESCRIPTION });
  }
}
