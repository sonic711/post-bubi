package com.postbubi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:postbubi-environment-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EnvironmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void managesEnvironmentVariablesAndRejectsInvalidKeys() throws Exception {
        String createBody = """
                {
                  "name": "SIT",
                  "variables": [
                    {"key": "baseUrl", "value": "https://sit.example.internal"},
                    {"key": "token", "value": "secret"}
                  ]
                }
                """;

        String response = mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("SIT"))
                .andExpect(jsonPath("$.variables[0].key").value("baseUrl"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asLong();

        mockMvc.perform(put("/api/environments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "UAT",
                                  "variables": [{"key": "baseUrl", "value": "https://uat.example.internal"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UAT"))
                .andExpect(jsonPath("$.variables").isArray())
                .andExpect(jsonPath("$.variables.length()").value(1));

        mockMvc.perform(post("/api/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid",
                                  "variables": [{"key": "1invalid", "value": "x"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_VARIABLE_KEY_INVALID"));

        mockMvc.perform(get("/api/environments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("UAT"));

        mockMvc.perform(delete("/api/environments/{id}", id))
                .andExpect(status().isNoContent());
    }
}
