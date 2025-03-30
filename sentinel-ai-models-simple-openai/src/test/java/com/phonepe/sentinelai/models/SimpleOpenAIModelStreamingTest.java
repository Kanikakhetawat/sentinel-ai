package com.phonepe.sentinelai.models;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.sentinelai.core.utils.TestUtils.readStubFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests simple text based io with {@link SimpleOpenAIModel}
 */
@Slf4j
@WireMockTest
class SimpleOpenAIModelStreamingTest {
    private static final class TestAgent extends Agent<String, String, TestAgent> {

        public TestAgent(
                @NonNull AgentSetup setup) {
            super(String.class,
                  "Greet the user",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }

        @Tool("Get name of the user")
        public String getName() {
            return "Santanu";
        }
    }

    @Test
    @SneakyThrows
    void testAgent(final WireMockRuntimeInfo wiremock) {
        //Setup stub for SSE
        IntStream.rangeClosed(1, 3)
                .forEach(i -> {
                    stubFor(post("/chat/completions?api-version=2024-10-21")
                                    .inScenario("model-test")
                                    .whenScenarioStateIs(i == 1 ? Scenario.STARTED : Objects.toString(i))
                                    .willReturn(okForContentType("text/event-stream",
                                                                 readStubFile(i, "events", getClass())))
                                    .willSetStateTo(Objects.toString(i + 1)));

                });
        final var objectMapper = JsonUtils.createMapper();

        final var httpClient = new OkHttpClient.Builder()
                .build();
        final var model = new SimpleOpenAIModel(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl("http://localhost:8080")
//                        // Uncomment the above to record responses using the wiremock recorder.
//                        // Yeah ... life is hard
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(httpClient))
                        .build(),
                objectMapper
        );
        final var stats = new ModelUsageStats(); //We want to collect stats from the whole session
        final var agent = new TestAgent(AgentSetup.builder()
                                                .model(model)
                                                .mapper(objectMapper)
                                                .modelSettings(ModelSettings.builder()
                                                                       .temperature(0.1f)
                                                                       .seed(1)
                                                                       .build())
                                                .build());
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming("Hi", AgentRequestMetadata.builder()
                                                                 .sessionId("s1")
                                                                 .userId("ss")
                                                                 .usageStats(stats)
                                                                 .build(),
                                                         data -> print(data, outputStream))
                .join();
        assertTrue(new String(response.getData()).contains("Santanu"));
        assertTrue(response.getUsage().getTotalTokens() > 1);
        final var response2 = agent.executeAsyncStreaming("What is my name?", AgentRequestMetadata.builder()
                                                                  .sessionId("s1")
                                                                  .userId("ss")
                                                                  .usageStats(stats)
                                                                  .build(),
                                                          data -> print(data, outputStream),
                                                          response.getAllMessages())
                .join();
        assertTrue(new String(response2.getData()).contains("Santanu"));
        assertTrue(response2.getUsage().getTotalTokens() > 1);
        assertTrue(stats.getTotalTokens() > 1);
        log.info("Session stats: {}", stats);
    }

    private static void print(byte[] data, PrintStream outputStream) {
        try {
            outputStream.write(data);
            outputStream.flush();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
