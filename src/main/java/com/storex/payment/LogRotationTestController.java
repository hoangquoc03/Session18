package com.storex.payment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogRotationTestController {

    private static final Logger logger = LoggerFactory.getLogger(LogRotationTestController.class);
    private static final int MAX_BULK_COUNT = 10_000;
    private static final String BULK_LOG_PAYLOAD = "rotation-test-payload-".repeat(12);

    @GetMapping("/api/test-log-rotation")
    public Map<String, String> testLogRotation() {
        logger.info("Testing log rotation with INFO");
        logger.debug("Testing log rotation with DEBUG");
        logger.warn("Testing log rotation with WARN");
        logger.error("Testing log rotation with ERROR");
        return Map.of("message", "Test logs emitted at INFO, DEBUG, WARN, and ERROR.");
    }

    @GetMapping("/api/test-log-rotation/bulk")
    public ResponseEntity<Map<String, Object>> writeBulkLogs(
            @RequestParam(defaultValue = "1000") int count
    ) {
        if (count < 1 || count > MAX_BULK_COUNT) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "count must be between 1 and " + MAX_BULK_COUNT,
                    "maxCount", MAX_BULK_COUNT));
        }

        for (int index = 1; index <= count; index++) {
            logger.info("Log rotation bulk test entry {}/{} payload={}", index, count, BULK_LOG_PAYLOAD);
        }

        return ResponseEntity.ok(Map.of(
                "requested", count,
                "logged", count,
                "message", "Bulk log entries written."));
    }
}
