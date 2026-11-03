package com.storex.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MdcTestService {

    private static final Logger logger = LoggerFactory.getLogger(MdcTestService.class);

    public String processRequest() {
        logger.debug("MDC test service debug information");
        logger.info("Processing request in MDC test service");
        logger.warn("MDC test service warning-level example");
        logger.error("MDC test service error-level example; no exception is thrown");
        return "Request processed; see application logs for the shared request ID.";
    }
}
