package com.coyotai.education.notification;

import com.coyotai.education.whatsapp.dto.WhatsAppSendResult;

/** Delivers one queued message on one channel. Add a provider by adding an implementation. */
public interface NotificationSender {

    NotificationChannel channel();

    WhatsAppSendResult send(Notification notification);
}
