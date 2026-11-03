package com.storex.payment.logging;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DiscordAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_DESCRIPTION_LENGTH = 3_500;
    private static final int MAX_FIELD_LENGTH = 1_000;
    private static final int MAX_QUEUE_SIZE = 128;
    private static final int ERROR_COLOR = 15_158_332;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?(?:password|passwd|pwd|token|secret|authorization|api[_-]?key|client[_-]?secret|credential(?:s)?)[\"']?\\s*[:=]\\s*[\"']?)([^\\s,\"';}]+)");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*");
    private static final Pattern BASIC_TOKEN = Pattern.compile("(?i)(Basic\\s+)[A-Za-z0-9+/=]+");
    private static final Pattern CONNECTION_URL = Pattern.compile(
            "(?i)(?:jdbc:[^\\s,;]+|(?:postgres(?:ql)?|mysql|mariadb|mongodb(?:\\+srv)?|redis)://[^\\s,;]+)");

    private final AtomicBoolean deliveryWarningLogged = new AtomicBoolean();
    private final AtomicBoolean queueWarningLogged = new AtomicBoolean();
    private volatile String webhookUrl;
    private volatile URI webhookUri;
    private volatile HttpClient httpClient;
    private volatile ThreadPoolExecutor executor;

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    }

    @Override
    public synchronized void start() {
        if (isStarted()) {
            return;
        }

        if (webhookUrl == null || webhookUrl.isBlank()) {
            System.err.println("[DiscordAppender] DISCORD_WEBHOOK_URL is not configured; Discord alerts are disabled.");
            super.start();
            return;
        }

        final URI configuredUri;
        try {
            configuredUri = URI.create(webhookUrl);
        } catch (IllegalArgumentException exception) {
            System.err.println("[DiscordAppender] Webhook URL is invalid; Discord alerts are disabled.");
            super.start();
            return;
        }

        if (!"https".equalsIgnoreCase(configuredUri.getScheme()) || configuredUri.getHost() == null) {
            System.err.println("[DiscordAppender] Webhook URL must be a valid HTTPS URL; Discord alerts are disabled.");
            super.start();
            return;
        }

        try {
            webhookUri = configuredUri;
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
            executor = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_QUEUE_SIZE),
                    new DiscordThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());
            super.start();
        } catch (RuntimeException exception) {
            ThreadPoolExecutor currentExecutor = executor;
            if (currentExecutor != null) {
                currentExecutor.shutdownNow();
            }
            HttpClient currentClient = httpClient;
            if (currentClient != null) {
                try {
                    currentClient.close();
                } catch (RuntimeException closeException) {
                    System.err.println("[DiscordAppender] HTTP client shutdown encountered an error.");
                }
            }
            executor = null;
            httpClient = null;
            webhookUri = null;
            System.err.println("[DiscordAppender] Webhook sender could not be initialized; Discord alerts are disabled.");
            super.start();
        }
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isErrorEvent(eventObject)) {
            return;
        }

        URI target = webhookUri;
        ThreadPoolExecutor currentExecutor = executor;
        if (target == null || currentExecutor == null || currentExecutor.isShutdown()) {
            return;
        }

        final String payload;
        try {
            payload = createPayload(eventObject);
        } catch (JsonProcessingException exception) {
            reportDeliveryProblem("could not serialize an error event");
            return;
        }

        try {
            currentExecutor.execute(() -> sendWebhook(target, payload));
        } catch (RejectedExecutionException exception) {
            if (queueWarningLogged.compareAndSet(false, true)) {
                System.err.println("[DiscordAppender] Alert queue is full or shutting down; an alert was dropped.");
            }
        }
    }

    static boolean isErrorEvent(ILoggingEvent eventObject) {
        return eventObject != null && eventObject.getLevel() != null
                && eventObject.getLevel().toInt() >= Level.ERROR_INT;
    }

    String createPayload(ILoggingEvent eventObject) throws JsonProcessingException {
        String message = eventObject.getFormattedMessage();
        String description = sanitize(message == null || message.isBlank() ? "(no message)" : message);

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("Level", eventObject.getLevel().toString(), true));
        fields.add(field("Logger", eventObject.getLoggerName(), false));

        Map<String, String> mdcProperties = eventObject.getMDCPropertyMap();
        String requestId = mdcProperties == null ? null : mdcProperties.get("requestId");
        String traceId = mdcProperties == null ? null : mdcProperties.get("traceId");
        String spanId = mdcProperties == null ? null : mdcProperties.get("spanId");
        fields.add(field("Request ID", requestId == null || requestId.isBlank() ? "Not available" : requestId, false));
        if (traceId != null && !traceId.isBlank()) {
            fields.add(field("Trace ID", traceId, false));
        }
        if (spanId != null && !spanId.isBlank()) {
            fields.add(field("Span ID", spanId, false));
        }

        if (eventObject.getThrowableProxy() != null) {
            String stackTrace = sanitize(ThrowableProxyUtil.asString(eventObject.getThrowableProxy()));
            fields.add(field("Exception", stackTrace, false));
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "🚨 APPLICATION ERROR");
        embed.put("description", truncate(description, MAX_DESCRIPTION_LENGTH));
        embed.put("color", ERROR_COLOR);
        embed.put("fields", fields);
        embed.put("timestamp", Instant.ofEpochMilli(eventObject.getTimeStamp()).toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("embeds", List.of(embed));
        payload.put("allowed_mentions", Map.of("parse", List.of()));
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private Map<String, Object> field(String name, String value, boolean inline) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", truncate(name == null || name.isBlank() ? "Unknown" : name, 256));
        String safeValue = value == null || value.isBlank() ? "Not available" : value;
        field.put("value", truncate(sanitize(safeValue), MAX_FIELD_LENGTH));
        field.put("inline", inline);
        return field;
    }

    private String sanitize(String value) {
        String sanitized = BEARER_TOKEN.matcher(value).replaceAll("$1[REDACTED]");
        sanitized = BASIC_TOKEN.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1[REDACTED]");
        return CONNECTION_URL.matcher(sanitized).replaceAll("[REDACTED CONNECTION URL]");
    }

    private String truncate(String value, int maxCodePoints) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxCodePoints) {
            return value;
        }

        int end = value.offsetByCodePoints(0, maxCodePoints - 1);
        return value.substring(0, end) + "…";
    }

    private void sendWebhook(URI target, String payload) {
        HttpClient client = httpClient;
        if (client == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                reportDeliveryProblem("webhook returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            reportDeliveryProblem("webhook request was interrupted");
        } catch (IOException | RuntimeException exception) {
            reportDeliveryProblem("webhook request failed (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private void reportDeliveryProblem(String reason) {
        if (deliveryWarningLogged.compareAndSet(false, true)) {
            System.err.println("[DiscordAppender] Discord alert delivery " + reason + ".");
        }
    }

    @Override
    public synchronized void stop() {
        ThreadPoolExecutor currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdown();
            try {
                if (!currentExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    currentExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                currentExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        HttpClient client = httpClient;
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException exception) {
                System.err.println("[DiscordAppender] HTTP client shutdown encountered an error.");
            }
        }

        executor = null;
        httpClient = null;
        webhookUri = null;
        super.stop();
    }

    private static final class DiscordThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "discord-log-appender");
            thread.setDaemon(true);
            return thread;
        }
    }
}
