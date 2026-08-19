package com.alzswell.inbox.api;

import com.alzswell.common.api.*;
import com.alzswell.inbox.api.InboxRequests.*;
import com.alzswell.inbox.api.InboxResponses.*;
import com.alzswell.inbox.application.OperationalInboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/customers/{customerId}")
public class CustomerInboxController {
    private static final String ID="^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final OperationalInboxService service;
    public CustomerInboxController(OperationalInboxService service) { this.service=service; }

    @GetMapping("/inbox") @PreAuthorize("#customerId == authentication.name and hasAuthority('INBOX_READ')")
    public ResponseEntity<ApiResponse<InboxPage>> messages(@PathVariable @Pattern(regexp=ID) String customerId,
            @RequestParam(required=false) Boolean unreadOnly,
            @RequestParam(required=false) @Pattern(regexp="CHANGE_ALERT|FOLLOW_UP|SERVICE_NOTICE") String type,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit,
            @RequestParam(required=false) String cursor) {
        return ApiResponses.ok("INBOX_RETRIEVED","인앱 알림 목록을 조회했습니다.",service.messages(customerId,unreadOnly,type,limit,cursor));
    }
    @GetMapping("/inbox/{messageId}") @PreAuthorize("#customerId == authentication.name and hasAuthority('INBOX_READ')")
    public ResponseEntity<ApiResponse<InboxMessage>> message(@PathVariable @Pattern(regexp=ID) String customerId,
            @PathVariable UUID messageId) {
        return ApiResponses.ok("INBOX_MESSAGE_RETRIEVED","인앱 알림을 조회했습니다.",service.message(customerId,messageId));
    }
    @PostMapping("/inbox/{messageId}/read") @PreAuthorize("#customerId == authentication.name and hasAuthority('INBOX_WRITE')")
    public ResponseEntity<ApiResponse<InboxMessage>> markRead(@PathVariable @Pattern(regexp=ID) String customerId,
            @PathVariable UUID messageId,@Valid @RequestBody MarkReadCommand command) {
        return ApiResponses.ok("INBOX_MESSAGE_READ","인앱 알림을 읽음 처리했습니다.",service.markRead(customerId,messageId,command.expectedVersion()));
    }
    @GetMapping("/notification-preferences") @PreAuthorize("#customerId == authentication.name and hasAuthority('INBOX_READ')")
    public ResponseEntity<ApiResponse<NotificationPreference>> preference(@PathVariable @Pattern(regexp=ID) String customerId) {
        return ApiResponses.ok("NOTIFICATION_PREFERENCE_RETRIEVED","인앱 알림 설정을 조회했습니다.",service.preference(customerId));
    }
    @PutMapping("/notification-preferences") @PreAuthorize("#customerId == authentication.name and hasAuthority('INBOX_WRITE')")
    public ResponseEntity<ApiResponse<NotificationPreference>> updatePreference(@PathVariable @Pattern(regexp=ID) String customerId,
            @Valid @RequestBody NotificationPreferenceCommand command) {
        return ApiResponses.ok("NOTIFICATION_PREFERENCE_UPDATED","인앱 알림 설정을 변경했습니다.",service.updatePreference(customerId,command));
    }
}
