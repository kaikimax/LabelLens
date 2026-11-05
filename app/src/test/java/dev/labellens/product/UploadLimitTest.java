package dev.labellens.product;

import dev.labellens.TestcontainersConfiguration;
import dev.labellens.ai.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The daily cap on uploads (decisions log, 2026-11-05). Its own Spring context with a limit of 2,
 * so it gets its own empty database and doesn't depend on what other tests uploaded.
 */
@SpringBootTest(properties = {
        "labellens.storage.local-dir=target/test-uploads",
        "labellens.uploads.daily-limit=2"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class UploadLimitTest {

    @Autowired
    MockMvc mvc;

    static MockMultipartFile jpeg() {
        return new MockMultipartFile("photos", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void thirdUploadOfTheDay_isRefused_beforeAnyAiCall() throws Exception {
        mvc.perform(multipart("/api/products").file(jpeg())).andExpect(status().isCreated());
        mvc.perform(multipart("/api/products").file(jpeg())).andExpect(status().isCreated());

        // API: 429 Too Many Requests, as a problem detail.
        mvc.perform(multipart("/api/products").file(jpeg()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("limit of 2 uploads in 24 hours")));

        // Web page without JavaScript: the home page with the message, also 429.
        mvc.perform(multipart("/products").file(jpeg()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("Please try again tomorrow.")));

        // With htmx: the message as a fragment.
        mvc.perform(multipart("/products").file(jpeg()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please try again tomorrow.")));
    }
}
