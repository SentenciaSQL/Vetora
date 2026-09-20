import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { StatusBadgePipe } from '../../../shared/ui/status-badge.pipe';
import { Appointment, Pet, TimelineEvent } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, EmptyStateComponent, StatusBadgePipe],
  template: `
    @if (pet(); as p) {
      <div class="mb-4 text-sm text-slate-500"><a routerLink="/pets" class="hover:text-brand-700">{{ 'nav.pets' | translate }}</a> / {{ p.name }}</div>
      <div class="card">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div class="grid h-20 w-20 place-items-center overflow-hidden rounded-3xl bg-brand-50 text-2xl font-bold text-brand-800">
            @if (p.photoUrl) { <img [src]="p.photoUrl" [alt]="p.name" class="h-full w-full object-cover" /> }
            @else { {{ p.name[0] }} }
          </div>
          <div class="flex-1">
            <h1 class="font-display text-2xl font-semibold">{{ p.name }}</h1>
            <p class="text-slate-500">{{ p.breed }} · {{ p.age || p.species }}</p>
            <p class="text-sm text-slate-500">
              {{ 'pets.owner' | translate }}: {{ p.ownerName }}
              · {{ p.weightKg }} kg
              · {{ 'pets.vet' | translate }}: {{ p.veterinarianName }}
            </p>
          </div>
          @if (auth.hasPermission('MEDICAL_RECORD_WRITE')) {
            <a class="btn-primary" [routerLink]="['/consultations/new']" [queryParams]="{ petId: p.id }">{{ 'consultations.new' | translate }}</a>
          }
        </div>
        @if (p.allergies) {
          <div class="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-800 dark:bg-rose-500/10 dark:text-rose-200">⚠ {{ 'pets.allergies' | translate }}: {{ p.allergies }}</div>
        }
        @if (p.medicalConditions) {
          <div class="mt-2 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:bg-amber-500/10">⚠ {{ 'pets.conditions' | translate }}: {{ p.medicalConditions }}</div>
        }
      </div>
      <div class="mt-6 flex flex-wrap gap-2">
        @for (t of tabs; track t.id) {
          <button type="button" class="rounded-full px-4 py-1.5 text-sm focus-visible:ring-2 focus-visible:ring-brand-500"
                  [class.bg-brand-700]="tab===t.id" [class.text-white]="tab===t.id"
                  [class.bg-slate-100]="tab!==t.id" (click)="select(t.id)">{{ t.label | translate }}</button>
        }
      </div>

      @if (tab === 'summary') {
        <div class="mt-4 grid gap-4 md:grid-cols-2">
          <div class="card space-y-1 text-sm">
            <p><span class="text-slate-400">{{ 'pets.species' | translate }}:</span> {{ p.species }}</p>
            <p><span class="text-slate-400">{{ 'pets.breed' | translate }}:</span> {{ p.breed }}</p>
            <p><span class="text-slate-400">{{ 'pets.weight' | translate }}:</span> {{ p.weightKg }} kg</p>
            <p><span class="text-slate-400">Microchip:</span> {{ p.microchip || '—' }}</p>
            <p><span class="text-slate-400">{{ 'common.status' | translate }}:</span> {{ p.status }}</p>
          </div>
          <div class="card">
            <p class="text-sm font-medium">{{ 'pets.tabs.timeline' | translate }}</p>
            @for (e of timeline().slice(0, 4); track e.entityId + e.type) {
              <p class="mt-2 text-sm text-slate-500">{{ e.title }} · {{ e.at | date:'short' }}</p>
            }
          </div>
        </div>
      }

      @if (tab === 'timeline') {
        <div class="mt-4 space-y-3">
          @if (timeline().length === 0) { <empty-state [title]="'pets.empty' | translate" /> }
          @for (e of timeline(); track e.entityId + e.type) {
            <div class="card flex items-start gap-3">
              <div class="mt-1 h-2.5 w-2.5 rounded-full bg-brand-600"></div>
              <div>
                <p class="text-xs uppercase tracking-wide text-slate-400">{{ e.type }} · {{ e.at | date:'medium' }} · {{ e.veterinarianName }}</p>
                <p class="font-medium">{{ e.title }}</p>
                <p class="text-sm text-slate-500">{{ e.summary }}</p>
              </div>
            </div>
          }
        </div>
      }

      @if (tab === 'consultations') {
        <div class="mt-4 space-y-2">
          @if (consultations().length === 0) { <empty-state [title]="'common.empty' | translate" /> }
          @for (c of consultations(); track c.id) {
            <div class="card">
              <p class="font-medium">{{ c.consultedAt | date:'medium' }} · {{ c.veterinarianName }}</p>
              <p class="text-sm text-slate-500">{{ c.reason }}</p>
              <p class="text-sm">{{ c.diagnosis }}</p>
            </div>
          }
        </div>
      }

      @if (tab === 'vaccines') {
        <div class="mt-4 space-y-2">
          @if (canWrite) {
            <form class="card grid gap-2 sm:grid-cols-4" (ngSubmit)="saveVaccine()">
              <input class="input" [(ngModel)]="vaccine.name" name="vname" [placeholder]="'pets.tabs.vaccines' | translate" required />
              <input class="input" [(ngModel)]="vaccine.brand" name="vbrand" placeholder="Marca" />
              <input class="input" type="date" [(ngModel)]="vaccine.appliedAt" name="vdate" required />
              <button class="btn-primary">{{ 'common.create' | translate }}</button>
            </form>
          }
          @for (v of vaccines(); track v.id) {
            <div class="card flex items-center justify-between">
              <div>
                <p class="font-medium">{{ v.vaccineName }}</p>
                <p class="text-sm text-slate-500">{{ v.appliedAt | date }} · {{ v.brand }}</p>
              </div>
              <span [class]="v.status | statusBadge">{{ v.status }}</span>
            </div>
          }
        </div>
      }

      @if (tab === 'treatments') {
        <div class="mt-4 space-y-2">
          @if (canWrite) {
            <form class="card grid gap-2 sm:grid-cols-3" (ngSubmit)="saveTreatment()">
              <input class="input" [(ngModel)]="treatment.name" name="tname" required [placeholder]="'pets.tabs.treatments' | translate" />
              <input class="input" type="date" [(ngModel)]="treatment.startDate" name="tdate" required />
              <button class="btn-primary">{{ 'common.create' | translate }}</button>
            </form>
          }
          @for (t of treatments(); track t.id) {
            <div class="card"><p class="font-medium">{{ t.name }}</p><p class="text-sm text-slate-500">{{ t.status }} · {{ t.startDate }}</p></div>
          }
        </div>
      }

      @if (tab === 'prescriptions') {
        <div class="mt-4 space-y-2">
          @if (auth.hasPermission('PRESCRIPTION_CREATE')) {
            <form class="card grid gap-2 sm:grid-cols-3" (ngSubmit)="saveRx()">
              <input class="input" [(ngModel)]="rx.medicationName" name="rxmed" placeholder="Medicamento" required />
              <input class="input" [(ngModel)]="rx.dose" name="rxdose" placeholder="Dosis" />
              <button class="btn-primary">{{ 'common.create' | translate }}</button>
            </form>
          }
          @for (item of prescriptions(); track item.id) {
            <div class="card flex items-center justify-between">
              <p>{{ item.issuedAt | date }} · {{ item.notes }}</p>
      <a class="btn-secondary text-xs" (click)="downloadPdf(item.id)">PDF</a>
            </div>
          }
        </div>
      }

            @if (tab === 'labs') {
        <div class="mt-4 space-y-2">
          @if (canWrite) {
            <form class="card grid gap-2 sm:grid-cols-3" (ngSubmit)="saveLab()">
              <input class="input" [(ngModel)]="lab.name" name="lname" required [placeholder]="'pets.tabs.labs' | translate" />
              <input class="input" [(ngModel)]="lab.resultSummary" name="lres" placeholder="Resultado" />
              <button class="btn-primary">{{ 'common.create' | translate }}</button>
            </form>
          }
          @for (l of labs(); track l.id) {
            <div class="card">
              <p class="font-medium">{{ l.name }}</p>
              <p class="text-sm text-slate-500">{{ l.labName }} · {{ l.collectedAt | date }} · {{ l.status }}</p>
              <p class="text-sm">{{ l.resultSummary }}</p>
            </div>
          }
        </div>
      }

      @if (tab === 'procedures') {
        <div class="mt-4 space-y-2">
          @if (canWrite) {
            <form class="card grid gap-2 sm:grid-cols-3" (ngSubmit)="saveProcedure()">
              <input class="input" [(ngModel)]="procedure.name" name="pname" required [placeholder]="'pets.tabs.procedures' | translate" />
              <input class="input" [(ngModel)]="procedure.notes" name="pnotes" [placeholder]="'consultations.plan' | translate" />
              <button class="btn-primary">{{ 'common.create' | translate }}</button>
            </form>
          }
          @for (item of procedures(); track item.id) {
            <div class="card">
              <p class="font-medium">{{ item.name }}</p>
              <p class="text-sm text-slate-500">{{ item.performedAt | date:'short' }} · {{ item.veterinarianName }}</p>
              <p class="text-sm">{{ item.notes }}</p>
            </div>
          }
        </div>
      }

      @if (tab === 'surgeries') {
        <div class="mt-4 space-y-2">
          @if (canWrite) {
            <form class="card grid gap-2 sm:grid-cols-3" (ngSubmit)="saveSurgery()">
              <input class="input" [(ngModel)]="surgery.name" name="sname" required [placeholder]="'pets.tabs.surgeries' | translate" />
              <input class="input" [(ngModel)]="surgery.anesthesia" name="sanes" placeholder="Anestesia" />
              <button class="btn-primary">{{ 'common.create' | translate }}</button>
            </form>
          }
          @for (item of surgeries(); track item.id) {
            <div class="card">
              <p class="font-medium">{{ item.name }}</p>
              <p class="text-sm text-slate-500">{{ item.performedAt | date:'short' }} · {{ item.anesthesia }}</p>
              <p class="text-sm">{{ item.notes }}</p>
            </div>
          }
        </div>
      }

      @if (tab === 'documents') {
        <div class="mt-4 space-y-2">
          @if (auth.hasPermission('DOCUMENT_WRITE')) {
            <label class="btn-secondary inline-flex cursor-pointer">
              {{ 'settings.upload' | translate }}
              <input type="file" class="hidden" (change)="uploadDoc($event)" />
            </label>
          }
          @for (d of documents(); track d.id) {
            <div class="card flex items-center justify-between">
              <div>
                <p class="font-medium">{{ d.title }}</p>
                <p class="text-xs text-slate-500">{{ d.category }} · {{ d.createdAt | date:'short' }}</p>
              </div>
              @if (d.url) { <a class="btn-secondary text-xs" [href]="d.url" target="_blank">{{ 'common.view' | translate }}</a> }
            </div>
          }
        </div>
      }

      @if (tab === 'appointments') {
        <div class="mt-4 space-y-2">
          @for (a of appointments(); track a.id) {
            <div class="card flex items-center justify-between">
              <p>{{ a.startAt | date:'short' }} · {{ a.serviceName }} · {{ a.veterinarianName }}</p>
              <span [class]="a.status | statusBadge">{{ a.status }}</span>
            </div>
          }
        </div>
      }

      @if (tab === 'weight') {
        <div class="mt-4 space-y-2">
          @if (auth.hasPermission('PET_UPDATE')) {
            <form class="card flex gap-2" (ngSubmit)="saveWeight()">
              <input class="input" type="number" step="0.1" [(ngModel)]="weightKg" name="wkg" placeholder="kg" required />
              <button class="btn-primary">{{ 'common.save' | translate }}</button>
            </form>
          }
          @for (w of weights(); track w.id) {
            <div class="card flex justify-between text-sm"><span>{{ w.recordedAt | date:'short' }}</span><span class="font-medium">{{ w.weightKg }} kg</span></div>
          }
        </div>
      }
    }
  `
})
export class PetProfilePage implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  auth = inject(AuthService);
  pet = signal<Pet | null>(null);
  timeline = signal<TimelineEvent[]>([]);
  consultations = signal<any[]>([]);
  vaccines = signal<any[]>([]);
  treatments = signal<any[]>([]);
  prescriptions = signal<any[]>([]);
  labs = signal<any[]>([]);
  procedures = signal<any[]>([]);
  surgeries = signal<any[]>([]);
  documents = signal<any[]>([]);
  appointments = signal<Appointment[]>([]);
  weights = signal<any[]>([]);
  tab = 'summary';
  vaccine = { name: '', brand: '', appliedAt: '' };
  treatment = { name: '', startDate: '' };
  rx = { medicationName: '', dose: '' };
  lab = { name: '', resultSummary: '' };
  procedure = { name: '', notes: '' };
  surgery = { name: '', anesthesia: '' };
  weightKg: number | null = null;
  tabs = [
    { id: 'summary', label: 'pets.tabs.summary' },
    { id: 'timeline', label: 'pets.tabs.timeline' },
    { id: 'consultations', label: 'pets.tabs.consultations' },
    { id: 'vaccines', label: 'pets.tabs.vaccines' },
    { id: 'treatments', label: 'pets.tabs.treatments' },
    { id: 'prescriptions', label: 'pets.tabs.prescriptions' },
    { id: 'labs', label: 'pets.tabs.labs' },
    { id: 'procedures', label: 'pets.tabs.procedures' },
    { id: 'surgeries', label: 'pets.tabs.surgeries' },
    { id: 'documents', label: 'pets.tabs.documents' },
    { id: 'appointments', label: 'pets.tabs.appointments' },
    { id: 'weight', label: 'pets.tabs.weight' }
  ];

  get canWrite() {
    return this.auth.hasPermission('MEDICAL_RECORD_WRITE');
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    this.api.get<Pet>(`/pets/${id}`).subscribe(p => this.pet.set(p));
    this.select('summary');
  }

  select(tab: string) {
    this.tab = tab;
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    if (tab === 'summary' || tab === 'timeline') this.api.get<TimelineEvent[]>(`/pets/${id}/timeline`).subscribe(t => this.timeline.set(t));
    if (tab === 'consultations') this.api.get<any[]>(`/pets/${id}/consultations`).subscribe(v => this.consultations.set(v));
    if (tab === 'vaccines') this.api.get<any[]>(`/pets/${id}/vaccinations`).subscribe(v => this.vaccines.set(v));
    if (tab === 'treatments') this.api.get<any[]>(`/pets/${id}/treatments`).subscribe(v => this.treatments.set(v));
    if (tab === 'prescriptions') this.api.get<any[]>(`/pets/${id}/prescriptions`).subscribe(v => this.prescriptions.set(v));
    if (tab === 'labs') this.api.get<any[]>(`/pets/${id}/labs`).subscribe(v => this.labs.set(v));
    if (tab === 'procedures') this.api.get<any[]>(`/pets/${id}/procedures`).subscribe(v => this.procedures.set(v));
    if (tab === 'surgeries') this.api.get<any[]>(`/pets/${id}/surgeries`).subscribe(v => this.surgeries.set(v));
    if (tab === 'documents') this.api.get<any[]>(`/pets/${id}/documents`).subscribe(v => this.documents.set(v));
    if (tab === 'appointments') this.api.get<Appointment[]>(`/appointments/pet/${id}`).subscribe(v => this.appointments.set(v));
    if (tab === 'weight') this.api.get<any[]>(`/pets/${id}/weights`).subscribe(v => this.weights.set(v));
  }

  downloadPdf(id: number) {
    this.api.blob(`/prescriptions/${id}/pdf`).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: () => this.toast.show('common.error', true)
    });
  }

  private petId() {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  saveVaccine() {
    this.api.post('/vaccinations', {
      petId: this.petId(),
      vaccineName: this.vaccine.name,
      brand: this.vaccine.brand,
      appliedAt: this.vaccine.appliedAt
    }).subscribe({
      next: () => { this.toast.show('common.saved'); this.vaccine = { name: '', brand: '', appliedAt: '' }; this.select('vaccines'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveTreatment() {
    this.api.post('/treatments', {
      petId: this.petId(),
      name: this.treatment.name,
      startDate: this.treatment.startDate,
      status: 'ACTIVE'
    }).subscribe({
      next: () => { this.toast.show('common.saved'); this.treatment = { name: '', startDate: '' }; this.select('treatments'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveRx() {
    this.api.post('/prescriptions', {
      petId: this.petId(),
      notes: this.rx.medicationName,
      items: [{ medicationName: this.rx.medicationName, dose: this.rx.dose }]
    }).subscribe({
      next: () => { this.toast.show('common.saved'); this.rx = { medicationName: '', dose: '' }; this.select('prescriptions'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveLab() {
    this.api.post('/labs', { petId: this.petId(), name: this.lab.name, resultSummary: this.lab.resultSummary }).subscribe({
      next: () => { this.toast.show('common.saved'); this.lab = { name: '', resultSummary: '' }; this.select('labs'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveProcedure() {
    this.api.post('/procedures', { petId: this.petId(), name: this.procedure.name, notes: this.procedure.notes }).subscribe({
      next: () => { this.toast.show('common.saved'); this.procedure = { name: '', notes: '' }; this.select('procedures'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveSurgery() {
    this.api.post('/surgeries', { petId: this.petId(), name: this.surgery.name, anesthesia: this.surgery.anesthesia }).subscribe({
      next: () => { this.toast.show('common.saved'); this.surgery = { name: '', anesthesia: '' }; this.select('surgeries'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  uploadDoc(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.upload(`/pets/${this.petId()}/documents`, file, { category: 'OTHER' }).subscribe({
      next: () => { this.toast.show('common.saved'); this.select('documents'); },
      error: () => this.toast.show('common.error', true)
    });
  }

  saveWeight() {
    if (this.weightKg == null) return;
    this.api.post(`/pets/${this.petId()}/weights`, { weightKg: this.weightKg }).subscribe({
      next: () => { this.toast.show('common.saved'); this.weightKg = null; this.select('weight'); this.api.get<Pet>(`/pets/${this.petId()}`).subscribe(p => this.pet.set(p)); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
