import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { Owner, PageResponse, Pet } from '../../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, EmptyStateComponent],
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'pets.title' | translate }}</h1>
        <p class="text-sm text-slate-500">{{ 'pets.subtitle' | translate }}</p>
      </div>
      @if (auth.isStaff()) {
        <button class="btn-primary" (click)="open=true">{{ 'pets.new' | translate }}</button>
      }
    </div>
    <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      @if (rows().length === 0) {
        <div class="sm:col-span-2 xl:col-span-3"><empty-state [title]="'pets.empty' | translate" /></div>
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
              <p class="text-sm text-slate-500">{{ p.species }} · {{ p.breed }}</p>
              <p class="text-xs text-slate-400">{{ p.ownerName }}</p>
            </div>
          </div>
        </a>
      }
    </div>
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card w-full max-w-lg space-y-3" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg">{{ 'pets.new' | translate }}</h2>
          <select class="input" formControlName="ownerId">
            <option value="">{{ 'pets.owner' | translate }}</option>
            @for (o of owners(); track o.id) { <option [value]="o.id">{{ o.fullName }}</option> }
          </select>
          <input class="input" formControlName="name" [placeholder]="'pets.name' | translate" />
          <div class="grid grid-cols-2 gap-3">
            <input class="input" formControlName="species" [placeholder]="'pets.species' | translate" />
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
  open = false;
  form = this.fb.group({
    ownerId: ['', Validators.required],
    name: ['', Validators.required],
    species: ['DOG'],
    breed: [''],
    sex: ['UNKNOWN']
  });

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      const ownerId = params.get('owner');
      this.load(ownerId);
    });
  }

  load(ownerId: string | null) {
    if (this.auth.isStaff()) {
      this.api.get<PageResponse<Pet>>('/pets', { page: 0, size: 50, ownerId: ownerId || undefined }).subscribe(r => this.rows.set(r.content || []));
      this.api.get<PageResponse<Owner>>('/owners', { page: 0, size: 100 }).subscribe(r => this.owners.set(r.content || []));
    } else {
      this.api.get<Pet[]>('/pets/mine').subscribe(r => this.rows.set(r || []));
    }
  }

  save() {
    const value = this.form.getRawValue();
    this.api.post('/pets', { ...value, ownerId: Number(value.ownerId) }).subscribe({
      next: () => { this.toast.show('common.saved'); this.open = false; this.load(this.route.snapshot.queryParamMap.get('owner')); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
