package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:postbubi-workspace-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsRequestInsideFolderAndDeletesItWithFolder() throws Exception {
        long collectionId = postJson("/api/collections", """
                {
                  "name": "整合測試 Collection",
                  "description": "Workspace API"
                }
                """).path("id").asLong();

        long folderId = postJson("/api/folders", """
                {
                  "collectionId": %d,
                  "parentFolderId": null,
                  "name": "HTTP APIs",
                  "sortOrder": 1
                }
                """.formatted(collectionId)).path("id").asLong();

        long requestId = postJson("/api/requests", """
                {
                  "collectionId": %d,
                  "folderId": %d,
                  "type": "HTTP",
                  "name": "健康檢查",
                  "sortOrder": 1,
                  "payloadJson": "{\\"requestType\\":\\"HTTP\\",\\"method\\":\\"GET\\"}"
                }
                """.formatted(collectionId, folderId)).path("id").asLong();

        mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(collectionId))
                .andExpect(jsonPath("$[0].folders[0].id").value(folderId))
                .andExpect(jsonPath("$[0].requests[0].id").value(requestId))
                .andExpect(jsonPath("$[0].requests[0].folderId").value(folderId));

        mockMvc.perform(delete("/api/folders/{id}", folderId))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode collection = objectMapper.readTree(body).get(0);
        assertThat(collection.path("folders")).isEmpty();
        assertThat(collection.path("requests")).isEmpty();
    }

    @Test
    void returnsStructuredErrorForInvalidInputAndMissingRequest() throws Exception {
        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_FIELD"))
                .andExpect(jsonPath("$.message").value("Collection 名稱不可空白。"))
                .andExpect(jsonPath("$.details.field").value("name"));

        mockMvc.perform(get("/api/requests/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("找不到指定的 Request。"))
                .andExpect(jsonPath("$.details.id").value(999));
    }

    private JsonNode postJson(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
