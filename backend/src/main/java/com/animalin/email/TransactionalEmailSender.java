package com.animalin.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionalEmailSender {

    private static final Logger log = LoggerFactory.getLogger(TransactionalEmailSender.class);

    public void sendAfterCommit(String type, String recipient, Runnable send) {
        Runnable safe = () -> runSafely(type, recipient, send);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
            return;
        }
        safe.run();
    }

    private void runSafely(String type, String recipient, Runnable send) {
        try {
            send.run();
        } catch (EmailDeliveryException ex) {
            log.warn("Email delivery failed type={} to={} status=failed httpStatus={}",
                    type, EmailLogSupport.mask(recipient), ex.getHttpStatus());
        } catch (RuntimeException ex) {
            log.warn("Email delivery failed type={} to={} status=failed cause={}",
                    type, EmailLogSupport.mask(recipient), ex.getClass().getSimpleName());
        }
    }
}
