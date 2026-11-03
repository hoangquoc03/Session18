package com.storex.payment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MdcTestController {

    private static final Logger logger = LoggerFactory.getLogger(MdcTestController.class);

    private final MdcTestService mdcTestService;

    public MdcTestController(MdcTestService mdcTestService) {
        this.mdcTestService = mdcTestService;
    }

    @GetMapping("/api/test-mdc")
    public Map<String, String> testMdc() {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        logger.debug("Received MDC test request");
        logger.info("Starting MDC test request");
        String result = mdcTestService.processRequest();

        return Map.of("requestId", requestId, "message", result);
    }
}
