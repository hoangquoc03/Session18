package com.storex.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LogRotationTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void emitsTestLogsAndRejectsUnsafeBulkCounts() throws Exception {
        mockMvc.perform(get("/api/test-log-rotation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Test logs emitted at INFO, DEBUG, WARN, and ERROR."));

        mockMvc.perform(get("/api/test-log-rotation/bulk").param("count", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(100))
                .andExpect(jsonPath("$.logged").value(100));

        mockMvc.perform(get("/api/test-log-rotation/bulk").param("count", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maxCount").value(10000));
    }
}
