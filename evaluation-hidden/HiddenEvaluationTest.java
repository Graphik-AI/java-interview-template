package com.company.promoengine.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.company.promoengine.support.JsonComparison;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Runs the fixtures in {@code evaluation-hidden/fixtures} from the project root.
 * Copy this class to {@code src/test/java/com/company/promoengine/api/} before
 * {@code ./mvnw test}. It prints a pass/fail line per fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HiddenEvaluationTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    private static final Path FIXTURES = Path.of("evaluation-hidden", "fixtures");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void evaluatesHiddenFixtures() throws Exception {
        List<String> names;
        try (Stream<Path> files = Files.list(FIXTURES)) {
            names = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();
        }

        var failures = new ArrayList<String>();
        for (String name : names) {
            try {
                evaluate(name);
                System.out.println("PASS " + name);
            } catch (AssertionError failure) {
                failures.add(name);
                System.out.println("FAIL " + name);
                System.out.println(failure.getMessage());
            }
        }

        System.out.printf("%d fixtures: %d passed, %d failed%n",
                names.size(), names.size() - failures.size(), failures.size());
        if (!failures.isEmpty()) {
            throw new AssertionError("Hidden fixtures failed: " + failures);
        }
    }

    private void evaluate(String name) throws Exception {
        JsonNode root = JSON.readTree(FIXTURES.resolve(name).toFile());
        MvcResult result = mockMvc.perform(post("/api/v1/cart/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsBytes(root.get("request"))))
                .andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        if (status != 200) {
            throw new AssertionError(name + " HTTP " + status + "\n" + body);
        }
        JsonComparison.assertEquals(root.get("expectedResponse"), JSON.readTree(body));
    }
}
