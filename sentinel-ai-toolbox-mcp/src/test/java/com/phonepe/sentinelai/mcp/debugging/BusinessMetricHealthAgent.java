package com.phonepe.sentinelai.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.flipkart.foxtrot.common.ActionRequest;
import com.flipkart.foxtrot.common.ActionResponse;
import com.flipkart.foxtrot.common.Period;
import com.flipkart.foxtrot.common.enums.SourceType;
import com.flipkart.foxtrot.common.query.numeric.BetweenFilter;
import com.flipkart.foxtrot.common.stats.Stat;
import com.flipkart.foxtrot.common.stats.StatsTrendRequest;
import com.flipkart.foxtrot.common.stats.StatsTrendResponse;
import com.phonepe.platform.moses.model.console.ConsoleSection;
import com.phonepe.platform.moses.model.console.widgets.BarWidget;
import com.phonepe.platform.moses.model.console.widgets.CountWidget;
import com.phonepe.platform.moses.model.console.widgets.FQLWidget;
import com.phonepe.platform.moses.model.console.widgets.FunnelWidget;
import com.phonepe.platform.moses.model.console.widgets.GaugeWidget;
import com.phonepe.platform.moses.model.console.widgets.LineRatioWidget;
import com.phonepe.platform.moses.model.console.widgets.LineWidget;
import com.phonepe.platform.moses.model.console.widgets.NonStackedLineWidget;
import com.phonepe.platform.moses.model.console.widgets.PercentageGaugeWidget;
import com.phonepe.platform.moses.model.console.widgets.PieWidget;
import com.phonepe.platform.moses.model.console.widgets.RadarWidget;
import com.phonepe.platform.moses.model.console.widgets.StackedBarWidget;
import com.phonepe.platform.moses.model.console.widgets.StackedLineWidget;
import com.phonepe.platform.moses.model.console.widgets.StatsTrendWidget;
import com.phonepe.platform.moses.model.console.widgets.SunburstWidget;
import com.phonepe.platform.moses.model.console.widgets.TrendWidget;
import com.phonepe.platform.moses.model.console.widgets.Widget;
import com.phonepe.platform.moses.model.console.widgets.WidgetVisitor;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class BusinessMetricHealthAgent extends BaseTest implements ToolBox {

    @BeforeEach
    void setup() {
        super.setup();
    }

    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long start, long end) {
    }


    @JsonClassDescription("Widget input")
    public record WidgetInput(Widget widget, long start, long end) {
    }

    @Tool("This tool can be used to get widgets for a particular console section and execute foxtrot queries")
    @SneakyThrows
    public Map<String, ActionResponse> getFoxtrotWidgets(final long start, final long end) {
        val request = (new Request.Builder())
                .url("http://plt-platdrovee239.phonepe.nbr:25283/v1/consoles/section/payments_oncall_dashboard_overall_performance")
                .build();
        val response = httpClientWithProxy.newCall(request).execute();
        val consoleSection = objectMapper.readValue(response.body().string(), ConsoleSection.class);
        log.info("Response: {}", consoleSection);
        val widgets = consoleSection.getWidgets();

        // filter top 4 widgets
        val filteredWidgets = new ArrayList<Widget>();
        widgets.forEach(widget -> {
            val layout = widget.getLayout();
            if (layout.getCoordinateY() == 0 || layout.getCoordinateY() == 10) {
                filteredWidgets.add(widget);
            }
        });

        val result = new HashMap<String, ActionResponse>();
        filteredWidgets.forEach(widget -> {
            val queryResponse = executeFoxtrotWidgetQuery(widget, start, end);
            if (queryResponse != null) {
                result.put(widget.getTitle(), queryResponse);
            }
        });
        return result;
    }


    //        @Tool("Execute foxtrot widget query")
    @SneakyThrows
    public ActionResponse executeFoxtrotWidgetQuery(final Widget widget,
                                                    final long start,
                                                    final long end) {
        val filters = widget.getFilters();
        val type = widget.getType();
        val table = widget.getTable();
        val actionRequest = widget.accept(new WidgetVisitor<ActionRequest>() {
            @Override
            public ActionRequest visit(LineWidget lineWidget) {
//                    return new HistogramRequest();
                return null;
            }

            @Override
            public ActionRequest visit(LineRatioWidget lineRatioWidget) {
//                    return new TrendRequest();
                return null;
            }

            @Override
            public ActionRequest visit(StackedLineWidget stackedLineWidget) {
//                    return new TrendRequest();
                return null;
            }

            @Override
            public ActionRequest visit(NonStackedLineWidget nonStackedLineWidget) {
//                    return new TrendRequest();
                return null;
            }

            @Override
            public ActionRequest visit(StackedBarWidget stackedBarWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(TrendWidget trendWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(CountWidget countWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(StatsTrendWidget statsTrendWidget) {
                val stats = statsTrendWidget.getStatsToPlot()
                        .stream().map(statOption -> switch (statOption) {
                            case STATS_AVG -> Stat.AVG;
                            case PERCENTILES_1,
                                    PERCENTILES_5, PERCENTILES_25, PERCENTILES_50,
                                    PERCENTILES_75, PERCENTILES_90, PERCENTILES_95, PERCENTILES_99 -> null;
                            case STATS_COUNT -> Stat.COUNT;
                            case STATS_MIN -> Stat.MIN;
                            case STATS_MAX -> Stat.MAX;
                            case STATS_SUM -> Stat.SUM;
                            case STATS_SUM_OF_SQUARES -> Stat.SUM_OF_SQUARES;
                            case STATS_VARIANCE -> Stat.VARIANCE;
                            case STATS_STD_DEVIATION -> Stat.STD_DEVIATION;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                if (stats.isEmpty()) {
                    return null;
                }

                val percentiles = statsTrendWidget.getStatsToPlot()
                        .stream().map(statOption -> switch (statOption) {
                            case PERCENTILES_1 -> 1.0;
                            case PERCENTILES_5 -> 5.0;
                            case PERCENTILES_25 -> 25.0;
                            case PERCENTILES_50 -> 50.0;
                            case PERCENTILES_75 -> 75.0;
                            case PERCENTILES_90 -> 90.0;
                            case PERCENTILES_95 -> 95.0;
                            case PERCENTILES_99 -> 99.0;
                            case STATS_COUNT, STATS_STD_DEVIATION,
                                    STATS_SUM_OF_SQUARES, STATS_MIN,
                                    STATS_MAX, STATS_SUM, STATS_AVG, STATS_VARIANCE -> null;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (percentiles.isEmpty()) {
                    return null;
                }

                return StatsTrendRequest.builder()
                        .table(table)
                        .field(statsTrendWidget.getField())
                        .percentiles(List.of(50.0, 75.0))
                        .extrapolationFlag(false)
                        .stats(stats)
                        .percentiles(percentiles)
                        .period(Period.minutes)
                        .build();
            }

            @Override
            public ActionRequest visit(PieWidget pieWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(GaugeWidget gaugeWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(PercentageGaugeWidget percentageGaugeWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(BarWidget barWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(SunburstWidget sunburstWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(RadarWidget radarWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(FunnelWidget funnelWidget) {
                return null;
            }

            @Override
            public ActionRequest visit(FQLWidget tabularWidget) {
                return null;
            }
        });

        if (actionRequest == null) {
            log.error("Widget Type: {} not supported", type);
            return null;
        }
        actionRequest.setExtrapolationFlag(false);
        actionRequest.setBypassCache(false);
        actionRequest.setSourceType(SourceType.SERVICE);
        actionRequest.setRequestTags(Map.of("serviceName", "spyglass"));
        filters.add(BetweenFilter.builder()
                .field("_timestamp")
                .temporal(true)
//                .from(1744440480000L - 1 * 3600 * 1000)
//                .to(1744440720000L)
                .from(start - 3600 * 1000)
                .to(end)
                .build());
        actionRequest.setFilters(filters);
        val requestBody = RequestBody.create(objectMapper.writeValueAsBytes(actionRequest),
                MediaType.parse("application/json"));
        val request = (new Request.Builder())
                .url("http://plt-platdrovee239.phonepe.nbr:25283/v2/analytics")
                .post(requestBody)
                .build();
        val response = httpClientWithProxy.newCall(request).execute();
        log.info("Foxtrot response code: {}", response.code());
        val responseString = response.body().string();
        val actionResponse = objectMapper.readValue(responseString, new TypeReference<StatsTrendResponse>() {
        });
        return actionResponse;
    }


//    private static final class TestAgent extends Agent<UserInput, String, TestAgent> {
//
//        @Builder
//        public TestAgent(@NonNull AgentSetup agentSetup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
//            super(String.class,
//                    """
//                            You are a business metric health tracker agent. Here are the list of tasks which you need to perform
//                            1. Run foxtrot widget tool to get the applicable widgets and foxtrot query data
//                            """,
//                    agentSetup, extensions, tools);
//        }
//
//        @Override
//        public String name() {
//            return "business-metric-health-agent";
//        }
//
//
//
////        @Tool("This tool can be used to query foxtrot")
////        @SneakyThrows
////        public Map<String, List<StatsTrendValue>> getFoxtrotDataForElapsedTime(final UserInput userInput) {
////            val actionRequest = StatsTrendRequest.builder()
////                    .stats(Set.of(Stat.AVG))
////                    .extrapolationFlag(false)
////                    .table("payment")
////                    .bypassCache(false)
////                    .field("eventData.elapsedTime")
////                    .percentiles(List.of(50.0, 75.0))
////                    .period(Period.minutes)
////                    .sourceType(SourceType.SERVICE)
////                    .requestTags(Map.of("serviceName", "spyglass"))
////                    .filters(List.of(EqualsFilter.builder()
////                                    .field("eventType")
////                                    .value("PAYMENT")
////                                    .build(),
////                            EqualsFilter.builder()
////                                    .field("eventData.senderUPIInvolved")
////                                    .value(true)
////                                    .build(),
////                            EqualsFilter.builder()
////                                    .field("eventData.backEndErrorCode")
////                                    .value("SUCCESS")
////                                    .build(),
////                            NotEqualsFilter.builder()
////                                    .field("eventData.recon")
////                                    .value(true)
////                                    .build(),
////                            LessThanFilter.builder()
////                                    .field("eventData.elapsedTime")
////                                    .value(3600000)
////                                    .build(),
////                            BetweenFilter.builder()
////                                    .field("_timestamp")
////                                    .temporal(true)
////                                    .from(1744440480000L - 1 * 3600 * 1000)
////                                    .to(1744440720000L)
////                                    .build()))
////                    .build();
////            val requestBody = RequestBody.create(objectMapper.writeValueAsBytes(actionRequest),
////                    MediaType.parse("application/json"));
////            val request = (new Request.Builder())
////                    .url("http://plt-platdrovee039.phonepe.mhr:28427/v2/analytics")
////                    .post(requestBody)
////                    .build();
////            val response = httpClientWithProxy.newCall(request).execute();
////            log.info("Foxtrot response code: {}", response.code());
////            val responseString = response.body().string();
////            val actionResponse = objectMapper.readValue(responseString, new TypeReference<StatsTrendResponse>() {
////            });
////            return Map.of("Payment High Elapsed Time", actionResponse.getResult());
////        }
////
//    }
//
//    @Test
//    @SneakyThrows
//    List<AgentMessage> test() {
//        final var objectMapper = JsonUtils.createMapper();
//        TrustManager[] trustAllCerts = new TrustManager[]{
//                new X509TrustManager() {
//                    @Override
//                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
//                    }
//
//                    @Override
//                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
//                    }
//
//                    @Override
//                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//                        return new java.security.cert.X509Certificate[]{};
//                    }
//                }
//        };
//        SSLContext sslContext = SSLContext.getInstance("SSL");
//        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
//        final var httpClient = new OkHttpClient.Builder()
//                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
//                .hostnameVerifier((hostname, session) -> true)
//                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
//                        .removeHeader(HttpHeaders.AUTHORIZATION)
//                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
//                        .build()))
//                .callTimeout(Duration.ofSeconds(180))
//                .connectTimeout(Duration.ofSeconds(120))
//                .readTimeout(Duration.ofSeconds(180))
//                .writeTimeout(Duration.ofSeconds(120))
//                .build();
//        final var model = new SimpleOpenAIModel(
//                "global:LLM_GLOBAL_GPT_4O_PRD",
//                SimpleOpenAI.builder()
//                        .baseUrl("https://godric-internal.phonepe.com")
//                        .apiKey("IGNORED")
//                        .objectMapper(objectMapper)
//                        .clientAdapter(new OkHttpClientAdapter(httpClient))
//                        .build(),
//                objectMapper
//        );
//        final var eventBus = new EventBus();
//
//        final var agent = TestAgent.builder()
//                .agentSetup(AgentSetup.builder()
//                        .mapper(objectMapper)
//                        .model(model)
//                        .modelSettings(ModelSettings.builder()
//                                .temperature(0.1f)
//                                .seed(0)
//                                .build())
//                        .eventBus(eventBus)
//                        .build())
//                .build();
//
//
//        final var requestMetadata = AgentRequestMetadata.builder()
//                .sessionId("s1")
//                .userId("ss")
//                .build();
//
//        final var response = agent.execute(AgentInput.<UserInput>builder()
//                .request(new UserInput("""
//                        Check the health of payment service
//                        """, "payment",
//                        1745899680000L, 1745899980000L))
//                .requestMetadata(requestMetadata)
//                .build());
//
////        log.info("Response: {}", response.getAllMessages());
//        return response.getAllMessages();
//    }
}
