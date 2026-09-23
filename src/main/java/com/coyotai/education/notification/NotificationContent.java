package com.coyotai.education.notification;

import java.util.List;

/**
 * What to say about an event. {@code parameters} feed the approved WhatsApp template in
 * order; {@code title} and {@code text} are used for in-app messages and the log.
 */
public record NotificationContent(String title, List<String> parameters, String text) {
}
