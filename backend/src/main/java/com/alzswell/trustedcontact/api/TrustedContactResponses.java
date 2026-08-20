package com.alzswell.trustedcontact.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TrustedContactResponses {
    public record Contact(UUID contactId,String customerId,UUID consentId,String displayName,String relationshipCode,
            String maskedContact,boolean recipientAccepted,String status,List<String> scopes,OffsetDateTime validFrom,
            OffsetDateTime expiresAt,long version,boolean authorizedToAct,boolean externalContactEnabled){}
    public record ContactList(String customerId,List<Contact> items,int total,boolean externalContactExecuted){}
    private TrustedContactResponses(){}
}
