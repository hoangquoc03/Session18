package com.storex.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class GatewayRequestIdFilterTest {

    @Test
    void replacesDownstreamRequestIdResponseHeaderWithSingleGatewayId() {
        GatewayRequestIdFilter filter = new GatewayRequestIdFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        AtomicReference<String> forwardedRequestId = new AtomicReference<>();

        filter.filter(exchange, updatedExchange -> {
            forwardedRequestId.set(updatedExchange.getRequest().getHeaders()
                    .getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER));
            updatedExchange.getResponse().getHeaders().add(
                    GatewayRequestIdFilter.REQUEST_ID_HEADER,
                    "downstream-request-id");
            return updatedExchange.getResponse().setComplete();
        }).block();

        assertEquals(1, exchange.getResponse().getHeaders()
                .get(GatewayRequestIdFilter.REQUEST_ID_HEADER).size());
        assertEquals(
                exchange.getResponse().getHeaders().getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER),
                forwardedRequestId.get());
    }
}
