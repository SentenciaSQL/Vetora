import { AfterViewInit, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Chart } from 'chart.js/auto';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { StatusBadgePipe } from '../../../shared/ui/status-badge.pipe';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe, StatCardComponent, StatusBadgePipe, EmptyStateComponent],
  template: `
    @if (auth.isSuperAdmin()) {
      <div class="card">
        <h1 class="font-display text-2xl font-semibold">{{ 'admin.title' | translate }}</h1>
        <a routerLink="/admin" class="btn-primary mt-4 inline-flex">{{ 'nav.admin' | translate }}</a>
      </div>
    } @else if (ownerView()) {
      <h1 class="font-display text-2xl font-semibold">{{ 'dashboard.ownerTitle' | translate }}</h1>
      <p class="text-sm text-slate-500">{{ 'dashboard.ownerSubtitle' | translate }}</p>
      <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <app-stat-card [label]="'nav.pets' | translate" [value]="(data().pets || []).length" />
        <app-stat-card [label]="'dashboard.treatments' | translate" [value]="(data().activeTreatments || []).length" />
        <app-stat-card [label]="'dashboard.messages' | translate" [value]="data().unreadMessages || 0" />
        <app-stat-card [label]="'nav.notifications' | translate" [value]="data().unreadNotifications || 0" />
      </div>
      <div class="mt-6 grid gap-4 lg:grid-cols-2">
        <div class="card">
          <h2 class="font-medium">{{ 'dashboard.upcoming' | translate }}</h2>
          @if (!data().nextAppointment?.id) {
            <empty-state [title]="'dashboard.emptyAgenda' | translate" />
          } @else {
            <p class="mt-3 font-medium">{{ data().nextAppointment.pet }}</p>
            <p class="text-sm text-slate-500">{{ data().nextAppointment.startAt | date:'short' }} · {{ data().nextAppointment.status }}</p>
          }
        </div>
        <div class="card">
          <h2 class="font-medium">{{ 'dashboard.recentVaccines' | translate }}</h2>
          @if (!data().nextVaccine?.id) {
            <p class="mt-3 text-sm text-slate-500">{{ 'common.empty' | translate }}</p>
          } @else {
            <p class="mt-3">{{ data().nextVaccine.pet }} · {{ data().nextVaccine.vaccine }}</p>
            <span [class]="data().nextVaccine.status | statusBadge">{{ data().nextVaccine.status }}</span>
          }
        </div>
      </div>
      <div class="mt-6 flex flex-wrap gap-3">
        <a routerLink="/pets" class="btn-primary">{{ 'nav.pets' | translate }}</a>
        <a routerLink="/calendar" class="btn-secondary">{{ 'nav.calendar' | translate }}</a>
        <a routerLink="/messages" class="btn-secondary">{{ 'nav.messages' | translate }}</a>
      </div>
    } @else {
      <div class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 class="font-display text-2xl font-semibold">{{ 'dashboard.title' | translate }}</h1>
          <p class="text-sm text-slate-500">{{ 'dashboard.subtitle' | translate }}</p>
        </div>
        <a routerLink="/calendar" class="btn-primary">{{ 'calendar.title' | translate }}</a>
      </div>
      <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <app-stat-card [label]="'dashboard.appointmentsToday' | translate" [value]="data().appointmentsToday || 0" />
        <app-stat-card [label]="'dashboard.pending' | translate" [value]="data().pendingAppointments || 0" />
        <app-stat-card [label]="'dashboard.patients' | translate" [value]="data().patientsToday || 0" />
        <app-stat-card [label]="'dashboard.vaccines' | translate" [value]="data().upcomingVaccines || 0" />
        <app-stat-card [label]="'dashboard.newOwners' | translate" [value]="data().newOwners || 0" />
        <app-stat-card [label]="'dashboard.newPets' | translate" [value]="data().newPets || 0" />
        <app-stat-card [label]="'dashboard.treatments' | translate" [value]="data().activeTreatments || 0" />
        <app-stat-card [label]="'dashboard.messages' | translate" [value]="data().unreadMessages || 0" />
      </div>
      <div class="mt-6 grid gap-4 lg:grid-cols-2">
        <div class="card">
          <h2 class="mb-4 font-medium">{{ 'dashboard.appointmentsMonth' | translate }}</h2>
          <canvas #monthChart></canvas>
        </div>
        <div class="card">
          <h2 class="mb-4 font-medium">{{ 'dashboard.species' | translate }}</h2>
          <canvas #speciesChart></canvas>
        </div>
      </div>
      <div class="mt-6 grid gap-4 lg:grid-cols-2">
        <div class="card p-0">
          <div class="border-b border-slate-100 px-5 py-4 font-medium dark:border-white/10">{{ 'dashboard.agenda' | translate }}</div>
          @if (!(data().todayAgenda || []).length) {
            <empty-state [title]="'dashboard.emptyAgenda' | translate" />
          } @else {
            <ul>
              @for (item of data().todayAgenda; track item.id) {
                <li class="flex items-center justify-between border-t border-slate-50 px-5 py-3 text-sm dark:border-white/5">
                  <div>
                    <p class="font-medium">{{ item.pet }} · {{ item.owner }}</p>
                    <p class="text-slate-500">{{ item.startAt | date:'shortTime' }} · {{ item.veterinarian }}</p>
                  </div>
                  <span [class]="item.status | statusBadge">{{ item.status }}</span>
                </li>
              }
            </ul>
          }
        </div>
        <div class="card p-0">
          <div class="border-b border-slate-100 px-5 py-4 font-medium dark:border-white/10">{{ 'dashboard.recentVaccines' | translate }}</div>
          @for (v of data().upcomingVaccinations || []; track v.id) {
            <div class="flex justify-between border-t border-slate-50 px-5 py-3 text-sm dark:border-white/5">
              <span>{{ v.pet }} · {{ v.vaccine }}</span>
              <span [class]="v.status | statusBadge">{{ v.status }}</span>
            </div>
          }
        </div>
      </div>
    }
  `
})
export class DashboardPage implements OnInit, AfterViewInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  data = signal<any>({});
  monthChart = viewChild<ElementRef<HTMLCanvasElement>>('monthChart');
  speciesChart = viewChild<ElementRef<HTMLCanvasElement>>('speciesChart');

  ownerView() {
    return this.auth.hasRole('PET_OWNER') && !this.auth.isStaff();
  }

  ngOnInit(): void {
    if (!this.auth.isSuperAdmin()) {
      this.api.get('/dashboard').subscribe(d => {
        this.data.set(d);
        queueMicrotask(() => this.renderCharts());
      });
    }
  }

  ngAfterViewInit(): void {
    this.renderCharts();
  }

  private renderCharts(): void {
    const months = this.data().appointmentsByMonth as { label: string; value: number }[] | undefined;
    const species = this.data().species as { label: string; value: number }[] | undefined;
    const monthEl = this.monthChart()?.nativeElement;
    const speciesEl = this.speciesChart()?.nativeElement;
    if (monthEl && months?.length) {
      new Chart(monthEl, {
        type: 'line',
        data: {
          labels: months.map(m => m.label),
          datasets: [{ data: months.map(m => m.value), borderColor: '#0f766e', backgroundColor: 'rgba(15,118,110,.15)', fill: true, tension: .35 }]
        },
        options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
      });
    }
    if (speciesEl && species?.length) {
      new Chart(speciesEl, {
        type: 'doughnut',
        data: {
          labels: species.map(s => String(s.label)),
          datasets: [{ data: species.map(s => Number(s.value)), backgroundColor: ['#0f766e', '#99f6e4', '#115e59', '#5eead4'] }]
        },
        options: { plugins: { legend: { position: 'bottom' } } }
      });
    }
  }
}
