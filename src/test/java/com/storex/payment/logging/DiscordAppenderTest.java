package com.storex.payment.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DiscordAppenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onlyAllowsErrorEventsThroughToTheWebhookQueue() {
        assertTrue(DiscordAppender.isErrorEvent(eventAtLevel(Level.ERROR)));
        assertFalse(DiscordAppender.isErrorEvent(eventAtLevel(Level.WARN)));
        assertFalse(DiscordAppender.isErrorEvent(eventAtLevel(Level.INFO)));
        assertFalse(DiscordAppender.isErrorEvent(eventAtLevel(Level.DEBUG)));
    }

    @Test
    void createsDiscordEmbedWithEventMdcAndRedactedSensitiveValues() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.ERROR);
        event.setLoggerName("com.storex.payment.PaymentService");
        event.setMessage(
                "Payment failed for password=super-secret jdbc:postgresql://db-user:db-pass@db.example/orders");
        event.setTimeStamp(1_793_700_000_000L);
        event.setMDCPropertyMap(Map.of("requestId", "test-request-123"));

        DiscordAppender appender = new DiscordAppender();
        JsonNode payload = objectMapper.readTree(appender.createPayload(event));
        JsonNode embed = payload.path("embeds").get(0);
        JsonNode fields = embed.path("fields");

        assertEquals("🚨 APPLICATION ERROR", embed.path("title").asText());
        assertEquals(15_158_332, embed.path("color").asInt());
        assertTrue(embed.path("description").asText().contains("password=[REDACTED]"));
        assertFalse(embed.path("description").asText().contains("super-secret"));
        assertFalse(embed.path("description").asText().contains("db-pass"));
        assertEquals("test-request-123", fieldValue(fields, "Request ID"));
        assertEquals("com.storex.payment.PaymentService", fieldValue(fields, "Logger"));
        assertEquals(List.of(), payload.path("allowed_mentions").path("parse").findValuesAsText(""));
    }

    @Test
    void handlesMissingMessageAndRequestId() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.ERROR);
        event.setLoggerName("com.storex.payment.PaymentService");
        event.setMessage(null);
        event.setTimeStamp(1_793_700_000_000L);
        event.setMDCPropertyMap(Map.of());

        DiscordAppender appender = new DiscordAppender();
        JsonNode embed = objectMapper.readTree(appender.createPayload(event)).path("embeds").get(0);

        assertEquals("(no message)", embed.path("description").asText());
        assertEquals("Not available", fieldValue(embed.path("fields"), "Request ID"));
    }

    private String fieldValue(JsonNode fields, String name) {
        for (JsonNode field : fields) {
            if (name.equals(field.path("name").asText())) {
                return field.path("value").asText();
            }
        }
        throw new AssertionError("Missing Discord embed field: " + name);
    }

    private LoggingEvent eventAtLevel(Level level) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(level);
        return event;
    }
}
