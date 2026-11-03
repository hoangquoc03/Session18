package com.storex.user;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final Tracer tracer;

    public UserService(Tracer tracer) {
        this.tracer = tracer;
    }

    public UserInformation findTracingTestUser() {
        Span span = tracer.nextSpan().name("user.fetch-profile").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            logger.info("Fetching user information");
            logger.debug("Returning in-memory tracing demo user");
            return new UserInformation(1001L, "storex-user", "ACTIVE");
        } finally {
            span.end();
        }
    }
}
