package dev.labellens.product;

import com.jayway.jsonpath.JsonPath;
import dev.labellens.TestcontainersConfiguration;
import dev.labellens.ai.AiCallRepository;
import dev.labellens.ai.FakeAiConfiguration;
import dev.labellens.ai.FakeTextModel;
import dev.labellens.ai.FakeVisionModel;
import dev.labellens.allergens.AllergenDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    FakeTextModel fakeTextModel;

    @Autowired
    AiCallRepository aiCalls;

    /** A spy: the real detector, except in the test that makes it throw (reset after each test). */
    @MockitoSpyBean
    AllergenDetector allergenDetector;

    @BeforeEach
    void resetFakeModel() {
        fakeModel.reset();
        fakeTextModel.reset();
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
                // Jam: no allergens, checked by the word list AND the AI.
                .andExpect(jsonPath("$.allergens.ingredientsRead").value(true))
                .andExpect(jsonPath("$.allergens.contains.length()").value(0))
                .andExpect(jsonPath("$.allergens.aiChecked").value(true))
                .andExpect(jsonPath("$.allergens.aiModel").value("fake-text-model"))
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

        // Every AI call is recorded, with its token counts: one to read, one for the allergens.
        var calls = aiCalls.findByProductId(UUID.fromString(id));
        assertThat(calls).hasSize(2);
        assertThat(calls).allMatch(c -> c.isSuccess());
        assertThat(calls).extracting(c -> c.getInputTokens()).containsExactlyInAnyOrder(1200, 200);
    }

    @Test
    void allergens_containsAndMayContain_areFoundAndStored() throws Exception {
        fakeModel.reply(FakeVisionModel.JAM_JSON
                .replace("Fraises, Sucre,", "Lait entier, Fraises, Sucre,")
                .replace("\"may_contain_text\": null", "\"may_contain_text\": \"Peut contenir des traces de noisettes.\""));
        fakeTextModel.reply("{\"allergens\": [\"en:gluten\", \"en:milk\"]}");

        String body = mvc.perform(multipart("/api/products").file(jpeg("a.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.allergens.contains[0]").value("en:gluten"))   // sorted
                .andExpect(jsonPath("$.allergens.contains[1]").value("en:milk"))
                .andExpect(jsonPath("$.allergens.foundByWordList[0]").value("en:milk"))
                .andExpect(jsonPath("$.allergens.mayContain[0]").value("en:nuts"))
                .andExpect(jsonPath("$.reading.mayContainText").value("Peut contenir des traces de noisettes."))
                .andReturn().getResponse().getContentAsString();

        // Read back from the database, not just the in-memory result.
        mvc.perform(get("/api/products/{id}", (String) JsonPath.read(body, "$.id")))
                .andExpect(jsonPath("$.allergens.contains.length()").value(2))
                .andExpect(jsonPath("$.allergens.mayContain[0]").value("en:nuts"));
    }

    @Test
    void allergenAiDown_productIsStillDone_withTheWordListResult() throws Exception {
        fakeModel.reply(FakeVisionModel.JAM_JSON.replace("Fraises, Sucre,", "Lait entier, Fraises, Sucre,"));
        fakeTextModel.failWith(new IllegalStateException("Connection refused"));

        mvc.perform(multipart("/api/products").file(jpeg("a.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.nutriScore.grade").value("D"))
                .andExpect(jsonPath("$.allergens.contains[0]").value("en:milk"))
                .andExpect(jsonPath("$.allergens.aiChecked").value(false))
                .andExpect(jsonPath("$.allergens.foundByAi").doesNotExist())
                .andExpect(jsonPath("$.allergens.aiProblem").value(org.hamcrest.Matchers.containsString("Connection refused")));
    }

    @Test
    void noIngredientsRead_isNotReportedAsNoAllergens() throws Exception {
        fakeModel.reply(FakeVisionModel.JAM_JSON.replace(
                "\"Fraises, Sucre, gélifiant: Pectine de fruits et correcteur d'acidité: acide citrique.\"", "null"));

        mvc.perform(multipart("/api/products").file(jpeg("a.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allergens.ingredientsRead").value(false))
                .andExpect(jsonPath("$.allergens.aiChecked").value(false));
        assertThat(fakeTextModel.receivedTexts()).isEmpty();
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
                .andExpect(jsonPath("$.allergens").doesNotExist())
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
    void unexpectedErrorAfterTheReading_marksProductFailed_neverLeavesItPending() throws Exception {
        doThrow(new IllegalStateException("bug in the allergen step")).when(allergenDetector).detect(any(), any(), any());

        String body = mvc.perform(multipart("/api/products").file(jpeg("a.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("IllegalStateException")))
                // The internal message stays in the server log, not in the API.
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("bug in the allergen step"))))
                .andExpect(jsonPath("$.reading").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Nothing half-saved: step 4 never ran, so no reading row either.
        mvc.perform(get("/api/products/{id}", (String) JsonPath.read(body, "$.id")))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.nutriScore").doesNotExist());
    }

    @Test
    void aiCosts_reportsTheCallsOfUploads() throws Exception {
        mvc.perform(multipart("/api/products").file(jpeg("a.jpg"))).andExpect(status().isCreated());

        // The fake models have no price: they are listed, not counted as free.
        mvc.perform(get("/api/ai-costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStepAndModel[?(@.purpose == 'READ_LABEL' && @.model == 'fake-model')]").exists())
                .andExpect(jsonPath("$.byStepAndModel[?(@.purpose == 'FIND_ALLERGENS' && @.model == 'fake-text-model')]").exists())
                .andExpect(jsonPath("$.unpricedModels").value(org.hamcrest.Matchers.hasItems("fake-model", "fake-text-model")))
                .andExpect(jsonPath("$.totals.products").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
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
