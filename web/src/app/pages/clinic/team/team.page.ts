import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { StaffInvite, TeamMember } from '../../../core/models';
import { TEAM_ASSIGNABLE_ROLES, VETERINARY_SPECIALTIES, roleLabel, specialtyLabel } from '../../../core/team-labels';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { BillingService } from '../../../core/services/billing.service';
import { SessionInactivityService } from '../../../core/services/session-inactivity.service';
import { SignupService } from '../../../core/services/signup.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, RouterLink, DatePipe],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'team.title' | translate }}</h1>
        <p class="mt-1 text-sm text-slate-500">{{ 'team.subtitle' | translate }}</p>
      </div>
      @if (canManage()) {
        <button class="btn-primary" type="button" [disabled]="atLimit()" (click)="startCreate()">+ {{ 'team.addMember' | translate }}</button>
      }
    </div>

    <div class="mt-6 grid gap-3 md:grid-cols-3">
      <div>
        <label class="block text-sm font-medium" for="team-search">{{ 'team.search' | translate }}</label>
        <input id="team-search" class="input mt-1" [value]="query()" (input)="query.set($any($event.target).value)" [placeholder]="'team.searchPlaceholder' | translate" />
      </div>
      <div>
        <label class="block text-sm font-medium" for="team-role">{{ 'team.role' | translate }}</label>
        <select id="team-role" class="input mt-1" [value]="roleFilter()" (change)="roleFilter.set($any($event.target).value)">
          <option value="">{{ 'team.allRoles' | translate }}</option>
          <option value="TENANT_OWNER">{{ labelRole('TENANT_OWNER') }}</option>
          @for (role of assignableRoles; track role) {
            <option [value]="role">{{ labelRole(role) }}</option>
          }
        </select>
      </div>
      <div>
        <label class="block text-sm font-medium" for="team-status">{{ 'team.status' | translate }}</label>
        <select id="team-status" class="input mt-1" [value]="statusFilter()" (change)="statusFilter.set($any($event.target).value)">
          <option value="">{{ 'team.allStatuses' | translate }}</option>
          <option value="ACTIVE">{{ 'team.active' | translate }}</option>
          <option value="INACTIVE">{{ 'team.inactive' | translate }}</option>
        </select>
      </div>
    </div>

    @if (usage(); as plan) {
      <div class="card mt-6">
        <div class="flex items-end justify-between gap-3">
          <p class="text-sm font-medium">{{ 'team.usage' | translate }}</p>
          <p class="text-sm text-slate-500">{{ plan.current }} {{ 'team.usageOf' | translate }} {{ plan.limit }} {{ 'team.usageUsed' | translate }}</p>
        </div>
        <div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-white/10">
          <div class="h-full rounded-full" [class.bg-rose-500]="atLimit()" [class.bg-brand-700]="!atLimit()" [style.width.%]="percent()"></div>
        </div>
        <p class="mt-2 text-xs text-slate-500">{{ percent() }}%</p>
        @if (atLimit()) {
          <p class="mt-3 text-sm text-rose-700">{{ 'team.limitReached' | translate }}</p>
          <a class="btn-primary mt-3" routerLink="/billing">{{ 'team.upgradePlan' | translate }}</a>
        }
      </div>
    }

    <h2 class="mt-8 font-display text-lg font-semibold">{{ 'team.members' | translate }}</h2>
    <div class="mt-3 space-y-3">
      @for (member of filtered(); track member.membershipId) {
        <article class="card flex items-start gap-4">
          <div class="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-brand-50 text-sm font-semibold text-brand-800 dark:bg-brand-500/15 dark:text-brand-100">
            {{ initials(member) }}
          </div>
          <div class="min-w-0 flex-1">
            <p class="font-semibold">{{ member.fullName }}</p>
            <p class="text-sm text-slate-500">{{ memberLine(member) }}</p>
            <p class="text-sm text-slate-500">{{ member.email }}</p>
            <div class="mt-3 flex flex-wrap items-center justify-between gap-3">
              <p class="text-sm text-slate-600">{{ member.branchName || ('team.noBranch' | translate) }}</p>
              <p class="inline-flex items-center gap-2 text-sm">
                <span class="h-2 w-2 rounded-full" [class.bg-emerald-500]="member.status === 'ACTIVE'" [class.bg-slate-400]="member.status !== 'ACTIVE'"></span>
                {{ (member.status === 'ACTIVE' ? 'team.active' : 'team.inactive') | translate }}
              </p>
            </div>
          </div>
          @if (canManage()) {
            <div class="relative z-30">
              <button class="btn-secondary px-3 py-1.5 text-xs" type="button" (click)="toggleMenu(member.membershipId)" [attr.aria-label]="'team.actions' | translate">...</button>
              @if (menuId() === member.membershipId) {
                <div class="absolute right-0 z-30 mt-1 w-52 rounded-xl border border-slate-200 bg-white py-1 text-left shadow-lg dark:border-slate-700 dark:bg-slate-900">
                  <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="startEdit(member)">{{ 'common.edit' | translate }}</button>
                  @if (!member.owner) {
                    <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="startEdit(member)">{{ 'team.changeRole' | translate }}</button>
                    <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="startEdit(member)">{{ 'team.changeSpecialty' | translate }}</button>
                  }
                  <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="startEdit(member)">{{ 'team.assignBranch' | translate }}</button>
                  @if (!member.owner && member.userId !== currentUserId()) {
                    <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="toggleStatus(member)">
                      {{ (member.status === 'ACTIVE' ? 'team.deactivate' : 'team.activate') | translate }}
                    </button>
                  }
                  <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="openDetail(member)">{{ 'team.viewDetail' | translate }}</button>
                </div>
              }
            </div>
          }
        </article>
      } @empty {
        <p class="text-sm text-slate-500">{{ 'team.noMembers' | translate }}</p>
      }
    </div>

    <h2 class="mt-8 font-display text-lg font-semibold">{{ 'team.pendingInvites' | translate }}</h2>
    <div class="mt-3 space-y-3">
      @for (invite of pendingInvites(); track invite.id) {
        <article class="card">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p class="font-semibold">{{ inviteName(invite) }}</p>
              <p class="text-sm text-slate-500">{{ inviteLine(invite) }}</p>
              <p class="text-sm text-slate-500">{{ invite.email }}</p>
              <p class="mt-2 text-xs text-slate-500">{{ 'team.invitedOn' | translate:{ date: (invite.createdAt | date:'dd/MM/yyyy') } }}</p>
            </div>
            <span class="badge bg-amber-50 text-amber-800 dark:bg-amber-500/10 dark:text-amber-200">{{ 'team.pending' | translate }}</span>
          </div>
          @if (canManage()) {
            <div class="mt-4 flex gap-2">
              <button class="btn-secondary text-xs" type="button" (click)="resend(invite)">{{ 'team.resend' | translate }}</button>
              <button class="btn-secondary text-xs" type="button" (click)="cancelInvite(invite)">{{ 'common.cancel' | translate }}</button>
            </div>
          }
        </article>
      } @empty {
        <p class="text-sm text-slate-500">{{ 'team.noInvites' | translate }}</p>
      }
    </div>

    @if (menuId() !== null) {
      <button class="fixed inset-0 z-20 cursor-default" type="button" (click)="menuId.set(null)" [attr.aria-label]="'common.close' | translate"></button>
    }

    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg font-semibold">{{ (editing() ? 'team.editMember' : 'team.addMember') | translate }}</h2>
          <div>
            <label class="block text-sm font-medium" for="member-first">{{ 'team.firstName' | translate }} <span class="text-rose-600">*</span></label>
            <input id="member-first" class="input mt-1" formControlName="firstName" [placeholder]="'team.firstNamePlaceholder' | translate" />
            @if (invalid('firstName')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="member-last">{{ 'team.lastName' | translate }} <span class="text-rose-600">*</span></label>
            <input id="member-last" class="input mt-1" formControlName="lastName" [placeholder]="'team.lastNamePlaceholder' | translate" />
            @if (invalid('lastName')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="member-email">{{ 'team.email' | translate }} <span class="text-rose-600">*</span></label>
            <input id="member-email" class="input mt-1" type="email" formControlName="email" [placeholder]="'team.emailPlaceholder' | translate" />
            <p class="mt-1 text-xs text-slate-500">{{ 'team.emailHint' | translate }}</p>
            @if (invalid('email')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.email' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="member-role">{{ 'team.role' | translate }} <span class="text-rose-600">*</span></label>
            <select id="member-role" class="input mt-1" formControlName="role">
              @if (!editing()?.owner) {
                <option value="">{{ 'team.selectRole' | translate }}</option>
              }
              @if (editing()?.owner) {
                <option value="TENANT_OWNER">{{ labelRole('TENANT_OWNER') }}</option>
              }
              @for (role of assignableRoles; track role) {
                <option [value]="role">{{ labelRole(role) }}</option>
              }
            </select>
            @if (editing()?.owner) { <p class="mt-1 text-xs text-slate-500">{{ 'team.ownerLocked' | translate }}</p> }
            @if (invalid('role')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
          </div>
          @if (form.controls.role.value === 'VETERINARIAN') {
            <div>
              <label class="block text-sm font-medium" for="member-specialty">{{ 'team.specialty' | translate }} <span class="text-rose-600">*</span></label>
              <select id="member-specialty" class="input mt-1" formControlName="specialtyCode">
                <option value="">{{ 'team.selectSpecialty' | translate }}</option>
                @for (code of specialties; track code) {
                  <option [value]="code">{{ labelSpecialty(code) }}</option>
                }
              </select>
              @if (invalid('specialtyCode')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
            </div>
            @if (form.controls.specialtyCode.value === 'OTHER') {
              <div>
                <label class="block text-sm font-medium" for="member-specialty-other">{{ 'team.otherSpecialty' | translate }} <span class="text-rose-600">*</span></label>
                <input id="member-specialty-other" class="input mt-1" formControlName="specialtyOther" [placeholder]="'team.otherSpecialtyPlaceholder' | translate" />
                @if (invalid('specialtyOther')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
              </div>
            }
          }
          @if (editing()) {
            <div>
              <label class="block text-sm font-medium" for="member-branch">{{ 'team.branch' | translate }}</label>
              <select id="member-branch" class="input mt-1" formControlName="branchId">
                <option value="">{{ 'team.noBranch' | translate }}</option>
                @for (branch of branches(); track branch.id) {
                  <option [value]="branch.id">{{ branch.name }}</option>
                }
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium" for="member-status">{{ 'team.status' | translate }}</label>
              <select id="member-status" class="input mt-1" formControlName="status">
                <option value="ACTIVE">{{ 'team.active' | translate }}</option>
                <option value="INACTIVE">{{ 'team.inactive' | translate }}</option>
              </select>
            </div>
          }
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary" type="submit">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }

    @if (detail(); as member) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="detail.set(null)">
        <div class="card w-full max-w-lg space-y-2" (click)="$event.stopPropagation()">
          <h2 class="font-display text-lg font-semibold">{{ 'team.memberDetail' | translate }}</h2>
          <p class="font-medium">{{ member.fullName }}</p>
          <p class="text-sm text-slate-500">{{ member.email }}</p>
          <p class="text-sm">{{ 'team.role' | translate }}: {{ labelRole(member.role) }}</p>
          @if (member.role === 'VETERINARIAN') {
            <p class="text-sm">{{ 'team.specialty' | translate }}: {{ memberSpecialty(member) }}</p>
          }
          <p class="text-sm">{{ 'team.branch' | translate }}: {{ member.branchName || ('team.noBranch' | translate) }}</p>
          <p class="text-sm">{{ 'team.status' | translate }}: {{ (member.status === 'ACTIVE' ? 'team.active' : 'team.inactive') | translate }}</p>
          <div class="flex justify-end pt-2">
            <button class="btn-secondary" type="button" (click)="detail.set(null)">{{ 'common.close' | translate }}</button>
          </div>
        </div>
      </div>
    }
  `
})
export class TeamPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  private signup = inject(SignupService);
  private billing = inject(BillingService);
  private i18n = inject(TranslateService);
  auth = inject(AuthService);
  private session = inject(SessionInactivityService);

  members = signal<TeamMember[]>([]);
  invites = signal<StaffInvite[]>([]);
  branches = signal<{ id: number; name: string }[]>([]);
  usage = signal<{ current: number; limit: number } | null>(null);
  query = signal('');
  roleFilter = signal('');
  statusFilter = signal('');
  menuId = signal<number | null>(null);
  detail = signal<TeamMember | null>(null);
  editing = signal<TeamMember | null>(null);
  open = false;
  submitted = false;
  assignableRoles = TEAM_ASSIGNABLE_ROLES;
  specialties = VETERINARY_SPECIALTIES;

  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    role: ['', Validators.required],
    specialtyCode: [''],
    specialtyOther: [''],
    branchId: [''],
    status: ['ACTIVE']
  });

  filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const role = this.roleFilter();
    const status = this.statusFilter();
    return this.members().filter(member => {
      const hay = `${member.fullName} ${member.email}`.toLowerCase();
      return (!q || hay.includes(q)) && (!role || member.role === role) && (!status || member.status === status);
    });
  });

  pendingInvites = computed(() => this.invites().filter(invite => invite.status === 'PENDING'));

  percent = computed(() => {
    const plan = this.usage();
    if (!plan || !plan.limit) {
      return 0;
    }
    return Math.min(100, Math.round((plan.current / plan.limit) * 100));
  });

  atLimit = computed(() => {
    const plan = this.usage();
    return !!plan && plan.limit > 0 && plan.current >= plan.limit;
  });

  ngOnInit(): void {
    this.form.controls.role.valueChanges.subscribe(() => this.syncValidators());
    this.form.controls.specialtyCode.valueChanges.subscribe(() => this.syncValidators());
    this.reload();
  }

  canManage(): boolean {
    return this.auth.hasPermission('STAFF_MANAGE');
  }

  currentUserId(): number | undefined {
    return this.auth.user()?.id;
  }

  labelRole(code: string): string {
    return roleLabel(this.i18n, code);
  }

  labelSpecialty(code: string): string {
    return specialtyLabel(this.i18n, code);
  }

  memberSpecialty(member: TeamMember): string {
    return specialtyLabel(this.i18n, member.specialtyCode || member.specialty, member.specialtyOther, member.specialty);
  }

  memberLine(member: TeamMember): string {
    const role = this.labelRole(member.role);
    if (member.role === 'VETERINARIAN') {
      const specialty = this.memberSpecialty(member);
      return specialty ? `${role} · ${specialty}` : role;
    }
    return role;
  }

  initials(member: TeamMember): string {
    return `${member.firstName || ''} ${member.lastName || ''}`
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part.charAt(0).toUpperCase())
      .join('') || '?';
  }

  inviteName(invite: StaffInvite): string {
    const name = `${invite.firstName || ''} ${invite.lastName || ''}`.trim();
    return name || invite.email;
  }

  inviteLine(invite: StaffInvite): string {
    const role = this.labelRole(invite.role);
    if (invite.role !== 'VETERINARIAN') {
      return role;
    }
    const specialty = specialtyLabel(this.i18n, invite.specialtyCode, invite.specialtyOther);
    return specialty ? `${role} · ${specialty}` : role;
  }

  toggleMenu(id: number): void {
    this.menuId.update(current => current === id ? null : id);
  }

  startCreate(): void {
    if (this.atLimit()) {
      return;
    }
    this.editing.set(null);
    this.submitted = false;
    this.menuId.set(null);
    this.form.reset({ firstName: '', lastName: '', email: '', role: '', specialtyCode: '', specialtyOther: '', branchId: '', status: 'ACTIVE' });
    this.form.controls.role.enable();
    this.form.controls.status.enable();
    this.syncValidators();
    this.open = true;
  }

  startEdit(member: TeamMember): void {
    this.editing.set(member);
    this.submitted = false;
    this.menuId.set(null);
    this.ensureBranches();
    const known = (VETERINARY_SPECIALTIES as readonly string[]).includes(member.specialty || '');
    const specialty = member.specialtyCode || (known ? member.specialty : '');
    this.form.reset({
      firstName: member.firstName || '',
      lastName: member.lastName || '',
      email: member.email || '',
      role: member.role || '',
      specialtyCode: specialty || (!member.specialtyCode && member.specialty ? 'OTHER' : ''),
      specialtyOther: member.specialtyOther || (!member.specialtyCode && member.specialty && !known ? member.specialty : ''),
      branchId: member.branchId ? String(member.branchId) : '',
      status: member.status || 'ACTIVE'
    });
    if (member.owner) {
      this.form.controls.role.disable();
      this.form.controls.status.disable();
    } else {
      this.form.controls.role.enable();
      this.form.controls.status.enable();
    }
    this.syncValidators();
    this.open = true;
  }

  openDetail(member: TeamMember): void {
    this.menuId.set(null);
    this.detail.set(member);
  }

  invalid(name: 'firstName' | 'lastName' | 'email' | 'role' | 'specialtyCode' | 'specialtyOther'): boolean {
    const control = this.form.controls[name];
    return (this.submitted || control.touched) && control.invalid;
  }

  save(): void {
    if (!this.session.ensureActive()) {
      return;
    }
    this.submitted = true;
    this.syncValidators();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: Record<string, unknown> = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      email: raw.email,
      role: raw.role,
      specialtyCode: raw.role === 'VETERINARIAN' ? raw.specialtyCode : null,
      specialtyOther: raw.specialtyCode === 'OTHER' ? raw.specialtyOther : null
    };
    const editing = this.editing();
    if (editing) {
      body['branchId'] = raw.branchId ? Number(raw.branchId) : 0;
      body['status'] = raw.status;
      this.api.put(`/employees/members/${editing.membershipId}`, body).subscribe({
        next: () => { this.toast.show('common.saved'); this.open = false; this.reload(); },
        error: (e) => this.toast.show(e.error?.message || 'common.error', true)
      });
      return;
    }
    this.api.post<{ outcome: string }>('/employees/members', body).subscribe({
      next: (result) => {
        this.toast.show(result.outcome === 'INVITED' ? 'team.inviteSent' : 'team.memberLinked');
        this.open = false;
        this.reload();
      },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  toggleStatus(member: TeamMember): void {
    if (!this.session.ensureActive()) {
      return;
    }
    this.menuId.set(null);
    const status = member.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.api.put(`/employees/members/${member.membershipId}`, { status }).subscribe({
      next: () => { this.toast.show('common.saved'); this.reload(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  resend(invite: StaffInvite): void {
    if (!this.session.ensureActive()) {
      return;
    }
    this.api.post(`/employees/invites/${invite.id}/resend`, {}).subscribe({
      next: () => { this.toast.show('team.invitationResent'); this.reload(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  cancelInvite(invite: StaffInvite): void {
    if (!this.session.ensureActive()) {
      return;
    }
    this.signup.cancelInvite(invite.id).subscribe({
      next: () => { this.toast.show('team.invitationCanceled'); this.reload(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  private reload(): void {
    this.api.get<TeamMember[]>('/employees/members').subscribe(rows => this.members.set(rows || []));
    if (this.canManage()) {
      this.signup.listInvites().subscribe(rows => this.invites.set(rows || []));
    }
    this.billing.loadSubscription().subscribe(sub => {
      if (sub.usage?.users) {
        this.usage.set({ current: sub.usage.users.current, limit: sub.usage.users.limit });
      }
    });
  }

  private ensureBranches(): void {
    if (this.branches().length) {
      return;
    }
    this.api.get<{ id: number; name: string }[]>('/branches').subscribe(rows => this.branches.set(rows || []));
  }

  private syncValidators(): void {
    const specialty = this.form.controls.specialtyCode;
    const other = this.form.controls.specialtyOther;
    if (this.form.controls.role.value === 'VETERINARIAN') {
      specialty.setValidators([Validators.required]);
    } else {
      specialty.clearValidators();
    }
    if (this.form.controls.specialtyCode.value === 'OTHER') {
      other.setValidators([Validators.required]);
    } else {
      other.clearValidators();
    }
    specialty.updateValueAndValidity({ emitEvent: false });
    other.updateValueAndValidity({ emitEvent: false });
  }
}
