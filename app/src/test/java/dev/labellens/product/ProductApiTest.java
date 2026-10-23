package dev.labellens.product;

import com.jayway.jsonpath.JsonPath;
import dev.labellens.TestcontainersConfiguration;
import dev.labellens.ai.AiCallRepository;
import dev.labellens.ai.FakeAiConfiguration;
import dev.labellens.ai.FakeVisionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the whole chain: HTTP request → controller → service → fake AI → real PostgreSQL → HTTP response.
 * MockMvc sends requests without starting a real web server (faster).
 * The AI is FakeVisionModel: these tests never call Azure.
 */
@SpringBootTest(properties = "labellens.storage.local-dir=target/test-uploads")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class ProductApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeVisionModel fakeModel;

    @Autowired
    AiCallRepository aiCalls;

    @BeforeEach
    void resetFakeModel() {
        fakeModel.reset();
    }

    // A few bytes are enough: the fake model doesn't look at the image.
    static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("photos", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void upload_isReadByTheAi_andTheReadingCanBeFetchedBack() throws Exception {
        String body = mvc.perform(multipart("/api/products")
                        .file(jpeg("back.jpg"))
                        .file(jpeg("front.jpg")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.reading.energyKj").value(1088.4))
                .andExpect(jsonPath("$.reading.sugars").value(60.0))
                .andExpect(jsonPath("$.reading.ingredientsLanguage").value("fr"))
                .andExpect(jsonPath("$.model").value("fake-model"))
                // Jam: energy 3 + sugars 15 + saturated fat 0 + salt 0 = 18 negative points;
                // protein not counted (18 >= 11); no fibre; fruit/veg assumed 0 → score 18 → D.
                .andExpect(jsonPath("$.nutriScore.scored").value(true))
                .andExpect(jsonPath("$.nutriScore.grade").value("D"))
                .andExpect(jsonPath("$.nutriScore.score").value(18))
                .andExpect(jsonPath("$.nutriScore.points.sugars").value(15))
                .andExpect(jsonPath("$.nutriScore.points.proteinCounted").value(false))
                .andExpect(jsonPath("$.nutriScore.fruitVegAssumedZero").value(true))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.nutriScore.grade").value("D"))
                .andExpect(jsonPath("$.reading.carbohydrates").value(62.9))
                .andExpect(jsonPath("$.photos[0].position").value(1))
                .andExpect(jsonPath("$.photos[1].position").value(2));

        // Every AI call is recorded, with its token counts.
        var calls = aiCalls.findByProductId(UUID.fromString(id));
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().isSuccess()).isTrue();
        assertThat(calls.getFirst().getInputTokens()).isEqualTo(1200);
    }

    @Test
    void drink_isDone_butNotScored() throws Exception {
        fakeModel.reply(FakeVisionModel.JAM_JSON.replace("\"type\": \"GENERAL\"", "\"type\": \"DRINK\""));

        mvc.perform(multipart("/api/products").file(jpeg("milk.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.nutriScore.scored").value(false))
                .andExpect(jsonPath("$.nutriScore.notScoredReason").isNotEmpty())
                .andExpect(jsonPath("$.nutriScore.grade").doesNotExist());
    }

    @Test
    void unusableAiAnswer_marksProductFailed_withAReason() throws Exception {
        fakeModel.reply("I cannot read this label.", "Still cannot read it.");

        String body = mvc.perform(multipart("/api/products").file(jpeg("blurry.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").exists())
                .andExpect(jsonPath("$.reading").doesNotExist())
                .andExpect(jsonPath("$.nutriScore").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        assertThat(aiCalls.findByProductId(UUID.fromString(id))).hasSize(2);
    }

    @Test
    void aiUnavailable_marksProductFailed_withoutCrashing() throws Exception {
        fakeModel.failWith(new IllegalStateException("Connection refused"));

        mvc.perform(multipart("/api/products").file(jpeg("a.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("Connection refused")));
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
