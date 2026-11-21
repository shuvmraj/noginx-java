package com.noginx.controller;

import com.noginx.model.FeatureRecord;
import com.noginx.service.PredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PredictionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PredictionService predictionService = new PredictionService(
                "target/test-model.ser",
                "data/features.csv");
        predictionService.initialize();
        PredictionController controller = new PredictionController(predictionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void healthEndpointReturnsRunningMessage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("server is running live"));
    }

    @Test
    void predictAcceptsBatchAndPreservesResponseShape() throws Exception {
        String body = """
                {"features":[
                  {"status":304,"size":0,"method":1,"path":0,"user_agent":54,"hour_of_day":8},
                  {"status":200,"size":490,"method":0,"path":0,"user_agent":40,"hour_of_day":8}
                ]}
                """;

        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].anomaly").isBoolean())
                .andExpect(jsonPath("$[1].anomaly").isBoolean());
    }

    @Test
    void predictOneAcceptsValidFeature() throws Exception {
        String body = """
                {"feature":{"status":304,"size":0,"method":1,"path":0,"user_agent":54,"hour_of_day":8}}
                """;

        mockMvc.perform(post("/predict_one").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].anomaly").isBoolean());
    }

    @Test
    void emptyBatchIsRejected() throws Exception {
        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON).content("{\"features\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void missingFeatureFieldIsRejected() throws Exception {
        mockMvc.perform(post("/predict_one").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feature\":{\"status\":304,\"size\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/predict").contentType(MediaType.APPLICATION_JSON).content("{\"features\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
