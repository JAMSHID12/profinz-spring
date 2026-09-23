package com.coyotai.education.notification;

import com.coyotai.education.util.JsonUtils;
import com.coyotai.education.whatsapp.WhatsAppService;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The channel implementations available today. */
@Configuration
public class NotificationSenders {

    /** WhatsApp via the WABI (or the mock sender when WHATSAPP_ENABLED=false). */
    @Bean
    public NotificationSender whatsAppSender(WhatsAppService whatsAppService) {
        return new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.WHATSAPP;
            }

            @Override
            public WhatsAppSendResult send(Notification notification) {
                NotificationPayload payload = JsonUtils.fromJson(notification.getMessagePayload(), NotificationPayload.class);
                return whatsAppService.send(notification.getDestination(), payload, notification.getEventType(),
                        "education-" + notification.getClientId() + "-notification-" + notification.getId());
            }
        };
    }

    /** In-app messages are stored rows; "sending" just makes them visible in the inbox. */
    @Bean
    public NotificationSender inAppSender() {
        return new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.IN_APP;
            }

            @Override
            public WhatsAppSendResult send(Notification notification) {
                return WhatsAppSendResult.success("in-app-" + notification.getId());
            }
        };
    }
}
