import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminPlansPage, emptyPlanDraft, flattenAdminPlan, payloadFromDraft, validatePlanDraft } from './plans.page';

describe('Admin plan form', () => {
  it('rejects empty codes, negative prices and invalid Paddle ids', () => {
    const errors = validatePlanDraft({
      ...emptyPlanDraft(),
      code: '  ',
      monthlyPrice: -1,
      annualPrice: -5,
      currency: 'EUR',
      maxUsers: -1,
      paddleProductId: 'prod_x',
      paddleMonthlyPriceId: 'price_1',
      paddleAnnualPriceId: 'pri_ok'
    });
    expect(errors).toContain('El código del plan no puede estar vacío');
    expect(errors).toContain('El precio mensual no puede ser negativo');
    expect(errors).toContain('El precio anual no puede ser negativo');
    expect(errors).toContain('La moneda debe ser USD');
    expect(errors).toContain('El límite de usuarios no puede ser negativo');
    expect(errors).toContain('El Paddle Product ID debe comenzar por pro_');
    expect(errors).toContain('El Paddle Monthly Price ID debe comenzar por pri_');
  });

  it('accepts a valid USD plan with Paddle ids', () => {
    expect(validatePlanDraft({
      ...emptyPlanDraft(),
      code: 'BASIC',
      monthlyPrice: 29,
      annualPrice: 290,
      paddleProductId: 'pro_basic',
      paddleMonthlyPriceId: 'pri_month',
      paddleAnnualPriceId: 'pri_year'
    })).toEqual([]);
  });

  it('flattens nested limits for the editor', () => {
    const draft = flattenAdminPlan({
      id: 1,
      code: 'PRO',
      nameEs: 'Profesional',
      nameEn: 'Professional',
      currency: 'USD',
      monthlyPrice: 79,
      active: true,
      maxUsers: 0,
      maxVeterinarians: 0,
      maxBranches: 0,
      maxStorageMb: 0,
      maxMessagesMonth: 0,
      reportsEnabled: false,
      messagingEnabled: false,
      laboratoryEnabled: false,
      limits: {
        maxUsers: 20,
        maxVeterinarians: 8,
        maxBranches: 3,
        maxStorageMb: 10240,
        maxMessagesMonth: 2000,
        reportsEnabled: true,
        messagingEnabled: true,
        laboratoryEnabled: true
      }
    });
    expect(draft.maxUsers).toBe(20);
    expect(draft.laboratoryEnabled).toBeTrue();
  });

  it('does not send a frontend amount as the source of truth in the save payload', () => {
    const payload = payloadFromDraft({
      ...emptyPlanDraft(),
      monthlyPrice: 29,
      paddleMonthlyPriceId: 'pri_month'
    });
    expect(payload.paddleMonthlyPriceId).toBe('pri_month');
    expect(payload.monthlyPrice).toBe(29);
  });

  it('asks for confirmation before rotating a Paddle price and blocks double submit', () => {
    const api = jasmine.createSpyObj('ApiService', ['get', 'put', 'post']);
    api.get.and.returnValue(of([]));
    api.post.and.returnValue(of({}));
    TestBed.configureTestingModule({
      imports: [AdminPlansPage],
      providers: [
        provideTranslateService(),
        { provide: ApiService, useValue: api },
        { provide: ToastService, useValue: { show() {}, showHttpError() {} } }
      ]
    });
    const fixture = TestBed.createComponent(AdminPlansPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(false);
    component.rotate({ ...emptyPlanDraft(), id: 1, monthlyPrice: 39 }, 'MONTHLY');
    expect(window.confirm).toHaveBeenCalled();
    expect(api.post).not.toHaveBeenCalled();
    component.busy.set(true);
    (window.confirm as jasmine.Spy).and.returnValue(true);
    component.save({ ...emptyPlanDraft(), id: 1 });
    expect(api.put).not.toHaveBeenCalled();
    expect(component.busy()).toBeTrue();
  });
});
