package com.storex.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/api/orders/test-tracing")
    public OrderTracingResponse testTracing() {
        logger.info("Received order tracing request");
        logger.debug("Starting order processing");
        UserInformation user = orderService.fetchUser();
        logger.info("Received user information for order");
        return new OrderTracingResponse("Distributed tracing successful", "order-service", user);
    }
}
