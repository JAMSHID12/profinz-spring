package com.coyotai.education.whatsapp;

import com.coyotai.education.notification.NotificationEvent;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WabiWhatsAppServiceTest {
    private static final String KEY = "test-only-wabi-key";
    private static final String URL = "https://wabi.example/api/v1/integrations/messages";
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    private final WhatsAppProperties properties = new WhatsAppProperties(true, WhatsAppProperties.Provider.WABI,
            new WhatsAppProperties.Wabi("https://wabi.example", KEY, NotificationEvent.STUDENT_LATE, "parent-opt-in"),
            "en", 10, 20,
            Map.of(NotificationEvent.STUDENT_LATE,"student_partial_attendance_v2"), new WhatsAppProperties.Queue(true,30,20,3,5));
    private final WabiWhatsAppService sender = new WabiWhatsAppService(client,properties);
    private final NotificationPayload payload = new NotificationPayload("student_partial_attendance_v2","en",
            List.of("Parent","Student","Class A","22 Sep 2026","PROFINZ"),"preview");

    @Test void sendsVerifiedContractAndReusesExternalIdAfterFailure() {
        for (int i=0;i<2;i++) {
            var expected=server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization","Bearer " + KEY))
                    .andExpect(content().json("""
                    {"external_id":"education-1-notification-7","to":"+919876543210",
                     "contact_name":"Parent","parameters":["Parent","Student","Class A","22 Sep 2026","PROFINZ"],
                     "opt_in_source":"parent-opt-in"}
                    """,true));
            if(i==0) expected.andRespond(withServerError());
            else expected.andRespond(withSuccess("{\"external_id\":\"education-1-notification-7\",\"status\":\"sent\",\"wamid\":\"wamid.test\"}",MediaType.APPLICATION_JSON));
        }
        assertThat(sender.send("+919876543210",payload,NotificationEvent.STUDENT_LATE,"education-1-notification-7").success()).isFalse();
        var result=sender.send("+919876543210",payload,NotificationEvent.STUDENT_LATE,"education-1-notification-7");
        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("wamid.test");
        server.verify();
    }

    @Test void neverSendsAnotherEventOrOldTemplateWithLateKey() {
        assertThat(sender.send("+919876543210",payload,NotificationEvent.STUDENT_ABSENT,"1").success()).isFalse();
        assertThat(sender.send("+919876543210",new NotificationPayload("old_template","en",payload.parameters(),""),NotificationEvent.STUDENT_LATE,"1").success()).isFalse();
        assertThat(properties.supportsEvent(NotificationEvent.STUDENT_ABSENT)).isFalse();
        server.verify();
    }

    @Test void rejectsFailureEvenWhenHttpResponseIsSuccessfulAndRedactsKey() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"external_id\":\"id\",\"status\":\"failed\",\"error\":\"bad " + KEY + "\"}",MediaType.APPLICATION_JSON));
        var result=sender.send("+919876543210",payload,NotificationEvent.STUDENT_LATE,"id");
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("[redacted]").doesNotContain(KEY);
        server.verify();
    }

    @Test void rejectsMalformedAcknowledgement() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThat(sender.send("+919876543210",payload,NotificationEvent.STUDENT_LATE,"id").success()).isFalse();
        server.verify();
    }

    @Test void rejectsWrongParameterCountWithoutSending() {
        var wrong = new NotificationPayload(payload.templateName(), "en", List.of("Parent"), "");
        assertThat(sender.send("+919876543210",wrong,NotificationEvent.STUDENT_LATE,"id").success()).isFalse();
        server.verify();
    }

    @Test void reportsConflictWithoutChangingExternalIdOrResending() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT));
        var result=sender.send("+919876543210",payload,NotificationEvent.STUDENT_LATE,"id");
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("409", "different content");
        server.verify();
    }

    @Test void choosesWabiWithoutMetaCredentials() {
        assertThat(properties.isLive()).isTrue();
        assertThat(new WhatsAppConfig().whatsAppService(client,properties).mode()).isEqualTo("WABI");
    }
}
