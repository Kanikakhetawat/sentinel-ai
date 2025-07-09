package com.phonepe.sentinelai.toolbox.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.common.net.HttpHeaders;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@WireMockTest
@Slf4j
public class GrafanaMCPToolTest {

    @JsonClassDescription("User input")
    public record UserInput(String data, long start, long end) {
    }


    class MCPTestAgent extends Agent<UserInput, String, MCPTestAgent> {

        public MCPTestAgent(
                @NonNull AgentSetup setup,
                Map<String, ExecutableTool> knownTools) {
            super(String.class,
                    """
                            1. Search for dashboard: get_dashboard_by_uid
                            2. List the exact metrics to be queried
                    
                            """, setup, List.of(), knownTools);
//            super(String.class,
//                    """
//                            1. Search for dashboard: get_dashboard_by_uid
//                            2. Search for AXL Pay metrics and get the data for the time period.
//                            3. Use grafana query tool to get the data of these metrics for last 2 mins
//                            3. Summarize the metrics
//                            """, setup, List.of(), knownTools);
        }

        @Override
        public String name() {
            return "mcp-grafana-agent";
        }

//        @Tool("Tool for querying metrics from grafana")
//        @SneakyThrows
//        public String queryGrafana(final String metric, final long start, final long end) {
//            final String GRAFANA_URL = "http://prd-grafana001.phonepe.nm5";
//            final var objectMapper = JsonUtils.createMapper();
//
//            Map<String, Object> data = new HashMap<>();
//            data.put("start", start);
//            data.put("end", end);
//
//            data.put("queries", List.of(Map.of("aggregator", "avg", "metric", metric)));
//
//            val dataString = objectMapper.writeValueAsString(data);
//
//            RequestBody requestBody =
//                    RequestBody.create(dataString, MediaType.parse("application/json"));
//
////            Request request = (new Request.Builder()).url(GRAFANA_URL + "/api/datasources/proxy/70/api/query")
////                    .post(requestBody)
////                    .build();
//
//            Request request = (new Request.Builder()).url("http://prd-grafana001.phonepe.nm5/d/ACDHU1Unk/one-upi?orgId=1")
//                    .build();
//
//            val httpClient = new OkHttpClient.Builder()
//                    .hostnameVerifier((hostname, session) -> true)
//                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
//                            .removeHeader(HttpHeaders.AUTHORIZATION)
//                            .build()))
//                    .callTimeout(Duration.ofSeconds(180))
//                    .connectTimeout(Duration.ofSeconds(120))
//                    .readTimeout(Duration.ofSeconds(180))
//                    .writeTimeout(Duration.ofSeconds(120))
//                    .build();;
//
//            Response response = httpClient.newCall(request).execute();
//            log.info("Response code: {}", response.code());
//            return response.body().string();
//        }
    }

    @Test
    @SneakyThrows
    public void test() {

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

        final var agent = new MCPTestAgent(
                AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(42)
                                .build())
                        .build(),
                Map.of() // No tools for now
        );

        final var params = ServerParameters.builder("/Users/kanika.khetawat/mcp-grafana")
                .env(Map.of("GRAFANA_URL", "http://prd-grafana001.phonepe.nm5"))
                .args("--disable-prometheus", "--disable-oncall", "--disable-alerting", "--disable-loki",
                        "--disable-incident")
                .build();

        final var transport = new StdioClientTransport(params);

        final var mcpClient = McpClient.sync(transport)
                .build();
        mcpClient.initialize();
//        final var mcpToolBox = new MCPToolBox(mcpClient, objectMapper);
//        agent.registerToolbox(mcpToolBox);
//        final var response = agent.execute(AgentInput.<UserInput>builder()
//                        .request(new UserInput("ACDHU1Unk", 0, 0))
//                .build());
//        log.info("Agent response: {}", response.getData());
    }
}
