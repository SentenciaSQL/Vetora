import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { Owner, PageResponse, Pet, PublicClinic, TenantSummary } from '../../../core/models';

export const PET_SPECIES = ['DOG', 'CAT', 'BIRD', 'RABBIT', 'RODENT', 'REPTILE', 'HORSE', 'OTHER'] as const;

export function canRegisterPet(isStaff: boolean, isPetOwner: boolean, isSuperAdmin: boolean): boolean {
  return !isSuperAdmin && (isStaff || isPetOwner);
}

export function petCreateEndpoint(isStaff: boolean): string {
  return isStaff ? '/pets' : '/pets/mine';
}

export function ownerPetPayload(value: {
  tenantSlug: string | null;
  name: string;
  species: string;
  breed: string;
  sex: string;
}) {
  return {
    tenantSlug: value.tenantSlug || undefined,
    name: value.name,
    species: value.species,
    breed: value.breed,
    sex: value.sex
  };
}

export function clinicsFromMemberships(memberships?: TenantSummary[] | null): PublicClinic[] {
  return (memberships || [])
    .filter(m => !!m.slug)
    .map(m => ({
      slug: m.slug,
      name: m.name,
      commercialName: m.commercialName,
      logoUrl: m.logoUrl
    }));
}

export function mergeClinics(...lists: Array<PublicClinic[] | null | undefined>): PublicClinic[] {
  const bySlug = new Map<string, PublicClinic>();
  for (const list of lists) {
    for (const clinic of list || []) {
      if (clinic?.slug && !bySlug.has(clinic.slug)) {
        bySlug.set(clinic.slug, clinic);
      }
    }
  }
  return [...bySlug.values()].sort((a, b) =>
    (a.commercialName || a.name).localeCompare(b.commercialName || b.name, undefined, { sensitivity: 'base' })
  );
}

