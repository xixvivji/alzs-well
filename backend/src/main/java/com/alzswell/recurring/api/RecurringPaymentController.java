package com.alzswell.recurring.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.common.security.AuditActor;
import com.alzswell.recurring.api.RecurringPaymentRequests.ReminderSettingsCommand;
import com.alzswell.recurring.api.RecurringPaymentResponses.*;
import com.alzswell.recurring.application.RecurringPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class RecurringPaymentController {
    private static final String CUSTOMER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_:-]{2,79}$";
    private final RecurringPaymentService service;

    public RecurringPaymentController(RecurringPaymentService service) { this.service = service; }

    @GetMapping("/customers/{customerId}/recurring-payments")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('RECURRING_PAYMENT_READ')")
    public ResponseEntity<ApiResponse<PaymentList>> payments(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("RECURRING_PAYMENTS_RETRIEVED", "정기납부·구독 목록을 조회했습니다.",
                service.payments(customerId));
    }

    @GetMapping("/recurring-payments/{recurringPaymentId}")
    @PreAuthorize("hasAuthority('RECURRING_PAYMENT_READ')")
    public ResponseEntity<ApiResponse<PaymentDetail>> payment(
            @PathVariable UUID recurringPaymentId, Authentication authentication) {
        return ApiResponses.ok("RECURRING_PAYMENT_RETRIEVED", "정기납부 상세를 조회했습니다.",
                service.payment(authentication.getName(), recurringPaymentId));
    }

    @GetMapping("/customers/{customerId}/recurring-payments/calendar")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('RECURRING_PAYMENT_READ')")
    public ResponseEntity<ApiResponse<Calendar>> calendar(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponses.ok("RECURRING_PAYMENT_CALENDAR_RETRIEVED", "예상 납부 일정을 조회했습니다.",
                service.calendar(customerId, from, to));
    }

    @GetMapping("/customers/{customerId}/recurring-payments/missed")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('RECURRING_PAYMENT_READ')")
    public ResponseEntity<ApiResponse<MissedList>> missed(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("MISSED_RECURRING_PAYMENTS_RETRIEVED", "미발생 정기납부 후보를 조회했습니다.",
                service.missed(customerId));
    }

    @GetMapping("/customers/{customerId}/recurring-payments/duplicates")
    @PreAuthorize("#customerId == authentication.name and hasAuthority('RECURRING_PAYMENT_READ')")
    public ResponseEntity<ApiResponse<DuplicateList>> duplicates(
            @PathVariable @Pattern(regexp = CUSTOMER_ID_PATTERN) String customerId) {
        return ApiResponses.ok("DUPLICATE_RECURRING_PAYMENTS_RETRIEVED", "중복 납부 후보를 조회했습니다.",
                service.duplicates(customerId));
    }

    @GetMapping("/recurring-payments/{recurringPaymentId}/occurrences")
    @PreAuthorize("hasAuthority('RECURRING_PAYMENT_READ')")
    public ResponseEntity<ApiResponse<OccurrenceList>> occurrences(
            @PathVariable UUID recurringPaymentId, Authentication authentication) {
        return ApiResponses.ok("RECURRING_PAYMENT_OCCURRENCES_RETRIEVED", "정기납부 발생 이력을 조회했습니다.",
                service.occurrences(authentication.getName(), recurringPaymentId));
    }

    @PutMapping("/recurring-payments/{recurringPaymentId}/reminder-settings")
    @PreAuthorize("hasAuthority('RECURRING_PAYMENT_WRITE')")
    public ResponseEntity<ApiResponse<PaymentDetail>> reminderSettings(
            @PathVariable UUID recurringPaymentId,
            @Valid @RequestBody ReminderSettingsCommand command,
            Authentication authentication) {
        return ApiResponses.ok("RECURRING_PAYMENT_REMINDER_UPDATED", "인앱 납부 확인 알림 설정을 변경했습니다.",
                service.updateReminder(authentication.getName(), recurringPaymentId, command,
                        AuditActor.from(authentication)));
    }
}
