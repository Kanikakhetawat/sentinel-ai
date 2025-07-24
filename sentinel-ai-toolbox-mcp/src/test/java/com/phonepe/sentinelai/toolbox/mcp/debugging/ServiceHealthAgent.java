package com.phonepe.sentinelai.toolbox.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.toolbox.mcp.MetricTypeKeyDeserializer;
import com.phonepe.sentinelai.toolbox.mcp.TimeRangeKeyDeserializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
public class ServiceHealthAgent extends BaseTest implements ToolBox {

    @BeforeEach
    void setup() {
        super.setup();
    }

    @Override
    public String name() {
        return "";
    }

    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long start, long end) {
    }

    @JsonClassDescription("User input")
    public record RcaUserInput(String name, String app, long start, long end) {
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
