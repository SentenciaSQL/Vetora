package com.animalin.billing;

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

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
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
        return billingService.prepareCheckout(request);
    }

    @PostMapping("/customer-portal")
    public BillingDtos.PortalResponse customerPortal() {
        return billingService.customerPortal();
    }

    @PostMapping("/subscription/cancel")
    @ResponseStatus(HttpStatus.OK)
    public BillingDtos.SubscriptionResponse cancel(@RequestBody(required = false) BillingDtos.CancelSubscriptionRequest request) {
        return billingService.cancel(request);
    }

    @PostMapping("/subscription/change-plan/preview")
    public BillingDtos.ChangePreviewResponse previewChange(@RequestBody BillingDtos.ChangePlanRequest request) {
        return billingService.previewChange(request);
    }

    @PostMapping("/subscription/change-plan")
    public BillingDtos.SubscriptionResponse changePlan(@Valid @RequestBody BillingDtos.ChangePlanRequest request) {
        return billingService.changePlan(request);
    }
}
