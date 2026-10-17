package dev.labellens.product;

import com.jayway.jsonpath.JsonPath;
import dev.labellens.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the whole chain: HTTP request → controller → service → real PostgreSQL → HTTP response.
 * MockMvc sends requests without starting a real web server (faster).
 */
@SpringBootTest(properties = "labellens.storage.local-dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductApiTest {

    @Autowired
    MockMvc mvc;

    // A few bytes are enough: day 2 checks the declared type, not the image content.
    static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("photos", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void uploadTwoPhotos_createsPendingProduct_thatCanBeReadBack() throws Exception {
        String body = mvc.perform(multipart("/api/products")
                        .file(jpeg("back.jpg"))
                        .file(jpeg("front.jpg")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.photos[0].position").value(1))
                .andExpect(jsonPath("$.photos[1].position").value(2));
    }

    @Test
    void uploadWithNoPhotos_isRejected() throws Exception {
        mvc.perform(multipart("/api/products"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFourPhotos_isRejected() throws Exception {
        mvc.perform(multipart("/api/products")
                        .file(jpeg("1.jpg")).file(jpeg("2.jpg"))
                        .file(jpeg("3.jpg")).file(jpeg("4.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Send at most 3 photos, got 4."));
    }

    @Test
    void uploadPdf_isRejected() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("photos", "label.pdf", "application/pdf", new byte[]{1});
        mvc.perform(multipart("/api/products").file(pdf))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProduct_returns404() throws Exception {
        mvc.perform(get("/api/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returnsAPage() throws Exception {
        mvc.perform(multipart("/api/products").file(jpeg("a.jpg")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/products").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.items").isArray());
    }
}
