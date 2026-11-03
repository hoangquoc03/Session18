package com.storex.gateway;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestIdFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });
        ServerWebExchange updatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(REQUEST_ID_HEADER, requestId)))
                .build();
        return chain.filter(updatedExchange);
    }
}
