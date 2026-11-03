package com.storex.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

@Component
public class GatewayTraceLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayTraceLoggingFilter.class);

    private final Tracer tracer;
    private final Propagator propagator;

    public GatewayTraceLoggingFilter(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER);
        Span activeSpan = tracer.currentSpan();
        boolean createdSpan = activeSpan == null;
        Span gatewaySpan = createdSpan
                ? propagator.extract(
                                exchange.getRequest(),
                                (request, key) -> request.getHeaders().getFirst(key))
                        .name("api-gateway.request")
                        .start()
                : activeSpan;

        ServerWebExchange tracedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    if (requestId != null) {
                        headers.set(GatewayRequestIdFilter.REQUEST_ID_HEADER, requestId);
                    }
                    propagator.inject(gatewaySpan.context(), headers, HttpHeaders::set);
                }))
                .build();

        try (Tracer.SpanInScope ignored = tracer.withSpan(gatewaySpan)) {
            if (requestId != null) {
                MDC.put("requestId", requestId);
            }
            logger.info("Received request from client: {}", exchange.getRequest().getURI().getPath());
            logger.debug("Forwarding request through API Gateway");
        } finally {
            MDC.remove("requestId");
        }
        return chain.filter(tracedExchange)
                .doFinally(signal -> {
                    try (Tracer.SpanInScope ignored = tracer.withSpan(gatewaySpan)) {
                        if (requestId != null) {
                            MDC.put("requestId", requestId);
                        }
                        logger.info("Gateway completed request with status {}", exchange.getResponse().getStatusCode());
                    } finally {
                        MDC.remove("requestId");
                        if (createdSpan) {
                            gatewaySpan.end();
                        }
                    }
                });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
