import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { PageResponse, Pet } from '../../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'consultations.new' | translate }}</h1>
    <p class="text-sm text-slate-500">{{ 'consultations.subtitle' | translate }}</p>
    <form class="mt-6 grid gap-6 lg:grid-cols-3" [formGroup]="form" (ngSubmit)="save()">
      <div class="card space-y-4 lg:col-span-2">
        <label class="text-sm font-medium">{{ 'pets.name' | translate }}
          <select class="input mt-1" formControlName="petId">
            @for (p of pets; track p.id) { <option [value]="p.id">{{ p.name }}</option> }
          </select>
        </label>
        <textarea class="input min-h-20" formControlName="reason" [placeholder]="'consultations.reason' | translate"></textarea>
        <textarea class="input min-h-20" formControlName="symptoms" [placeholder]="'consultations.symptoms' | translate"></textarea>
        <textarea class="input min-h-20" formControlName="anamnesis" [placeholder]="'consultations.anamnesis' | translate"></textarea>
        <textarea class="input min-h-20" formControlName="physicalExam" [placeholder]="'consultations.exam' | translate"></textarea>
        <input class="input" formControlName="diagnosis" [placeholder]="'consultations.diagnosis' | translate" />
        <textarea class="input min-h-20" formControlName="differentialDiagnosis" [placeholder]="'consultations.differential' | translate"></textarea>
        <textarea class="input min-h-20" formControlName="treatmentPlan" [placeholder]="'consultations.plan' | translate"></textarea>
        <textarea class="input min-h-20" formControlName="recommendations" [placeholder]="'consultations.recommendations' | translate"></textarea>
        <textarea class="input min-h-20" formControlName="internalNotes" [placeholder]="'consultations.internal' | translate"></textarea>
        <input class="input" type="datetime-local" formControlName="nextControlAt" [placeholder]="'consultations.followUp' | translate" />
      </div>
      <div class="card space-y-3">
        <h2 class="font-medium">{{ 'consultations.vitals' | translate }}</h2>
        <input class="input" type="number" step="0.1" formControlName="weightKg" [placeholder]="'consultations.kg' | translate" />
        <input class="input" type="number" step="0.1" formControlName="temperatureC" [placeholder]="'consultations.temp' | translate" />
        <input class="input" type="number" formControlName="heartRate" [placeholder]="'consultations.hr' | translate" />
        <input class="input" type="number" formControlName="respiratoryRate" [placeholder]="'consultations.rr' | translate" />
        <button class="btn-primary w-full">{{ 'common.save' | translate }}</button>
      </div>
    </form>
  `
})
export class ConsultationPage implements OnInit {
  private api = inject(ApiService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  pets: Pet[] = [];
  form = this.fb.group({
    petId: ['', Validators.required],
    appointmentId: [null as number | null],
    reason: [''],
    symptoms: [''],
    anamnesis: [''],
    physicalExam: [''],
    diagnosis: [''],
    differentialDiagnosis: [''],
    treatmentPlan: [''],
    recommendations: [''],
    internalNotes: [''],
    nextControlAt: [''],
    weightKg: [null as number | null],
    temperatureC: [null as number | null],
    heartRate: [null as number | null],
    respiratoryRate: [null as number | null]
  });

  ngOnInit() {
    this.api.get<PageResponse<Pet>>('/pets', { size: 100 }).subscribe(r => {
      this.pets = r.content || [];
      const petId = this.route.snapshot.queryParamMap.get('petId');
      const appointmentId = this.route.snapshot.queryParamMap.get('appointmentId');
      if (petId) this.form.patchValue({ petId });
      if (appointmentId) this.form.patchValue({ appointmentId: Number(appointmentId) });
    });
  }

  save() {
    const value = this.form.getRawValue();
    const payload: Record<string, unknown> = { ...value, petId: Number(value.petId) };
    if (value.nextControlAt) {
      payload['nextControlAt'] = new Date(value.nextControlAt).toISOString();
    } else {
      delete payload['nextControlAt'];
    }
    this.api.post('/consultations', payload).subscribe({
      next: () => { this.toast.show('common.saved'); void this.router.navigate(['/pets', value.petId]); },
      error: () => this.toast.show('common.error', true)
    });
  }
}
