package com.storex.payment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogTestController {

    private static final Logger logger = LoggerFactory.getLogger(LogTestController.class);

    @GetMapping("/api/test-logs")
    public Map<String, String> testLogs() {
        logger.trace("Dynamic log-level check: TRACE");
        logger.debug("Dynamic log-level check: DEBUG");
        logger.info("Dynamic log-level check: INFO");
        logger.warn("Dynamic log-level check: WARN");
        logger.error("Dynamic log-level check: ERROR");

        return Map.of("message", "Logs emitted at TRACE, DEBUG, INFO, WARN, and ERROR levels.");
    }
}
