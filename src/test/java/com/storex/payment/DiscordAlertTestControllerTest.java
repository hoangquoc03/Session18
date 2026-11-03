package com.storex.payment;

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
class DiscordAlertTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void emitsTestLogsAndReturnsTheRequestId() throws Exception {
        mockMvc.perform(get("/api/test-discord-error"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.message").value(
                        "Test logs emitted. Only the ERROR event is sent to Discord when a webhook URL is configured."));
    }
}
