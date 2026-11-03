package com.storex.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserTracingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTracingUserAndPreservesUpstreamRequestId() throws Exception {
        mockMvc.perform(get("/api/users/tracing-test")
                        .header("X-Request-Id", "gateway-request-id")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "gateway-request-id"))
                .andExpect(jsonPath("$.userId").value(1001))
                .andExpect(jsonPath("$.username").value("storex-user"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
