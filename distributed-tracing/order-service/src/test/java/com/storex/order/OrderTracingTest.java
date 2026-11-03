package com.storex.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderTracingTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void returnsOrderResponseWithUserServiceData() {
        UserInformation user = new UserInformation(1001L, "storex-user", "ACTIVE");
        when(orderService.fetchUser()).thenReturn(user);

        OrderTracingResponse response = orderController.testTracing();

        assertEquals("Distributed tracing successful", response.message());
        assertEquals("order-service", response.orderService());
        assertEquals(user, response.user());
        verify(orderService).fetchUser();
    }
}
