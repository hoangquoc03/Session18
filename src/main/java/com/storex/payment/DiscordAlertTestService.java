package com.storex.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiscordAlertTestService {

    private static final Logger logger = LoggerFactory.getLogger(DiscordAlertTestService.class);

    public String emitTestLogs() {
        logger.info("Testing Discord notification");
        logger.warn("This warning must remain on console and must not alert Discord");
        logger.error("TEST DISCORD WEBHOOK ALERT", new IllegalStateException("Simulated alert-test exception"));
        return "Test logs emitted. Only the ERROR event is sent to Discord when a webhook URL is configured.";
    }
}
