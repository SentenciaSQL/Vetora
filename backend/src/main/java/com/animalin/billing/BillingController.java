package com.animalin.billing;

import com.animalin.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;
    private final AuthService authService;

    public BillingController(BillingService billingService, AuthService authService) {
        this.billingService = billingService;
        this.authService = authService;
    }

    @GetMapping("/plans")
    public List<BillingDtos.PlanResponse> plans() {
        return billingService.plans();
    }

    @GetMapping("/config")
    public BillingDtos.BillingConfigResponse config() {
        return billingService.configAndPlans();
    }

    @GetMapping("/subscription")
    public BillingDtos.SubscriptionResponse subscription() {
        return billingService.currentSubscription();
    }

    @PostMapping("/checkout")
    public BillingDtos.CheckoutResponse checkout(@RequestBody BillingDtos.CheckoutRequest request) {
        authService.requireActiveSession();
        return billingService.prepareCheckout(request);
    }

    @PostMapping("/customer-portal")
    public BillingDtos.PortalResponse customerPortal() {
        authService.requireActiveSession();
        return billingService.customerPortal();
    }

    @PostMapping("/subscription/cancel")
    @ResponseStatus(HttpStatus.OK)
    public BillingDtos.SubscriptionResponse cancel(@RequestBody(required = false) BillingDtos.CancelSubscriptionRequest request) {
        authService.requireActiveSession();
        return billingService.cancel(request);
    }

    @PostMapping("/subscription/change-plan/preview")
    public BillingDtos.ChangePreviewResponse previewChange(@RequestBody BillingDtos.ChangePlanRequest request) {
        authService.requireActiveSession();
        return billingService.previewChange(request);
    }

    @PostMapping("/subscription/change-plan")
    public BillingDtos.SubscriptionResponse changePlan(@Valid @RequestBody BillingDtos.ChangePlanRequest request) {
        authService.requireActiveSession();
        return billingService.changePlan(request);
    }

    @PostMapping("/subscription/pending-change/cancel")
    public BillingDtos.SubscriptionResponse cancelPendingChange() {
        authService.requireActiveSession();
        return billingService.cancelPendingChange();
    }
}
