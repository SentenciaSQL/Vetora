package com.animalin.billing.paddle;

import java.util.List;

public interface PaddleClient {

    PaddleDtos.Customer getCustomer(String customerId);

    List<PaddleDtos.Customer> listCustomers();

    PaddleDtos.Subscription getSubscription(String subscriptionId);

    List<PaddleDtos.Subscription> listSubscriptions();

    PaddleDtos.Subscription updateSubscription(String subscriptionId, PaddleDtos.UpdateSubscriptionRequest request);

    PaddleDtos.Subscription cancelSubscription(String subscriptionId, PaddleDtos.CancelSubscriptionRequest request);

    PaddleDtos.Transaction getTransaction(String transactionId);

    List<PaddleDtos.Transaction> listTransactions();

    PaddleDtos.Product getProduct(String productId);

    List<PaddleDtos.Product> listProducts();

    PaddleDtos.Product createProduct(PaddleDtos.CreateProductRequest request);

    PaddleDtos.Product updateProduct(String productId, PaddleDtos.UpdateProductRequest request);

    PaddleDtos.Price getPrice(String priceId);

    List<PaddleDtos.Price> listPrices();

    PaddleDtos.Price createPrice(PaddleDtos.CreatePriceRequest request);

    PaddleDtos.Price updatePrice(String priceId, PaddleDtos.UpdatePriceRequest request);

    PaddleDtos.Price setPriceTrialPeriod(String priceId, PaddleDtos.TrialPeriod trialPeriod);

    PaddleDtos.SubscriptionPreview previewSubscriptionUpdate(String subscriptionId, PaddleDtos.UpdateSubscriptionRequest request);

    PaddleDtos.PortalSession createCustomerPortalSession(String customerId, PaddleDtos.CreatePortalSessionRequest request);
}
