package com.storex.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayContextTest {

    @Autowired
    private Environment environment;

    @Test
    void startsAsReactiveGatewayWithDedicatedServiceName() {
        assertEquals("api-gateway", environment.getProperty("spring.application.name"));
        assertEquals("w3c", environment.getProperty("management.tracing.propagation.type"));
        assertEquals("auto", environment.getProperty("spring.reactor.context-propagation"));
    }
}
