import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { PlatformUser } from '../../core/models';
import { PLATFORM_ROLES, VETERINARY_SPECIALTIES, roleLabel, specialtyLabel } from '../../core/team-labels';
import { StatusBadgePipe } from '../../shared/ui/status-badge.pipe';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, StatusBadgePipe],
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="font-display text-2xl font-semibold">{{ 'nav.users' | translate }}</h1>
        <p class="mt-1 text-sm text-slate-500">{{ 'admin.usersSubtitle' | translate }}</p>
      </div>
      <button class="btn-primary" type="button" (click)="startCreate()">+ {{ 'admin.newUser' | translate }}</button>
    </div>

    <div class="card mt-6 grid gap-3 sm:grid-cols-2">
      <div>
        <label class="block text-sm font-medium" for="user-search">{{ 'common.search' | translate }}</label>
        <input id="user-search" class="input mt-1" [value]="query()" (input)="query.set($any($event.target).value)" [placeholder]="'admin.searchUsers' | translate" />
      </div>
      <div>
        <label class="block text-sm font-medium" for="user-role-filter">{{ 'admin.role' | translate }}</label>
        <select id="user-role-filter" class="input mt-1" [value]="roleFilter()" (change)="roleFilter.set($any($event.target).value)">
          <option value="">{{ 'admin.allRoles' | translate }}</option>
          @for (role of roles; track role) {
            <option [value]="role">{{ labelRole(role) }}</option>
          }
        </select>
      </div>
    </div>

    <div class="card mt-6 overflow-x-auto p-0">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500 dark:bg-white/5">
          <tr>
            <th class="px-4 py-3">{{ 'owners.name' | translate }}</th>
            <th class="px-4 py-3">{{ 'auth.email' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.role' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.clinic' | translate }}</th>
            <th class="px-4 py-3">{{ 'admin.accountStatus' | translate }}</th>
            <th class="px-4 py-3 text-right">{{ 'common.actions' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (u of filtered(); track u.id) {
            <tr class="border-t border-slate-100 dark:border-white/5">
              <td class="px-4 py-3 font-medium">{{ u.fullName }}</td>
              <td class="px-4 py-3">{{ u.email }}</td>
              <td class="px-4 py-3">{{ rolesOf(u) }}</td>
              <td class="px-4 py-3">{{ clinicsOf(u) }}</td>
              <td class="px-4 py-3">
                <span [class]="u.status | statusBadge">{{ (u.enabled ? 'admin.active' : 'admin.inactive') | translate }}</span>
              </td>
              <td class="relative z-30 px-4 py-3 text-right">
                <button class="btn-secondary px-3 py-1.5 text-xs" type="button" (click)="toggleMenu(u.id)" [attr.aria-label]="'common.actions' | translate">...</button>
                @if (menuId() === u.id) {
                  <div class="absolute right-4 z-30 mt-1 w-44 rounded-xl border border-slate-200 bg-white py-1 text-left shadow-lg dark:border-slate-700 dark:bg-slate-900">
                    <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="startEdit(u)">{{ 'common.edit' | translate }}</button>
                    <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="toggleEnabled(u)">
                      {{ (u.enabled ? 'admin.deactivateUser' : 'admin.activateUser') | translate }}
                    </button>
                    <button class="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-white/5" type="button" (click)="openDetail(u)">{{ 'team.viewDetail' | translate }}</button>
                  </div>
                }
              </td>
            </tr>
          } @empty {
            <tr><td class="px-4 py-6 text-slate-500" colspan="6">{{ 'admin.noUsers' | translate }}</td></tr>
          }
        </tbody>
      </table>
    </div>

    @if (menuId() !== null) {
      <button class="fixed inset-0 z-20 cursor-default" type="button" (click)="menuId.set(null)" [attr.aria-label]="'common.close' | translate"></button>
    }

    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="open=false">
        <form class="card max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto" (click)="$event.stopPropagation()" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display text-lg font-semibold">{{ (editingId ? 'admin.editUser' : 'admin.newUser') | translate }}</h2>
          <div>
            <label class="block text-sm font-medium" for="admin-first">{{ 'auth.firstName' | translate }} <span class="text-rose-600">*</span></label>
            <input id="admin-first" class="input mt-1" formControlName="firstName" [placeholder]="'team.firstNamePlaceholder' | translate" />
            @if (invalid('firstName')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="admin-last">{{ 'auth.lastName' | translate }} <span class="text-rose-600">*</span></label>
            <input id="admin-last" class="input mt-1" formControlName="lastName" [placeholder]="'team.lastNamePlaceholder' | translate" />
            @if (invalid('lastName')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="admin-email">{{ 'auth.email' | translate }} <span class="text-rose-600">*</span></label>
            <input id="admin-email" class="input mt-1" type="email" formControlName="email" placeholder="nombre@ejemplo.com" />
            @if (invalid('email')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.email' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="admin-phone">{{ 'admin.phone' | translate }}</label>
            <input id="admin-phone" class="input mt-1" formControlName="phone" [placeholder]="'admin.phoneOptional' | translate" />
          </div>
          <div>
            <label class="block text-sm font-medium" for="admin-password">
              {{ (editingId ? 'admin.passwordOptional' : 'admin.password') | translate }}
              @if (!editingId) { <span class="text-rose-600">*</span> }
            </label>
            <input id="admin-password" class="input mt-1" type="password" formControlName="password" autocomplete="new-password" />
            <p class="mt-1 text-xs text-slate-500">{{ (editingId ? 'admin.passwordKeep' : 'admin.passwordHint') | translate }}</p>
            @if (invalid('password')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.password' | translate }}</p> }
          </div>
          <div>
            <label class="block text-sm font-medium" for="admin-role">{{ 'admin.role' | translate }} <span class="text-rose-600">*</span></label>
            <select id="admin-role" class="input mt-1" formControlName="role">
              <option value="">{{ 'team.selectRole' | translate }}</option>
              @for (role of roles; track role) {
                <option [value]="role">{{ labelRole(role) }}</option>
              }
            </select>
            @if (invalid('role')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
          </div>
          @if (needsTenant()) {
            <div>
              <label class="block text-sm font-medium" for="admin-tenant">{{ 'admin.clinic' | translate }} <span class="text-rose-600">*</span></label>
              <select id="admin-tenant" class="input mt-1" formControlName="tenantId">
                <option value="">{{ 'admin.selectClinic' | translate }}</option>
                @for (tenant of tenants(); track tenant.id) {
                  <option [value]="tenant.id">{{ tenant.name }}</option>
                }
              </select>
              <p class="mt-1 text-xs text-slate-500">{{ 'admin.tenantRequired' | translate }}</p>
              @if (invalid('tenantId')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
            </div>
          }
          @if (form.controls.role.value === 'VETERINARIAN') {
            <div>
              <label class="block text-sm font-medium" for="admin-specialty">{{ 'team.specialty' | translate }} <span class="text-rose-600">*</span></label>
              <select id="admin-specialty" class="input mt-1" formControlName="specialtyCode">
                <option value="">{{ 'team.selectSpecialty' | translate }}</option>
                @for (code of specialties; track code) {
                  <option [value]="code">{{ labelSpecialty(code) }}</option>
                }
              </select>
              @if (invalid('specialtyCode')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
            </div>
            @if (form.controls.specialtyCode.value === 'OTHER') {
              <div>
                <label class="block text-sm font-medium" for="admin-specialty-other">{{ 'team.otherSpecialty' | translate }} <span class="text-rose-600">*</span></label>
                <input id="admin-specialty-other" class="input mt-1" formControlName="specialtyOther" [placeholder]="'team.otherSpecialtyPlaceholder' | translate" />
                @if (invalid('specialtyOther')) { <p class="mt-1 text-xs text-rose-600">{{ 'validation.required' | translate }}</p> }
              </div>
            }
          }
          <div>
            <label class="block text-sm font-medium" for="admin-status">{{ 'admin.accountStatus' | translate }}</label>
            <select id="admin-status" class="input mt-1" formControlName="status">
              <option value="ACTIVE">{{ 'admin.active' | translate }}</option>
              <option value="INACTIVE">{{ 'admin.inactive' | translate }}</option>
            </select>
          </div>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" class="btn-secondary" (click)="open=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary" type="submit">{{ 'common.save' | translate }}</button>
          </div>
        </form>
      </div>
    }

    @if (detail(); as user) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="detail.set(null)">
        <div class="card max-h-[90vh] w-full max-w-lg space-y-3 overflow-y-auto" (click)="$event.stopPropagation()">
          <h2 class="font-display text-lg font-semibold">{{ 'admin.userDetail' | translate }}</h2>
          <p class="font-medium">{{ user.fullName }}</p>
          <p class="text-sm text-slate-500">{{ user.email }}</p>
          @if (user.phone) { <p class="text-sm">{{ user.phone }}</p> }
          <p class="text-sm">{{ 'admin.accountStatus' | translate }}: {{ (user.enabled ? 'admin.active' : 'admin.inactive') | translate }}</p>
          <div>
            <p class="text-sm font-medium">{{ 'admin.globalRoles' | translate }}</p>
            <p class="text-sm text-slate-500">{{ globalRoles(user) || '—' }}</p>
          </div>
          <div>
            <p class="text-sm font-medium">{{ 'admin.memberships' | translate }}</p>
            @if (!user.memberships.length) {
              <p class="text-sm text-slate-500">{{ 'admin.noMemberships' | translate }}</p>
            } @else {
              @for (membership of user.memberships; track membership.id) {
                <p class="text-sm text-slate-500">{{ membership.tenantName }} · {{ labelRole(membership.role) }} · {{ membership.status }}</p>
              }
            }
          </div>
          <div class="flex justify-end">
            <button class="btn-secondary" type="button" (click)="detail.set(null)">{{ 'common.close' | translate }}</button>
          </div>
        </div>
      </div>
    }
  `
})
export class AdminUsersPage implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);
  private i18n = inject(TranslateService);
  users = signal<PlatformUser[]>([]);
  tenants = signal<{ id: number; name: string }[]>([]);
  query = signal('');
  roleFilter = signal('');
  menuId = signal<number | null>(null);
  detail = signal<PlatformUser | null>(null);
  open = false;
  submitted = false;
  editingId: number | null = null;
  roles = PLATFORM_ROLES;
  specialties = VETERINARY_SPECIALTIES;
  form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: [''],
    role: ['', Validators.required],
    tenantId: [''],
    specialtyCode: [''],
    specialtyOther: [''],
    status: ['ACTIVE']
  });

  ngOnInit(): void {
    this.reload();
    this.api.get<{ id: number; name: string }[]>('/admin/tenants').subscribe(rows => this.tenants.set(rows || []));
    this.form.controls.role.valueChanges.subscribe(() => this.syncValidators());
    this.form.controls.specialtyCode.valueChanges.subscribe(() => this.syncValidators());
  }

  filtered(): PlatformUser[] {
    const q = this.query().trim().toLowerCase();
    const role = this.roleFilter();
    return this.users().filter(user => {
      const hay = `${user.fullName} ${user.email}`.toLowerCase();
      const roles = new Set([...(user.roles || []), ...(user.memberships || []).map(m => m.role)]);
      return (!q || hay.includes(q)) && (!role || roles.has(role));
    });
  }

  labelRole(code: string): string {
    return roleLabel(this.i18n, code);
  }

  labelSpecialty(code: string): string {
    return specialtyLabel(this.i18n, code);
  }

  rolesOf(user: PlatformUser): string {
    const codes = [...new Set([...(user.roles || []), ...(user.memberships || []).map(m => m.role)])];
    return codes.map(code => this.labelRole(code)).join(', ') || '—';
  }

  globalRoles(user: PlatformUser): string {
    return (user.roles || []).map(code => this.labelRole(code)).join(', ');
  }

  clinicsOf(user: PlatformUser): string {
    return (user.memberships || []).map(m => m.tenantName).join(', ') || '—';
  }

  needsTenant(): boolean {
    const role = this.form.controls.role.value;
    return !!role && role !== 'SUPER_ADMIN';
  }

  toggleMenu(id: number): void {
    this.menuId.update(current => current === id ? null : id);
  }

  startCreate(): void {
    this.editingId = null;
    this.submitted = false;
    this.menuId.set(null);
    this.form.reset({ firstName: '', lastName: '', email: '', phone: '', password: '', role: '', tenantId: '', specialtyCode: '', specialtyOther: '', status: 'ACTIVE' });
    this.syncValidators();
    this.open = true;
  }

  startEdit(user: PlatformUser): void {
    this.editingId = user.id;
    this.submitted = false;
    this.menuId.set(null);
    const membership = user.memberships?.[0];
    const role = user.roles?.includes('SUPER_ADMIN') && !membership ? 'SUPER_ADMIN' : (membership?.role || user.roles?.[0] || '');
    this.form.reset({
      firstName: user.firstName || '',
      lastName: user.lastName || '',
      email: user.email || '',
      phone: user.phone || '',
      password: '',
      role,
      tenantId: membership ? String(membership.tenantId) : '',
      specialtyCode: '',
      specialtyOther: '',
      status: user.enabled ? 'ACTIVE' : 'INACTIVE'
    });
    this.syncValidators();
    this.open = true;
  }

  openDetail(user: PlatformUser): void {
    this.menuId.set(null);
    this.detail.set(user);
  }

  invalid(name: 'firstName' | 'lastName' | 'email' | 'password' | 'role' | 'tenantId' | 'specialtyCode' | 'specialtyOther'): boolean {
    const control = this.form.controls[name];
    return (this.submitted || control.touched) && control.invalid;
  }

  save(): void {
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
      phone: raw.phone,
      role: raw.role,
      enabled: raw.status === 'ACTIVE',
      tenantId: raw.role === 'SUPER_ADMIN' || !raw.tenantId ? null : Number(raw.tenantId),
      specialtyCode: raw.role === 'VETERINARIAN' ? raw.specialtyCode : null,
      specialtyOther: raw.specialtyCode === 'OTHER' ? raw.specialtyOther : null
    };
    if (raw.password) {
      body['password'] = raw.password;
    }
    const request = this.editingId
      ? this.api.put<PlatformUser>(`/admin/users/${this.editingId}`, body)
      : this.api.post<PlatformUser>('/admin/users', body);
    request.subscribe({
      next: () => { this.toast.show('admin.userCreated'); this.open = false; this.reload(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  toggleEnabled(user: PlatformUser): void {
    this.menuId.set(null);
    this.api.post(`/admin/users/${user.id}/status`, { enabled: !user.enabled }).subscribe({
      next: () => { this.toast.show('common.saved'); this.reload(); },
      error: (e) => this.toast.show(e.error?.message || 'common.error', true)
    });
  }

  private reload(): void {
    this.api.get<PlatformUser[]>('/admin/users').subscribe(rows => this.users.set(rows || []));
  }

  private syncValidators(): void {
    const password = this.form.controls.password;
    const tenant = this.form.controls.tenantId;
    const specialty = this.form.controls.specialtyCode;
    const other = this.form.controls.specialtyOther;
    if (this.editingId) {
      password.setValidators([control => !control.value || String(control.value).length >= 8 ? null : { minlength: true }]);
    } else {
      password.setValidators([Validators.required, Validators.minLength(8)]);
    }
    if (this.needsTenant()) {
      tenant.setValidators([Validators.required]);
    } else {
      tenant.clearValidators();
    }
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
    password.updateValueAndValidity({ emitEvent: false });
    tenant.updateValueAndValidity({ emitEvent: false });
    specialty.updateValueAndValidity({ emitEvent: false });
    other.updateValueAndValidity({ emitEvent: false });
  }
}
