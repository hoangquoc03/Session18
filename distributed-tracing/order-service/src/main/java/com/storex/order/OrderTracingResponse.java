package com.storex.order;

public record OrderTracingResponse(String message, String orderService, UserInformation user) {
}
