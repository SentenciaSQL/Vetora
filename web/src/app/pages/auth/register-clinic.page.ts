import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SignupService } from '../../core/services/signup.service';
import { AuthService } from '../../core/services/auth.service';
import { PaddleService } from '../../core/services/paddle.service';
import { ApiService } from '../../core/services/api.service';
import { BrandingService } from '../../core/services/branding.service';
import { ImageUploadComponent } from '../../shared/ui/image-upload.component';
import { Branding, PublicPlan, SignupConfig } from '../../core/models';
import { BillingCycle } from '../../core/services/billing.service';
import {
  cycleAvailable,
  displayedPrice,
  isPopularPlan,
  monthlyEquivalentAmount,
  savingsPercentAmount,
  showsFreeTrial
} from '../clinic/billing/billing.page';
import { apiErrorMessage } from '../../core/http-error';

function matchPassword(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { mismatch: true } : null;
}

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, ImageUploadComponent],
  template: `
    <div class="mx-auto min-h-screen max-w-5xl px-4 py-10 sm:px-6">
      <div class="mb-8 flex items-center justify-between gap-4">
        <div>
          <p class="text-sm uppercase tracking-wide text-brand-700">{{ 'signup.kicker' | translate }}</p>
          <h1 class="font-display text-3xl font-semibold">{{ 'signup.title' | translate }}</h1>
        </div>
        <a routerLink="/login"
           class="cursor-pointer text-sm font-medium text-brand-700 underline decoration-brand-500/70 underline-offset-2 hover:text-brand-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
          {{ 'auth.hasAccount' | translate }}
        </a>
      </div>

      <ol class="mb-8 grid grid-cols-4 gap-2 text-center text-xs font-medium sm:text-sm">
        @for (label of steps; track label; let i = $index) {
          <li class="rounded-full px-2 py-2"
              [ngClass]="step() === i + 1
                ? 'bg-brand-600 text-white'
                : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200'">
            {{ i + 1 }}. {{ label | translate }}
          </li>
        }
      </ol>

      @if (error()) {
        <p class="mb-4 text-sm text-rose-600">{{ error() }}</p>
      }

      @if (step() === 1) {
        <form class="card mx-auto max-w-xl space-y-3" [formGroup]="account" (ngSubmit)="submitAccount()">
          <div class="grid gap-3 sm:grid-cols-2">
            <input class="input" formControlName="firstName" [placeholder]="'auth.firstName' | translate" />
            <input class="input" formControlName="lastName" [placeholder]="'auth.lastName' | translate" />
          </div>
          <input class="input" type="email" formControlName="email" [placeholder]="'auth.email' | translate" />
          <input class="input" formControlName="phone" [placeholder]="'signup.phoneOptional' | translate" />
          <input class="input" type="password" formControlName="password" [placeholder]="'auth.password' | translate" />
          <input class="input" type="password" formControlName="confirmPassword" [placeholder]="'signup.confirmPassword' | translate" />
          @if (account.hasError('mismatch') && account.touched) {
            <p class="text-sm text-rose-600">{{ 'signup.passwordMismatch' | translate }}</p>
          }
          <label class="flex items-start gap-2 text-sm">
            <input type="checkbox" formControlName="termsAccepted" class="mt-1" />
            <span>{{ 'signup.terms' | translate }}</span>
          </label>
          <button type="submit" class="btn-primary w-full" [disabled]="account.invalid || busy()">{{ 'common.continue' | translate }}</button>
        </form>
      }

      @if (step() === 2) {
        <form class="card mx-auto max-w-xl space-y-3" [formGroup]="clinic" (ngSubmit)="go(3)">
          <input class="input" formControlName="name" [placeholder]="'signup.clinicName' | translate" (blur)="suggestSlug()" />
          <div>
            <input class="input" formControlName="slug" [placeholder]="'admin.slug' | translate" />
            @if (slugTaken()) {
              <p class="mt-1 text-sm text-rose-600">{{ 'signup.slugTaken' | translate }}</p>
            }
          </div>
          <select class="input" formControlName="country">
            @for (country of countries; track country.code) {
              <option [value]="country.code">{{ country.label }}</option>
            }
          </select>
          <select class="input" formControlName="timezone">
            @for (zone of timezones; track zone) {
              <option [value]="zone">{{ zone }}</option>
            }
          </select>
          <input class="input" formControlName="phone" [placeholder]="'signup.phoneOptional' | translate" />
          <input class="input" formControlName="address" [placeholder]="'signup.addressOptional' | translate" />
          <app-image-upload [label]="'signup.logo' | translate" [hint]="'signup.logoHint' | translate" [src]="logoPreview()" (selected)="onLogo($event)" (cleared)="clearLogo()" />
          <div class="flex justify-between">
            <button type="button" class="btn-secondary" (click)="go(1)">{{ 'common.back' | translate }}</button>
            <button type="submit" class="btn-primary" [disabled]="clinic.invalid || slugTaken()">{{ 'common.continue' | translate }}</button>
          </div>
        </form>
      }

      @if (step() === 3) {
        <section>
          <div class="mb-4 inline-flex rounded-full border border-slate-200 p-1 text-sm dark:border-slate-700">
            <button type="button" class="rounded-full px-4 py-1" [class.bg-brand-600]="cycle() === 'MONTHLY'"
                    [class.text-white]="cycle() === 'MONTHLY'" (click)="cycle.set('MONTHLY')">{{ 'billing.monthly' | translate }}</button>
            <button type="button" class="rounded-full px-4 py-1" [class.bg-brand-600]="cycle() === 'ANNUAL'"
                    [class.text-white]="cycle() === 'ANNUAL'" (click)="cycle.set('ANNUAL')">{{ 'billing.annual' | translate }}</button>
          </div>
          <div class="grid gap-4 md:grid-cols-3">
            @for (plan of config()?.plans || []; track plan.id) {
              <article class="card cursor-pointer space-y-3" [class.ring-2]="selectedPlan()?.id === plan.id"
                       [class.ring-brand-500]="selectedPlan()?.id === plan.id || isPopularPlan(plan.code)"
                       (click)="selectedPlan.set(plan)">
                @if (isPopularPlan(plan.code)) {
                  <p class="text-xs font-medium text-brand-700">{{ 'billing.mostPopular' | translate }}</p>
                }
                <h3 class="font-display text-xl font-semibold">{{ plan.name }}</h3>
                <p class="text-3xl font-semibold">{{ displayedPrice(plan, cycle()) | number:'1.2-2' }} {{ plan.currency }}</p>
                <p class="text-xs text-slate-400">{{ cycle() === 'ANNUAL' ? ('billing.perYear' | translate) : ('billing.perMonth' | translate) }}</p>
                @if (cycle() === 'ANNUAL' && monthlyEquivalentAmount(plan); as equivalent) {
                  <p class="text-sm text-slate-500">{{ equivalent | number:'1.2-2' }} {{ plan.currency }}/mes</p>
                  <p class="text-xs font-medium text-emerald-700">{{ 'billing.twoMonthsFree' | translate }}
                    @if (savingsPercentAmount(plan); as save) { · {{ 'billing.savePercent' | translate:{ percent: save } }} }
                  </p>
                }
                @if (showsFreeTrial(plan, cycle())) {
                  <p class="text-xs font-medium text-brand-700">{{ 'billing.basicMonthlyTrial' | translate }}</p>
                }
                <ul class="text-sm text-slate-600">
                  <li>{{ 'admin.users' | translate }}: {{ plan.limits.maxUsers }}</li>
                  <li>{{ 'nav.team' | translate }}: {{ plan.limits.maxVeterinarians }}</li>
                  <li>{{ 'nav.branches' | translate }}: {{ plan.limits.maxBranches }}</li>
                </ul>
              </article>
            }
          </div>
          <div class="mt-6 flex justify-between">
            <button type="button" class="btn-secondary" (click)="go(2)">{{ 'common.back' | translate }}</button>
            <button type="button" class="btn-primary" [disabled]="!canContinuePlan()" (click)="go(4)">{{ 'common.continue' | translate }}</button>
          </div>
        </section>
      }

      @if (step() === 4 && selectedPlan(); as plan) {
        <section class="card mx-auto max-w-xl space-y-3">
          <h2 class="font-display text-xl font-semibold">{{ 'signup.summary' | translate }}</h2>
          <p>{{ 'signup.owner' | translate }}: {{ ownerName() }}</p>
          <p>{{ 'signup.clinic' | translate }}: {{ clinic.value.name }}</p>
          <p>{{ 'admin.plan' | translate }}: {{ plan.name }} · {{ cycle() }}</p>
          <p>{{ 'signup.price' | translate }}: {{ displayedPrice(plan, cycle()) | number:'1.2-2' }} {{ plan.currency }}</p>
          @if (showsFreeTrial(plan, cycle())) {
            <p>{{ 'billing.basicMonthlyTrial' | translate }}</p>
            <p>{{ 'billing.nextCharge' | translate }}: {{ firstCharge() | date:'mediumDate' }}</p>
          } @else {
            <p>{{ 'billing.nextCharge' | translate }}: {{ 'billing.chargeNow' | translate }}</p>
          }
          <ul class="text-sm text-slate-600">
            <li>{{ 'admin.users' | translate }}: {{ plan.limits.maxUsers }}</li>
            <li>{{ 'nav.team' | translate }}: {{ plan.limits.maxVeterinarians }}</li>
            <li>{{ 'nav.branches' | translate }}: {{ plan.limits.maxBranches }}</li>
          </ul>
          <div class="flex justify-between">
            <button type="button" class="btn-secondary" (click)="go(3)">{{ 'common.back' | translate }}</button>
            <button type="button" class="btn-primary" [disabled]="busy()" (click)="pay()">{{ 'signup.pay' | translate }}</button>
          </div>
        </section>
      }

      <p class="mt-8 text-center">
        <a routerLink="/login"
           class="cursor-pointer text-sm font-medium text-brand-700 underline decoration-brand-500/70 underline-offset-2 hover:text-brand-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
          {{ 'auth.hasAccount' | translate }}
        </a>
      </p>
    </div>
  `
})
export class RegisterClinicPage implements OnInit {
  private fb = inject(FormBuilder);
  private signup = inject(SignupService);
  private auth = inject(AuthService);
  private paddle = inject(PaddleService);
  private api = inject(ApiService);
  private branding = inject(BrandingService);
  private router = inject(Router);

