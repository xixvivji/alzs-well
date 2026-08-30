package com.alzswell.inbox.api;

import com.alzswell.common.api.*;
import com.alzswell.inbox.api.InboxRequests.NotificationPreviewCommand;
import com.alzswell.inbox.api.InboxResponses.NotificationPreview;
import com.alzswell.inbox.application.OperationalInboxService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/notification-previews")
public class NotificationPreviewController {
    private final OperationalInboxService service;
    public NotificationPreviewController(OperationalInboxService service) { this.service=service; }
    @PostMapping @PreAuthorize("hasAuthority('NOTIFICATION_PREVIEW')")
    public ResponseEntity<ApiResponse<NotificationPreview>> preview(@Valid @RequestBody NotificationPreviewCommand command) {
        return ApiResponses.ok("NOTIFICATION_PREVIEW_CREATED","외부 발송 없는 알림 문구를 생성했습니다.",service.preview(command));
    }
}
