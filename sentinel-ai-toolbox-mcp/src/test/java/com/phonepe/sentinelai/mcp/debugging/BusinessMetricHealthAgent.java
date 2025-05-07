package com.phonepe.sentinelai.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.flipkart.foxtrot.common.Period;
import com.flipkart.foxtrot.common.enums.SourceType;
import com.flipkart.foxtrot.common.query.general.EqualsFilter;
import com.flipkart.foxtrot.common.query.general.NotEqualsFilter;
import com.flipkart.foxtrot.common.query.numeric.BetweenFilter;
import com.flipkart.foxtrot.common.query.numeric.LessThanFilter;
import com.flipkart.foxtrot.common.stats.Stat;
import com.flipkart.foxtrot.common.stats.StatsTrendRequest;
import com.flipkart.foxtrot.common.stats.StatsTrendResponse;
import com.flipkart.foxtrot.common.stats.StatsTrendValue;
import com.google.common.net.HttpHeaders;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class BusinessMetricHealthAgent extends BaseTest {

    @BeforeEach
    void setup() {
        super.setup();
    }

    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long start, long end) {
    }


    private static final class TestAgent extends Agent<UserInput, String, TestAgent> {

        @Builder
        public TestAgent(@NonNull AgentSetup agentSetup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(String.class,
                    """
                            You are a business metric health tracker agent. Here are the list of tasks which you need to perform
                            1. Run foxtrot tool to get the behaviour of business metric
                            """,
                    agentSetup, extensions, tools);
        }

        @Override
        public String name() {
            return "business-metric-health-agent";
        }

        @Tool("This tool can be used to query foxtrot")
        @SneakyThrows
        public Map<String, List<StatsTrendValue>> getFoxtrotDataForElapsedTime(final UserInput userInput) {
            val actionRequest = StatsTrendRequest.builder()
                    .stats(Set.of(Stat.AVG))
                    .extrapolationFlag(false)
                    .table("payment")
                    .bypassCache(false)
                    .field("eventData.elapsedTime")
                    .percentiles(List.of(50.0, 75.0))
                    .period(Period.minutes)
                    .sourceType(SourceType.SERVICE)
                    .requestTags(Map.of("serviceName", "spyglass"))
                    .filters(List.of(EqualsFilter.builder()
                                    .field("eventType")
                                    .value("PAYMENT")
                                    .build(),
                            EqualsFilter.builder()
                                    .field("eventData.senderUPIInvolved")
                                    .value(true)
                                    .build(),
                            EqualsFilter.builder()
                                    .field("eventData.backEndErrorCode")
                                    .value("SUCCESS")
                                    .build(),
                            NotEqualsFilter.builder()
                                    .field("eventData.recon")
                                    .value(true)
                                    .build(),
                            LessThanFilter.builder()
                                    .field("eventData.elapsedTime")
                                    .value(3600000)
                                    .build(),
                            BetweenFilter.builder()
                                    .field("_timestamp")
                                    .temporal(true)
                                    .from(1744440480000L - 1 * 3600 * 1000)
                                    .to(1744440720000L)
                                    .build()))
                    .build();
            val requestBody = RequestBody.create(objectMapper.writeValueAsBytes(actionRequest),
                    MediaType.parse("application/json"));
            val request = (new Request.Builder())
                    .url("http://plt-platdrovee039.phonepe.mhr:28427/v2/analytics")
                    .post(requestBody)
                    .build();
            val response = httpClientWithProxy.newCall(request).execute();
            log.info("Foxtrot response code: {}", response.code());
            val responseString = response.body().string();
            val actionResponse = objectMapper.readValue(responseString, new TypeReference<StatsTrendResponse>() {
            });
            return Map.of("Payment High Elapsed Time", actionResponse.getResult());
        }

    }

    @Test
    @SneakyThrows
    List<AgentMessage> test() {
        final var objectMapper = JsonUtils.createMapper();
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        final var httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .removeHeader(HttpHeaders.AUTHORIZATION)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .build()))
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var model = new SimpleOpenAIModel(
                "global:LLM_GLOBAL_GPT_4O_PRD",
                SimpleOpenAI.builder()
                        .baseUrl("https://godric-internal.phonepe.com")
                        .apiKey("IGNORED")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(httpClient))
                        .build(),
                objectMapper
        );
        final var eventBus = new EventBus();

        final var agent = TestAgent.builder()
                .agentSetup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(0)
                                .build())
                        .eventBus(eventBus)
                        .build())
                .build();


        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();

        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("""
                        Check the health of payment service
                        """, "payment",
                        1745899680000L, 1745899980000L))
                .requestMetadata(requestMetadata)
                .build());

//        log.info("Response: {}", response.getAllMessages());
        return response.getAllMessages();
    }
}
