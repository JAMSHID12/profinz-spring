package com.coyotai.education.notification;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.PageResponse;
import com.coyotai.education.notification.NotificationDtos.LogEntry;
import com.coyotai.education.notification.NotificationDtos.TestMessageRequest;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import com.coyotai.education.whatsapp.WhatsAppService;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Message log, manual retry and WhatsApp connectivity test. */
@RestController
@RequestMapping("/api/whatsapp")
@RequiresModule(ModuleCode.NOTIFICATIONS)
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMessageFactory messageFactory;
    private final WhatsAppService whatsAppService;
    private final WhatsAppProperties whatsAppProperties;

    public NotificationController(NotificationService notificationService, NotificationMessageFactory messageFactory,
                                  WhatsAppService whatsAppService, WhatsAppProperties whatsAppProperties) {
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.whatsAppService = whatsAppService;
        this.whatsAppProperties = whatsAppProperties;
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<PageResponse<LogEntry>> messages(@RequestParam(required = false) Notification.Status status,
                                                        @RequestParam(required = false) NotificationChannel channel,
                                                        @RequestParam(required = false) NotificationEvent event,
                                                        @RequestParam(required = false) Long studentId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(notificationService.search(status, channel, event, studentId,
                PageRequest.of(page, Math.min(size, 200))));
    }

    @PostMapping("/messages/{id}/retry")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public ApiResponse<LogEntry> retry(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.retry(id), "Message queued for another attempt");
    }

    /** Sends immediately so the administrator sees the outcome - the one bypass of the queue. */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public ApiResponse<WhatsAppSendResult> test(@Valid @RequestBody TestMessageRequest request) {
        if (whatsAppProperties.enabled() && whatsAppProperties.usesWabi()) {
            if (request.parameters() == null || request.parameters().isEmpty())
                throw new BusinessRuleException("WABI test requires template parameters in their approved order");
            NotificationEvent event = whatsAppProperties.wabi().event();
            NotificationPayload payload = new NotificationPayload(whatsAppProperties.templateName(event),
                    whatsAppProperties.defaultLanguage(), request.parameters(), "WABI template test");
            WhatsAppSendResult result = whatsAppService.send(request.phoneNumber(), payload, event,
                    "education-test-" + java.util.UUID.randomUUID());
            if (!result.success()) throw new BusinessRuleException("Test message failed: " + result.errorMessage());
            return ApiResponse.ok(result, "Test message accepted by WABI");
        }
        String text = (request.message() == null || request.message().isBlank())
                ? "This is a test message from your education management system." : request.message();
        NotificationContent content = messageFactory.general("Parent", text);
        NotificationPayload payload = new NotificationPayload(
                whatsAppProperties.templateName(NotificationEvent.GENERAL), whatsAppProperties.defaultLanguage(),
                content.parameters(), content.text());
        WhatsAppSendResult result = whatsAppService.send(request.phoneNumber(), payload);
        if (!result.success()) {
            throw new BusinessRuleException("Test message failed: " + result.errorMessage());
        }
        return ApiResponse.ok(result, "Test message sent in " + whatsAppService.mode() + " mode");
    }
}
