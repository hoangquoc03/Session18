package com.storex.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthAndDynamicLogLevelRemainAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        try {
            mockMvc.perform(post("/actuator/loggers/com.storex.payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"configuredLevel\":\"DEBUG\"}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/actuator/loggers/com.storex.payment"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configuredLevel").value("DEBUG"))
                    .andExpect(jsonPath("$.effectiveLevel").value("DEBUG"));

            mockMvc.perform(get("/api/test-mdc"))
                    .andExpect(status().isOk());
        } finally {
            mockMvc.perform(post("/actuator/loggers/com.storex.payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"configuredLevel\":\"INFO\"}"))
                    .andExpect(status().isNoContent());
        }
    }
}
