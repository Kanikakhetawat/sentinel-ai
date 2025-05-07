package com.phonepe.sentinelai.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.net.HttpHeaders;
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
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.mcp.MetricTypeKeyDeserializer;
import com.phonepe.sentinelai.mcp.TimeRangeKeyDeserializer;
import com.phonepe.sentinelai.mcp.debugging.BaseTest;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
public class ServiceHealthAgent extends BaseTest {

    @BeforeEach
    void setup() {
        super.setup();
    }

    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long start, long end) {
    }

    @JsonClassDescription("User input")
    public record RcaUserInput(String name, String app, long start, long end) {
    }


    private static final class TestAgent extends Agent<UserInput, String, TestAgent> {

        @Builder
        public TestAgent(@NonNull AgentSetup agentSetup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(String.class,
                    """
                            You are a service health tracker agent. Here are the list of tasks which you need to perform
                            1. Check for top anomalous apis for the provided service for the given time period using service status tool
                            2. Check the root cause for top 3 identified anomalous api names. Send service name as app and api path as "name" to the spyglass RCA tool
                            """,
                    agentSetup, extensions, tools);
        }

        @Override
        public String name() {
            return "service-health-agent";
        }

        @Tool("This tool can be used for checking anomalies in the application metrics for a given time period")
        @SneakyThrows
        public List<WidgetElement> getServiceStatus(final UserInput userInput) {
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
