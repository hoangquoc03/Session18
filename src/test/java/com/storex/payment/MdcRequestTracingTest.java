package com.storex.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MdcRequestTracingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void assignsANewRequestIdPerRequestAndClearsMdcAfterward() throws Exception {
        assertNull(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));

        MvcResult firstResult = mockMvc.perform(get("/api/test-mdc")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "client-supplied-id"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();

        String firstRequestId = firstResult.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertTrue(isUuid(firstRequestId));
        assertNotEquals("client-supplied-id", firstRequestId);
        assertEquals(firstRequestId, jsonRequestId(firstResult));
        assertNull(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));

        MvcResult secondResult = mockMvc.perform(get("/api/test-mdc"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();

        String secondRequestId = secondResult.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertTrue(isUuid(secondRequestId));
        assertNotEquals(firstRequestId, secondRequestId);
        assertEquals(secondRequestId, jsonRequestId(secondResult));
        assertNull(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));
    }

    private String jsonRequestId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
    }

    private boolean isUuid(String value) {
        if (value == null) {
            return false;
        }

        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