  steps = ['signup.stepAccount', 'signup.stepClinic', 'signup.stepPlan', 'signup.stepPay'];
  step = signal(1);
  busy = signal(false);
  error = signal('');
  slugTaken = signal(false);
  config = signal<SignupConfig | null>(null);
  selectedPlan = signal<PublicPlan | null>(null);
  cycle = signal<BillingCycle>('MONTHLY');
  logo?: File;
  logoPreview = signal<string | null>(null);

  readonly displayedPrice = displayedPrice;
  readonly monthlyEquivalentAmount = monthlyEquivalentAmount;
  readonly savingsPercentAmount = savingsPercentAmount;
  readonly isPopularPlan = isPopularPlan;
  readonly showsFreeTrial = showsFreeTrial;

  countries = [
    { code: 'DO', label: 'República Dominicana' },
    { code: 'US', label: 'Estados Unidos' },
    { code: 'MX', label: 'México' },
    { code: 'ES', label: 'España' },
    { code: 'CO', label: 'Colombia' },
    { code: 'AR', label: 'Argentina' },
    { code: 'CL', label: 'Chile' },
    { code: 'PE', label: 'Perú' }
  ];
  timezones = ['America/Santo_Domingo', 'America/New_York', 'America/Mexico_City', 'America/Bogota', 'Europe/Madrid'];

