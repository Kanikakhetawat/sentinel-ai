package com.phonepe.sentinelai.mcp;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.TermsQuery;
import com.phonepe.commons.columbus.search.query.text.MatchQuery;
import com.phonepe.dataplatform.severus.model.columbus.KnnQuery;
import com.phonepe.dataplatform.severus.model.request.DocSearchRequest;
import com.phonepe.devx.saarthi.models.SaarthiResponse;
import com.phonepe.devx.saarthi.models.datasource.DataSource;
import com.phonepe.devx.saarthi.models.dto.search.request.SearchRequest;
import com.phonepe.devx.saarthi.models.dto.search.response.SearchResponse;
import com.phonepe.devx.saarthi.models.dto.search.response.post.Post;
import com.phonepe.platform.spyglass.service.health.data.widgets.WidgetType;
import com.phonepe.platform.spyglass.service.health.data.widgets.metric.request.WidgetMetricRequest;
import com.phonepe.platform.spyglass.service.health.data.widgets.metric.request.WidgetRequest;
import com.phonepe.platform.spyglass.service.health.data.widgets.metric.response.WidgetMetricResponse;
import com.phonepe.platform.spyglass.service.health.data.widgets.metric.response.widget.WidgetElement;
import com.phonepe.platform.spyglass.service.health.data.widgets.metric.response.widget.impl.ServerApiWidgetElement;
import com.phonepe.platform.spyglass.service.models.TimeRange;
import com.phonepe.platform.spyglass.service.models.metrics.MetricType;
import com.phonepe.platform.spyglass.service.models.rca.response.NodeAnomalyState;
import com.phonepe.platform.spyglass.service.models.request.OpDirection;
import com.phonepe.platform.spyglass.service.models.request.impl.RCAGraphRequest;
import com.phonepe.platform.spyglass.service.models.response.GraphResponseV2;
import com.phonepe.platform.spyglass.service.models.response.SpyGlassResponse;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.mcp.debugging.GrafanaMCPToolTest;
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
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DebuggingAgentTest {

    private static final String AUTH_HEADER =
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiMmIyZTg4ZDktZDMwNS00N2JkLTk1M2UtYmI2YjJlOWIzY2Y5Iiwic2lkIjoiNzdjNzhjYWItODYzNy00ZDkxLThiMWYtOTJjZWUzYjM5NWQ4IiwiaWF0IjoxNzQ1ODU2NDAzLCJleHAiOjE3NDU5NDI4MDJ9.8uFiyjLUDJZmMcJNfdv2gDKgmckQn8HkgiQEpZxhFuiJm9NBIXy-NM-UPKKXjVQkXr9ek98oTs8735kqO8yXEw";
    private static final String STAGE_AUTH_HEADER =
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiYzJmMTdkY2EtYzIyNi00YmEzLWIwMTgtYjUxOGEzYmZhOTJkIiwic2lkIjoiNWE1YjQ1MTgtYjAwMC00ZjQ3LWIwNGYtZGQ5OWY4YTNjM2QyIiwiaWF0IjoxNzQ1NzY1NDY4LCJleHAiOjE3NDYzNzAyNjh9.rpYdeMNEplTMQIaAZBBD8MLrTFUWYXUKWnh8sb8UGvJ1ijNzWXD0vB7z72CKDlOMOAYVLRnA9NLlGOjdYFRe6A";

    private static OkHttpClient httpClientWithProxy;
    private static final ObjectMapper objectMapper = JsonUtils.createMapper();

    @BeforeEach
    @SneakyThrows
    void setup() {
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
        httpClientWithProxy = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .removeHeader(HttpHeaders.AUTHORIZATION)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .build()))
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1080)))
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();

    }


    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long start, long end) {
    }

    public record Output() {

    }

    @JsonClassDescription("User input")
    public record RcaUserInput(String name, String app, long start, long end) {
    }

    private static final class TestAgent extends Agent<UserInput, String, TestAgent> {


        @Builder
        public TestAgent(@NonNull AgentSetup agentSetup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(String.class,
                    """
                            You are a debugging agent. Here are the list of tasks which you need to perform
                            1. Check for top anomalous apis for the provided service for the given time period using service status tool
                            2. Check the root cause for top 3 identified anomalous api names. Send service name as app and api path as "name" to the spyglass RCA tool
                            3. Check the elapsed time metric of the provided service using foxtrot tool.
                               Compare the current interval result with historical data returned in foxtrot response.
                            4. If there is an anomaly in foxtrot tool data, send the user input query string to vector DB
                            5. Summarize the vector DB results and highlight the remediation measures or possible causes mentioned in the results
                            6. Analyse the RCA response, elapsed time metrics response analysis, vector DB results and summarise the issues.
                            Summarize the vector DB response and highlight the remediation measures or possible causes. Also suggest any actions which need to be taken for handling
                            the issue. List the dashboard (grafana and foxtrot) links to be used.
                            7. With the grafana dashboards, further debug the issue by checking the metrics of grafana links/dashboards using the grafanaMcp tool.
                            Use these metric data to summarize the issue
                            """,
                    agentSetup, extensions, tools);
        }

        @Override
        public String name() {
            return "test-agent";
        }

        @Tool("This tool can used to fetch metric data from grafana mcp")
        public void grafanaMcp() {
          //  log.info("Link: {}", link);
            new GrafanaMCPToolTest().test();
        }

        @Tool("This tool can be used for checking anomalies in the application metrics for a given time period")
        public List<WidgetElement> getServiceStatus(final UserInput userInput) throws IOException, NoSuchAlgorithmException {
            SimpleModule module = new SimpleModule();
            module.addKeyDeserializer(TimeRange.class, new TimeRangeKeyDeserializer());
            objectMapper.registerModule(module);

            val widgetMetricRequest = WidgetMetricRequest.builder()
                    .widgetRequests(Map.of("widgetId", WidgetRequest.builder()
                            .widgetType(WidgetType.SERVER_API)
                            .serviceName(userInput.service)
                            .build()))
                    .timeRange(TimeRange.builder()
                            .start(new Date(userInput.start))
                            .end(new Date(userInput.end))
                            .build())
                    .build();

            RequestBody requestBody =
                    RequestBody.create(objectMapper.writeValueAsBytes(widgetMetricRequest),
                            MediaType.parse("application/json"));

            Request request = (new Request.Builder()).url("https://spyglass.drove.plat.phonepe.mh1/v1/service/widgets/metrics")
                    .post(requestBody)
                    .build();
            Response response = httpClientWithProxy.newCall(request).execute();
            log.info("Spyglass Response code: {}", response.code());
            SpyGlassResponse<WidgetMetricResponse> saarthiResponse = objectMapper.readValue(response.body().bytes(), new TypeReference<>() {
            });
            val widgetResponse = saarthiResponse.getResponse().getWidgetResponse().get("widgetId");
            val widgetElements = new ArrayList<WidgetElement>();
            widgetResponse.getWidgetElements().stream()
                    .filter(widgetElement -> widgetElement.getType() == WidgetType.SERVER_API)
                    .map(widgetElement -> (ServerApiWidgetElement) widgetElement)
                    .forEach(widgetElement -> {
                        val anyAnomalous = widgetElement.getMetricAnomalyData()
                                .entrySet()
                                .stream().anyMatch(metric -> metric.getValue().entrySet()
                                        .stream()
                                        .anyMatch(timeBased -> timeBased.getValue().getAnomalyData().getAnomalyState() == NodeAnomalyState.ANOMALOUS));
                        if (anyAnomalous) {
                            widgetElements.add(widgetElement);
                        }
                    });
            return widgetElements;
        }

        @Tool(value = "This tool is responsible for searching multiple data sources to find matches for a given input query")
        @SneakyThrows
        public List<Post> search(@JsonPropertyDescription("Query to find results against") final String query) {
            log.info("Searching for: {}", query);
            final var objectMapper = JsonUtils.createMapper();
            RequestBody requestBody =
                    RequestBody.create(objectMapper
                                    .writeValueAsBytes(SearchRequest.builder()
                                            .dataSources(Set.of(DataSource.CONFLUENCE, DataSource.SLACK))
                                            .query(query)
                                            .build()),
                            MediaType.parse("application/json"));

            Request request = (new Request.Builder()).url("http://prd-devxdrovee003.phonepe.mh1:12393/search/v1/search")
                    .post(requestBody)
                    .build();

            Response response = httpClientWithProxy.newCall(request).execute();
            log.info("Response code: {}", response.code());
            SaarthiResponse<SearchResponse> saarthiResponse = objectMapper.readValue(response.body().bytes(), new TypeReference<>() {
            });
            return saarthiResponse.getResponse().getPosts();
        }

        @Tool("This tool can be used to get root cause for an anomalous service node")
        @SneakyThrows
        public GraphResponseV2 getRca(final RcaUserInput rcaUserInput) {
            SimpleModule module = new SimpleModule();
            module.addKeyDeserializer(MetricType.class, new MetricTypeKeyDeserializer());
            objectMapper.registerModule(module);

            log.info("Executing rca for: {}", rcaUserInput);
            val rcaRequest = RCAGraphRequest.builder()
                    .opDirection(OpDirection.FORWARD)
                    .app(rcaUserInput.app)
                    .name(rcaUserInput.name)
                    .timeRange(TimeRange.builder()
                            .start(new Date(rcaUserInput.start))
                            .end(new Date(rcaUserInput.end))
                            .build())
                    .build();
            val requestBody = RequestBody.create(objectMapper.writeValueAsBytes(rcaRequest),
                    MediaType.parse("application/json"));
            val request = (new Request.Builder()).url("https://spyglass.drove.plat.phonepe.mh1/v1/graph")
                    .post(requestBody)
                    .build();
            val response = httpClientWithProxy.newCall(request).execute();
            log.info("RCA response code: {}", response.code());
            SpyGlassResponse<GraphResponseV2> spyGlassResponse = objectMapper.readValue(response.body().bytes(), new TypeReference<>() {
            });
            return spyGlassResponse.getResponse();
        }

        @Tool("This tool can be used to query foxtrot")
        @SneakyThrows
        public List<StatsTrendValue> getFoxtrotDataForElapsedTime(final UserInput userInput) {
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
            return actionResponse.getResult();
        }

        @Tool("This tool can be used to query vector DB")
        @SneakyThrows
        public com.phonepe.dataplatform.severus.model.response.SearchResponse queryVectorStore(final String query) {
            val httpClient = new OkHttpClient.Builder()
                    .hostnameVerifier((hostname, session) -> true)
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .removeHeader(HttpHeaders.AUTHORIZATION)
                            .header(HttpHeaders.AUTHORIZATION, STAGE_AUTH_HEADER)
                            .build()))
                    .callTimeout(Duration.ofSeconds(180))
                    .connectTimeout(Duration.ofSeconds(120))
                    .readTimeout(Duration.ofSeconds(180))
                    .writeTimeout(Duration.ofSeconds(120))
                    .build();


            val docSearchRequest = DocSearchRequest.builder()
                    .query(KnnQuery.builder()
                            .embed(true)
                            .field("text")
                            .value(query)
                            .k(10)
                            .numCandidates(10000)
                            .boost(100.0F)
                            .subQuery(BoolQuery.builder()
                                    .filterClauses(List.of(TermsQuery.builder()
                                            .field("metadata.source_id")
                                            .values(List.of("fa8c970a74d187435234"))
                                            .build()))
                                    .mustClauses(List.of(MatchQuery.builder()
                                            .field("text")
                                            .value(query)
                                            .build()))
                                    .build())
                            .build())
                    .limit(5)
                    .build();
            val requestBody = RequestBody.create(objectMapper.writeValueAsBytes(docSearchRequest),
                    MediaType.parse("application/json"));

            val request = (new Request.Builder())
                    .url("http://severus.nixy.stg-drove.phonepe.nb6/v1/collection/test/search/DC_TEST_DOCSGPT_17_MAR_1_STG")
                    .post(requestBody)
                    .build();
            val response = httpClient.newCall(request).execute();
            log.info("VectorDB response code: {}", response.code());


            val responseString = response.body().string();
            return objectMapper.readValue(responseString, new TypeReference<>() {
            });
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
                .build();


        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();

        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("""
                        Why is payments elapsed time high
                        """, "payment",
                        1745205600000L, 1745206082000L))
                .requestMetadata(requestMetadata)
                .build());

        log.info("Response: {}", response.getData());
    }
}
