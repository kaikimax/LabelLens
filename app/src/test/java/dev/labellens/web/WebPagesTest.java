package dev.labellens.web;

import dev.labellens.TestcontainersConfiguration;
import dev.labellens.ai.FakeAiConfiguration;
import dev.labellens.ai.FakeTextModel;
import dev.labellens.ai.FakeVisionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The web pages, rendered for real (Thymeleaf), with the fake models and a real database. */
@SpringBootTest(properties = "labellens.storage.local-dir=target/test-uploads")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class WebPagesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeVisionModel fakeModel;

    @Autowired
    FakeTextModel fakeTextModel;

    @BeforeEach
    void reset() {
        fakeModel.reset();
        fakeTextModel.reset();
    }

    static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("photos", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void homePage_hasTheUploadForm() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Read a food label")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("Open Food Facts")));
    }

    @Test
    void upload_withoutJavaScript_redirectsToTheResultPage() throws Exception {
        MvcResult upload = mvc.perform(multipart("/products").file(jpeg("jam.jpg")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", matchesPattern("/products/[0-9a-f-]{36}")))
                .andReturn();

        // The jam: grade D, score 18, no allergens, fruit/vegetables assumed 0.
        mvc.perform(get(upload.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confiture de Fraises")))
                .andExpect(content().string(containsString("grade grade-d")))
                .andExpect(content().string(containsString("none of the 14 EU allergens found")))
                .andExpect(content().string(containsString("0% was assumed")));
    }

    @Test
    void upload_withHtmx_returnsOnlyTheResult_andTheProductUrl() throws Exception {
        mvc.perform(multipart("/products").file(jpeg("jam.jpg")).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Push-Url", matchesPattern("/products/[0-9a-f-]{36}")))
                .andExpect(content().string(containsString("grade grade-d")))
                .andExpect(content().string(not(containsString("<html"))));       // a fragment, not a page
    }

    @Test
    void allergens_areShownByName_withMayContainSeparately() throws Exception {
        fakeModel.reply(FakeVisionModel.JAM_JSON
                .replace("Fraises, Sucre,", "Lait entier, Fraises, Sucre,")
                .replace("\"may_contain_text\": null", "\"may_contain_text\": \"Peut contenir des traces de noisettes.\""));

        MvcResult upload = mvc.perform(multipart("/products").file(jpeg("a.jpg"))).andReturn();

        mvc.perform(get(upload.getResponse().getHeader("Location")))
                .andExpect(content().string(containsString(">Milk<")))
                .andExpect(content().string(containsString("tag tag-may")))
                .andExpect(content().string(containsString(">Tree nuts<")))
                .andExpect(content().string(containsString("Peut contenir des traces de noisettes.")));
    }

    @Test
    void allergenAiDown_thePageSaysOnlyTheWordListChecked() throws Exception {
        fakeModel.reply(FakeVisionModel.JAM_JSON.replace("Fraises, Sucre,", "Lait entier, Fraises, Sucre,"));
        fakeTextModel.failWith(new IllegalStateException("Connection refused"));

        MvcResult upload = mvc.perform(multipart("/products").file(jpeg("a.jpg"))).andReturn();

        mvc.perform(get(upload.getResponse().getHeader("Location")))
                .andExpect(content().string(containsString(">Milk<")))
                .andExpect(content().string(containsString("Only the word list checked these ingredients")));
    }

    @Test
    void failedReading_showsTheReason() throws Exception {
        fakeModel.reply("I cannot read this label.", "Still cannot read it.");

        MvcResult upload = mvc.perform(multipart("/products").file(jpeg("blurry.jpg"))).andReturn();

        mvc.perform(get(upload.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Couldn't read this label")));
    }

    @Test
    void noPhoto_withoutJavaScript_isA400WithTheMessageOnThePage() throws Exception {
        // What a browser sends when no file is chosen: one empty part with no file name.
        MockMultipartFile none = new MockMultipartFile("photos", "", "application/octet-stream", new byte[0]);

        mvc.perform(multipart("/products").file(none))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Send at least 1 photo.")))
                .andExpect(content().string(containsString("Read a food label")));       // the whole page
    }

    @Test
    void noPhoto_withHtmx_returnsTheMessageAsAFragment() throws Exception {
        mvc.perform(multipart("/products").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Send at least 1 photo.")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void unknownProduct_is404_withAMessage() throws Exception {
        mvc.perform(get("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                // th:text escapes the apostrophe of "doesn't", so match the rest.
                .andExpect(content().string(containsString("exist (or was deleted)")));
    }

    @Test
    void apiErrors_stayJson() throws Exception {
        // The API's error handler must not have been replaced by the pages' one.
        mvc.perform(multipart("/api/products"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")));
    }

    @Test
    void htmx_isServedFromTheApp_notACdn() throws Exception {
        MvcResult js = mvc.perform(get("/js/htmx-2.0.4.min.js")).andExpect(status().isOk()).andReturn();
        assertThat(js.getResponse().getContentAsString()).startsWith("var htmx=");
    }
}