  account = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
    termsAccepted: [false, Validators.requiredTrue]
  }, { validators: matchPassword });

  clinic = this.fb.group({
    name: ['', Validators.required],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    country: ['DO', Validators.required],
    timezone: ['America/Santo_Domingo', Validators.required],
    phone: [''],
    address: ['']
  });

  ngOnInit(): void {
    this.signup.config().subscribe(config => {
      this.config.set(config);
      this.clinic.patchValue({
        country: config.defaultCountry || 'DO',
        timezone: config.defaultTimezone || 'America/Santo_Domingo'
      });
      void this.paddle.ensure({
        environment: config.environment,
        clientToken: config.clientToken,
        gracePeriodDays: config.gracePeriodDays,
        trialDays: config.trialDays,
        plans: []
      });
    });
    const user = this.auth.user();
    if (user && this.auth.isAuthenticated) {
      this.account.patchValue({ firstName: user.firstName, lastName: user.lastName, email: user.email, phone: user.phone || '' });
      this.signup.status().subscribe({
        next: status => this.applyStatus(status),
        error: () => this.applyStoredUser()
      });
    }
  }

  private applyStatus(status: { signupStatus?: string; emailVerified: boolean; tenantId?: number | null; onboardingComplete?: boolean; accessGranted?: boolean; checkoutPending?: boolean; user?: { firstName?: string; lastName?: string; email?: string; phone?: string } }): void {
    if (status.user) {
      this.account.patchValue({
        firstName: status.user.firstName || '',
        lastName: status.user.lastName || '',
        email: status.user.email || '',
        phone: status.user.phone || ''
      });
    }
    if (this.auth.onboardingComplete() || status.onboardingComplete || status.accessGranted) {
      void this.router.navigateByUrl(this.auth.isSuspended() ? '/billing' : '/dashboard');
      return;
    }
    if (!status.emailVerified) {
      void this.router.navigateByUrl('/verify-email');
      return;
    }
    if (status.checkoutPending) {
      void this.router.navigateByUrl('/signup/processing');
      return;
    }
    this.step.set(status.tenantId ? 3 : 2);
  }

  private applyStoredUser(): void {
    const user = this.auth.user();
    if (!user || !this.auth.isTenantOwner()) {
      return;
    }
    if (this.auth.onboardingComplete() || this.auth.accessGranted()) {
      void this.router.navigateByUrl(this.auth.homePath());
      return;
    }
    if (!user.emailVerified) {
      void this.router.navigateByUrl('/verify-email');
      return;
    }
    if (this.auth.checkoutPending()) {
      void this.router.navigateByUrl('/signup/processing');
      return;
    }
    this.step.set(user.tenantId ? 3 : 2);
  }

  ownerName(): string {
    const value = this.account.getRawValue();
    return `${value.firstName} ${value.lastName}`.trim();
  }

  firstCharge(): Date {
    const plan = this.selectedPlan();
    if (!showsFreeTrial(plan, this.cycle())) {
      return new Date();
    }
    const days = plan?.monthlyTrialDays || 14;
    return new Date(Date.now() + days * 24 * 60 * 60 * 1000);
  }

  canContinuePlan(): boolean {
    const plan = this.selectedPlan();
    return !!plan && cycleAvailable(plan, this.cycle());
  }

  go(next: number): void {
    this.error.set('');
    this.step.set(next);
  }

  onLogo(file: File): void {
    this.logo = file;
    this.logoPreview.set(URL.createObjectURL(file));
  }

  clearLogo(): void {
    this.logo = undefined;
    this.logoPreview.set(null);
  }

  suggestSlug(): void {
    const name = this.clinic.value.name;
    if (!name) {
      return;
    }
    this.signup.suggestSlug(name).subscribe(result => this.clinic.patchValue({ slug: result.slug }));
  }

  submitAccount(): void {
    if (this.account.invalid || this.busy()) {
      this.account.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.signup.register(this.account.getRawValue()).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl('/verify-email');
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo crear la cuenta'));
      }
    });
  }

  pay(): void {
    const plan = this.selectedPlan();
    if (!plan || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const clinic = this.clinic.getRawValue();
    const finish = () => this.signup.checkout(plan.id, this.cycle()).subscribe({
      next: session => {
        void this.paddle.openCheckout(session, () => void this.router.navigateByUrl('/signup/processing'), `${window.location.origin}/signup/processing`);
        this.busy.set(false);
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo iniciar el pago'));
      }
    });
    const afterClinic = () => {
      if (!this.logo) {
        finish();
        return;
      }
      this.signup.refreshOwnerSession().subscribe({
        next: () => this.uploadLogo(finish),
        error: () => this.uploadLogo(finish)
      });
    };
    if (this.auth.user()?.tenantId) {
      afterClinic();
      return;
    }
    this.signup.completeClinic({
      name: clinic.name,
      slug: clinic.slug,
      country: clinic.country,
      timezone: clinic.timezone,
      phone: clinic.phone,
      address: clinic.address,
      planId: plan.id,
      billingCycle: this.cycle()
    }).subscribe({
      next: () => afterClinic(),
      error: err => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo registrar la veterinaria'));
      }
    });
  }

  private uploadLogo(finish: () => void): void {
    if (!this.logo) {
      finish();
      return;
    }
    this.api.upload<Branding>('/settings/branding/logo', this.logo, { variant: 'light' }).subscribe({
      next: brand => {
        this.branding.branding.set(brand);
        finish();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo guardar el logo de la veterinaria'));
      }
    });
  }
}
