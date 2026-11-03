package com.storex.payment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiscordAlertTestController {

    private static final Logger logger = LoggerFactory.getLogger(DiscordAlertTestController.class);

    private final DiscordAlertTestService discordAlertTestService;

    public DiscordAlertTestController(DiscordAlertTestService discordAlertTestService) {
        this.discordAlertTestService = discordAlertTestService;
    }

    @GetMapping("/api/test-discord-error")
    public Map<String, String> testDiscordError() {
        logger.info("Received Discord alert test request");
        String message = discordAlertTestService.emitTestLogs();
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        return Map.of("requestId", requestId, "message", message);
    }
}
