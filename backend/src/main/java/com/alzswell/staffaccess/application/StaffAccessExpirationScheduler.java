package com.alzswell.staffaccess.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.staff-access.expiration-scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class StaffAccessExpirationScheduler {
    private final StaffAccessPolicyService service;

    public StaffAccessExpirationScheduler(StaffAccessPolicyService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.staff-access.expiration-interval-ms:60000}")
    public void expireDueGrants() {
        service.expireDueGrants();
    }
}
