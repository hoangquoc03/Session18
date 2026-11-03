package com.storex.order;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final RestClient userRestClient;
    private final Tracer tracer;

    public OrderService(RestClient userRestClient, Tracer tracer) {
        this.userRestClient = userRestClient;
        this.tracer = tracer;
    }

    public UserInformation fetchUser() {
        Span span = tracer.nextSpan().name("order.fetch-user").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            logger.info("Calling User Service");
            logger.debug("Fetching sample user data");
            UserInformation user = userRestClient.get()
                    .uri("/api/users/tracing-test")
                    .headers(headers -> {
                        String requestId = MDC.get("requestId");
                        if (requestId != null) {
                            headers.set("X-Request-Id", requestId);
                        }
                    })
                    .retrieve()
                    .body(UserInformation.class);
            if (user == null) {
                throw new IllegalStateException("User Service returned an empty response");
            }
            return user;
        } catch (RestClientException exception) {
            logger.error("User Service request failed", exception);
            throw exception;
        } finally {
            span.end();
        }
    }

}