export function speciesLabelKey(code?: string | null): string {
  const normalized = (code || '').toUpperCase();
  return PET_SPECIES.includes(normalized as (typeof PET_SPECIES)[number])
    ? `pets.speciesOptions.${normalized}`
    : '';
}

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, EmptyStateComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'pets.title' | translate }}</h1>
        <p class="text-sm text-slate-500">{{ (auth.isStaff() ? 'pets.subtitle' : 'pets.ownerSubtitle') | translate }}</p>
      </div>
      @if (canCreate()) {
        <button class="btn-primary" (click)="openForm()">{{ (auth.isStaff() ? 'pets.new' : 'pets.register') | translate }}</button>
      }
    </div>
    <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      @if (rows().length === 0) {
        <div class="sm:col-span-2 xl:col-span-3">
          <empty-state
            [title]="(auth.isStaff() ? 'pets.empty' : 'pets.emptyOwner') | translate"
            [subtitle]="auth.isStaff() ? '' : ('pets.emptyHint' | translate)"
          />
          @if (canCreate() && !auth.isStaff()) {
            <div class="flex justify-center pb-6">
              <button class="btn-primary" (click)="openForm()">{{ 'pets.register' | translate }}</button>
            </div>
          }
        </div>
      }
      @for (p of rows(); track p.id) {
        <a [routerLink]="['/pets', p.id]" class="card group block hover:border-brand-200">
          <div class="flex items-center gap-3">
            <div class="grid h-14 w-14 place-items-center overflow-hidden rounded-2xl bg-brand-50 text-lg font-bold text-brand-800">
              @if (p.photoUrl) { <img [src]="p.photoUrl" [alt]="p.name" class="h-full w-full object-cover" /> }
              @else { {{ p.name[0] }} }
            </div>
            <div>
              <p class="font-semibold group-hover:text-brand-800">{{ p.name }}</p>
              <p class="text-sm text-slate-500">
                @if (speciesLabelKey(p.species); as speciesKey) { {{ speciesKey | translate }} }
                @else { {{ p.species }} }
                @if (p.breed) { · {{ p.breed }} }
              </p>
              <p class="text-xs text-slate-400">{{ p.ownerName }}</p>
            </div>
          </div>
        </a>
      }
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg">{{ (auth.isStaff() ? 'pets.new' : 'pets.register') | translate }}</h2>
          @if (auth.isStaff()) {
            <select class="input" formControlName="ownerId">
              <option value="">{{ 'pets.owner' | translate }}</option>
              @for (o of owners(); track o.id) { <option [value]="o.id">{{ o.fullName }}</option> }
            </select>
          } @else {
            <select class="input" formControlName="tenantSlug">
              <option value="">{{ 'pets.pickClinic' | translate }}</option>
              @for (c of clinics(); track c.slug) { <option [value]="c.slug">{{ c.commercialName || c.name }}</option> }
            </select>
            @if (clinics().length === 0) {
              <p class="text-sm text-rose-600">{{ 'pets.noClinics' | translate }}</p>
            }
          }
          <input class="input" formControlName="name" [placeholder]="'pets.name' | translate" />
          <div class="grid grid-cols-2 gap-3">
            <select class="input" formControlName="species">
              @for (code of speciesOptions; track code) {
                <option [value]="code">{{ 'pets.speciesOptions.' + code | translate }}</option>
              }
            </select>
            <input class="input" formControlName="breed" [placeholder]="'pets.breed' | translate" />
          </div>
          <select class="input" formControlName="sex">
            <option value="UNKNOWN">{{ 'pets.unknown' | translate }}</option>
            <option value="MALE">{{ 'pets.male' | translate }}</option>
            <option value="FEMALE">{{ 'pets.female' | translate }}</option>
          </select>
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }
  `
})
export class PetsPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);
  private fb = inject(FormBuilder);
  rows = signal<Pet[]>([]);
  owners = signal<Owner[]>([]);
  clinics = signal<PublicClinic[]>([]);
  speciesOptions = PET_SPECIES;
  speciesLabelKey = speciesLabelKey;
  open = false;
  form = this.fb.group({
    ownerId: [''],
    tenantSlug: [''],
    name: ['', Validators.required],
    species: ['DOG', Validators.required],
    breed: [''],
    sex: ['UNKNOWN']
  });

  ngOnInit() {
    if (!this.auth.isStaff()) {
      this.loadClinics();
    }
    this.route.queryParamMap.subscribe(params => {
      const ownerId = params.get('owner');
      this.load(ownerId);
      if (params.get('register') === '1' && this.canCreate()) {
        this.openForm();
      }
    });
  }

  canCreate() {
    return canRegisterPet(this.auth.isStaff(), this.auth.hasRole('PET_OWNER'), this.auth.isSuperAdmin());
  }

  openForm() {
    this.open = true;
    if (!this.auth.isStaff()) {
      this.loadClinics();
    }
  }

  load(ownerId: string | null) {
    if (this.auth.isStaff()) {
      this.api.get<PageResponse<Pet>>('/pets', { page: 0, size: 50, ownerId: ownerId || undefined }).subscribe(r => this.rows.set(r.content || []));
      this.api.get<PageResponse<Owner>>('/owners', { page: 0, size: 100 }).subscribe(r => this.owners.set(r.content || []));
    } else {
      this.api.get<Pet[]>('/pets/mine').subscribe(r => this.rows.set(r || []));
    }
  }

  loadClinics() {
    const membershipClinics = clinicsFromMemberships(this.auth.user()?.memberships);
    this.applyClinics(membershipClinics);
    this.api.get<PublicClinic[]>('/clinics').pipe(
      catchError(() => this.api.get<PublicClinic[]>('/public/clinics').pipe(catchError(() => of([]))))
    ).subscribe(list => this.applyClinics(mergeClinics(membershipClinics, list)));
  }

  save() {
    const value = this.form.getRawValue();
    if (this.auth.isStaff()) {
      this.api.post(petCreateEndpoint(true), { ...value, ownerId: Number(value.ownerId) }).subscribe({
        next: () => this.afterSave(),
        error: () => this.toast.show('common.error', true)
      });
      return;
    }
    const tenantSlug = value.tenantSlug || this.clinics()[0]?.slug || '';
    if (!tenantSlug) {
      this.toast.show('pets.noClinics', true);
      return;
    }
    this.api.post(petCreateEndpoint(false), ownerPetPayload({
      tenantSlug,
      name: value.name || '',
      species: value.species || 'DOG',
      breed: value.breed || '',
      sex: value.sex || 'UNKNOWN'
    })).subscribe({
      next: () => {
        this.auth.reloadProfile().subscribe();
        this.afterSave();
      },
      error: () => this.toast.show('common.error', true)
    });
  }

  private applyClinics(clinics: PublicClinic[]) {
    this.clinics.set(clinics);
    const preferred = this.form.value.tenantSlug
      || this.auth.user()?.tenantSlug
      || this.auth.user()?.memberships?.[0]?.slug
      || (clinics.length === 1 ? clinics[0].slug : '');
    if (preferred && clinics.some(c => c.slug === preferred)) {
      this.form.patchValue({ tenantSlug: preferred });
    }
  }

  private afterSave() {
    this.toast.show('common.saved');
    this.open = false;
    this.form.patchValue({ name: '', breed: '', sex: 'UNKNOWN', species: 'DOG' });
    this.load(this.route.snapshot.queryParamMap.get('owner'));
  }
}
