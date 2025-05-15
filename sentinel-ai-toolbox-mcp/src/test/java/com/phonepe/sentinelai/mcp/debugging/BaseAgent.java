package com.phonepe.sentinelai.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
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
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
public class BaseAgent extends BaseTest {

    @BeforeEach
    void setup() {
        super.setup();
    }


    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long start, long end) {
    }


    //  "system_fingerprint" : "fp_ee1d74bde0",

    private static final class TestAgent extends Agent<UserInput, String, TestAgent> {

        @Builder
        public TestAgent(@NonNull AgentSetup agentSetup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(String.class,
                    """
                            You are a debugging agent. Here are the list of tasks which you need to perform. Please make sure to perform each individual step
                            Step 1. Run ServiceHealth tool to identify any issues with the service. For each anomalous API, run RCA tool to identify the issue
                            Step 2. Check critical business metrics performance using the business metric health tool
                            Step 3. This step has to be run after step (2). For each business metric check the following:
                                 a. Check if there is a huge deviation from usual values.
                                 b. For these metrics, send the key in the map which is having the issue in the query to vectorDB.     
                            Step 4. After step (4) and (5), combine all the results and show following:
                              a. Service level degradation, apis which are degraded and for each its root cause identified
                              b. Business level degradation
                                   (i). Metrics which are anomalous. Show the usual values and the current value
                                   (ii). Possible reasons
                                   (iii). All the things to check and all relevant grafana,foxtrot, confluence links
                            """,
                    agentSetup, extensions, tools);

            // Step 3. For each identified service metric issue, query vector DB tool. Send the metric which is having issue in the query
            //                            to vectorDB
        }

        @Override
        public String name() {
            return "base-agent";
        }

//        @Tool("Tool to execute service health agent tool")
//        public List<AgentMessage> serviceHealthTool() {
//            return new ServiceHealthAgent().test();
//        }
//
//        @Tool("Tool to execute business metric health agent tool")
//        public List<AgentMessage> businessMetricHealthTool() {
//            return new BusinessMetricHealthAgent().test();
//        }
//
//        @Tool("Tool to execute vector DB agent")
//        public VectorDBAgentResponse vectorDBQueryTool(final String query) {
//            log.info("Query to vector DB: {}", query);
//            return new VectorDBQueryAgent().test(query);
//        }

        @Tool("Tool to query grafana agent")
        public void grafanaTool(final String url,
                                final long start,
                                final long end) {
            new GrafanaMCPToolTest().test();
        }
    }

    @Test
    @SneakyThrows
    void test() {
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
                .tools(Map.of())
                .build();

        agent.registerToolboxes(List.of(new ServiceHealthAgent(), new BusinessMetricHealthAgent(),
                new VectorDBQueryAgent()));


        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();

//        final var response = agent.execute(AgentInput.<UserInput>builder()
//                .request(new UserInput("""
//                        Payment service health
//                        """, "payment",
//                        1745899680000L, 1745899980000L))
//                .requestMetadata(requestMetadata)
//                .build());

        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("""
                        Payment service health
                        """, "payment",
                        1747053600000L, 1747053900000L))
                .requestMetadata(requestMetadata)
                .build());

        log.info("Response: {}", response.getData());
    }
}
