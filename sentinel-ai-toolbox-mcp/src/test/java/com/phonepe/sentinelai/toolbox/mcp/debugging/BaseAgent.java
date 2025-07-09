package com.phonepe.sentinelai.toolbox.mcp.debugging;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.net.HttpHeaders;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.ResponseTransformerConfig;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.HandlebarHelper;
import com.slack.api.methods.response.search.SearchAllResponse;
import com.slack.api.model.MatchedItem;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.LONG;
import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.STRING;

@Slf4j
public class BaseAgent extends BaseTest {

    public static final String AUTH_HEADER = "";


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
                            You are a debugging agent. Here are the list of tasks which you need to perform. Please make sure to perform each individual step
                             Step 1. Run ServiceHealth tool to identify any issues with the service.
                                     a. Run RCA tool to identify the root cause for each anomalous widget
                             Step 2. Get business widgets. Run following steps:
                                     a. Use `businessMetricWidgetTool` to fetch all business metric widgets.
                                     b. Extract all the widget data associated with "widgets" key from the above json response.
                                     c. Select the widgets using the following pseudocode logic:
                                        for widget in widgets:
                                           if widget["layout"]["coordinateY"] <= 10 and widget["type"] in ["STACKED_LINE"]:
                                                   Include this widget entire data in the next step
                                     d. After step 2.c: For each selected widget, **call `executeFoxtrotQuery`** with the complete widget data.
                                       i. Check if there is a huge deviation from usual values.
                                       ii. For these metrics, send the title of the widget which is having the issue in the query to vectorDB
                                       iii. For these metrics, send the title of the widget which is having the issue in the query to slackTool
                             Step 3. Combine all the above results and return response in following manner:
                              a. Service level degradation:
                                   a. Server_api which are degraded and the deviation values along with root cause from rca tool
                                   b. DB sharding shards which are degraded and the deviation values
                              b. Business level degradation
                                   (i). Metrics which are anomalous. Show the usual values and the current value with the deviation
                                   (ii). Vector DB and slack insights along with relevant links or dashboards
                            """,
                    agentSetup, extensions, tools);
        }

        @Override
        public String name() {
            return "base-agent";
        }

        @Tool("Tool to search slack messages")
        @SneakyThrows
        public List<SlackContent> slackSearchTool(final String query) throws NoSuchAlgorithmException {

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
                            .header(HttpHeaders.AUTHORIZATION, "Bearer :q")
                            .build()))
                    .callTimeout(Duration.ofSeconds(180))
                    .connectTimeout(Duration.ofSeconds(120))
                    .readTimeout(Duration.ofSeconds(180))
                    .writeTimeout(Duration.ofSeconds(120))
                    .build();

            val allResults = new ArrayList<MatchedItem>();
            int page = 1;
            boolean hasMore = true;
            int limit = 50;
            try {
                while (hasMore) {
                    Request request = (new Request.Builder()).url("https://slack-com.prdob.phonepe.nm5/api/search.messages?q=" + query)
                            .header("Authorization",  "Bearer xoxb-")
                            .build();
                    Response response = httpClient.newCall(request).execute();

                    SearchAllResponse searchAllResponse =
                            objectMapper.readValue(response.body().bytes(), new TypeReference<>() {
                            });
                    if (searchAllResponse.getMessages().getTotal() > 0) {
                        val matches = searchAllResponse.getMessages().getMatches();
                        allResults.addAll(matches);
                    }

                    hasMore = page < searchAllResponse.getMessages().getPagination().getPageCount();
                    page++;
                    if (!hasMore || allResults.size() >= limit) {
                        break;
                    }

                    // Respect rate limits
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                log.error("Error while searching slack messages: {}", e.getMessage(), e);
            }

            val formattedResults = new ArrayList<SlackContent>();
            for (val result : allResults) {
                if (result.getChannel() == null || result.getChannel().isPrivateChannel()) {
                    continue;
                }
                val formattedResult = SlackContent.builder()
                        .channelName(result.getChannel().getName() != null ? result.getChannel().getName() : "Unknown channel")
                        .user(result.getUsername() != null ? result.getUsername() : "Unknown user")
                        .text(result.getText() != null ? result.getText() : "")
                        .permalink(result.getPermalink() != null ? result.getPermalink() : "")
                        .date(result.getTs() != null ? result.getTs().toString() : "")
                        .dateTs(result.getTs() != null ? LocalDateTime.parse(result.getTs()) : null)
                        .build();
                formattedResults.add(formattedResult);
            }

            return formattedResults;
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

        final var tool = TemplatizedHttpTool.builder()
                .metadata(HttpToolMetadata.builder() //Create tool metadata
                        .name("serviceHealthTool")
                        .description("Get service status")
                        .parameters(Map.of( //Define parameters for the tool
                                "start", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Start time specified in the input", STRING),
                                "end", new HttpToolMetadata.HttpToolParameterMeta(
                                        "End time specified in the input", STRING),
                                "service", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Service name specified in input", STRING)))
                        .build())
                .template(HttpCallTemplate.builder()
                        .method(HttpCallSpec.HttpMethod.POST)
                        .path(HttpCallTemplate.Template.text("/v1/service/widgets/metrics"))
                        .body(HttpCallTemplate.Template.textSubstitutor("{" +
                                "\"widgetRequests\": {" +
                                "\"widgetId1\": {" +
                                "\"widgetType\": \"SERVER_API\"," +
                                "\"serviceName\": \"${service}\"" +
                                "}," +
                                "\"widgetId2\": {" +
                                "\"widgetType\": \"DB_SHARDING_SHARD\"," +
                                "\"serviceName\": \"${service}\"" +
                                "}" +
                                "}," +
                                "\"timeRange\": {" +
                                "\"start\": \"${start}\"," +
                                "\"end\": \"${end}\"" +
                                "}" +
                                "}"))
                        .contentType("application/json")
                        .headers(Map.of(
                                "Authorization", List.of(HttpCallTemplate.Template.textSubstitutor(AUTH_HEADER))))
                        .build())
                .responseTransformations(ResponseTransformerConfig.builder()
                        .type(ResponseTransformerConfig.Type.JOLT)
                        .config("""
                                [
                                   {
                                     "operation": "shift",
                                     "spec": {
                                       "success": "success",
                                       "response": {
                                         "widgetResponse": {
                                           "widgetId1": {
                                             "widgetElements": {
                                               "0": "response.widgetResponse.widgetId.widgetElements[0]",
                                               "1": "response.widgetResponse.widgetId.widgetElements[1]",
                                               "2": "response.widgetResponse.widgetId.widgetElements[2]"
                                             }
                                           },
                                           "widgetId2": {
                                             "widgetElements": {
                                               "0": "response.widgetResponse.widgetId.widgetElements[0]",
                                               "1": "response.widgetResponse.widgetId.widgetElements[1]",
                                               "2": "response.widgetResponse.widgetId.widgetElements[2]",
                                               "3": "response.widgetResponse.widgetId.widgetElements[3]"
                                             }
                                           }
                                         }
                                       }
                                     }
                                   }
                                 ]
                                """)
                        .build())
                .build();

        final var rcaTool = TemplatizedHttpTool.builder()
                .metadata(HttpToolMetadata.builder() //Create tool metadata
                        .name("rcaTool")
                        .description("Get RCA of a widget")
                        .parameters(Map.of( //Define parameters for the tool
                                "start", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Start time specified in the input", STRING),
                                "end", new HttpToolMetadata.HttpToolParameterMeta(
                                        "End time specified in the input", STRING),
                                "service", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Service name specified in input", STRING),
                                "name", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Name of the widget", STRING)))
                        .build())
                .template(HttpCallTemplate.builder()
                        .method(HttpCallSpec.HttpMethod.POST)
                        .path(HttpCallTemplate.Template.text("/v1/graph"))
                        .body(HttpCallTemplate.Template.textSubstitutor("{" +
                                "\"operationType\": \"RCA\"," +
                                "\"opDirection\": \"FORWARD\"," +
                                "\"app\": \"${service}\"," +
                                "\"name\": \"${name}\"," +
                                "\"timeRange\": {" +
                                "\"start\": \"${start}\"," +
                                "\"end\": \"${end}\"" +
                                "}" +
                                "}"))
                        .contentType("application/json")
                        .headers(Map.of(
                                "Authorization", List.of(HttpCallTemplate.Template.textSubstitutor(AUTH_HEADER))))
                        .build())
                .responseTransformations(ResponseTransformerConfig.builder()
                        .type(ResponseTransformerConfig.Type.JOLT)
                        .config("""
                                [
                                  {
                                    "operation": "shift",
                                    "spec": {
                                      "success": "success",
                                      "response": {
                                        "rootNodeId": "response.rootNodeId",
                                        "operationId": "response.operationId",
                                        "nodes": {
                                          "*": {
                                            "nodeRcaData": {
                                              "nodeAnomalyData": {
                                                "*": {
                                                  "0": {
                                                    "anomalyState": {
                                                      "ANOMALOUS": "response.nodes.&6.nodeRcaData.nodeAnomalyData.&4"
                                                    }
                                                  },
                                                  "1": {
                                                    "anomalyState": {
                                                      "ANOMALOUS": "response.nodes.&6.nodeRcaData.nodeAnomalyData.&4"
                                                    }
                                                  },
                                                  "2": {
                                                    "anomalyState": {
                                                      "ANOMALOUS": "response.nodes.&6.nodeRcaData.nodeAnomalyData.&4"
                                                    }
                                                  }
                                                }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }
                                  }
                                ]
                                """)
                        .build())
                .build();

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
                .build()
                .register("spyglass", tool, rcaTool);

        final var spyglassToolBox = new HttpToolBox("spyglass",
                httpClientWithProxy,
                toolSource,
                JsonUtils.createMapper(),
                "http://plt-platdrovee227.phonepe.nbr:32425");

        final var foxtrotToolBox = new HttpToolBox("foxtrot",
                httpClientWithProxy, getFoxtrotToolSource(),
                JsonUtils.createMapper(),
                "http://plt-platdrovee138.phonepe.nbr:25151");


        final OkHttpClient stageHttpClient = new OkHttpClient.Builder()
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

        final var vectorDBToolBox = new HttpToolBox("vectorDB",
                stageHttpClient, getVectorDBSource(),
                JsonUtils.createMapper(),
                "http://severus.nixy.stg-drove.phonepe.nb6");

        agent.registerToolboxes(List.of(vectorDBToolBox, spyglassToolBox, foxtrotToolBox));


        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("""
                        Payment service health
                        """, "payment",
                        1750738920000L, 1750739400000L))
                .requestMetadata(requestMetadata)
                .build());

        log.info("Response: {}", response.getData());
    }

    private InMemoryHttpToolSource getVectorDBSource() {
        final var searchTool = TemplatizedHttpTool.builder()
                .metadata(HttpToolMetadata.builder() //Create tool metadata
                        .name("vectorDBTool")
                        .description("Tool for querying vector DB")
                        .parameters(Map.of( //Define parameters for the tool
                                "query", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Query for vectorDB", STRING)))
                        .build())
                .template(HttpCallTemplate.builder()
                        .method(HttpCallSpec.HttpMethod.POST)
                        .path(HttpCallTemplate.Template.text("/v1/collection/test/search/DC_TEST_AGENTHUB_MAY_2_STG"))
                        .body(HttpCallTemplate.Template.textSubstitutor("{" +
                                "\"query\": {" +
                                "\"type\": \"KNN\"," +
                                "\"field\": \"text\"," +
                                "\"embed\": true," +
                                "\"value\": \"${query}\"," +
                                "\"k\": 10," +
                                "\"numCandidates\": 10000," +
                                "\"boost\": 100.0," +
                                "\"subQuery\": {" +
                                "\"type\": \"BOOL\"," +
                                "\"filterClauses\": [{" +
                                "\"type\": \"TERMS\"," +
                                "\"field\": \"metadata.source_id\"," +
                                "\"values\": [\"fa8c970a74d187435234\"]" +
                                "}]," +
                                "\"mustClauses\": [{" +
                                "\"type\": \"MATCH\"," +
                                "\"field\": \"text\"," +
                                "\"value\": \"${query}\"" +
                                "}]" +
                                "}," +
                                "\"limit\": 5" +
                                "}}"))
                        .contentType("application/json")
                        .headers(Map.of(
                                "Accept", List.of(HttpCallTemplate.Template.text("application/json")),
                                "Authorization", List.of(HttpCallTemplate.Template.textSubstitutor(STAGE_AUTH_HEADER))))
                        .build())
                .build();
        return InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
                .build()
                .register("vectorDB", searchTool);
    }

    private InMemoryHttpToolSource getFoxtrotToolSource() {
        final var widgetTool = TemplatizedHttpTool.builder()
                .metadata(HttpToolMetadata.builder() //Create tool metadata
                        .name("businessMetricWidgetTool")
                        .description("Get widgets for business metrics")
                        .build())
                .template(HttpCallTemplate.builder()
                        .method(HttpCallSpec.HttpMethod.GET)
                        .path(HttpCallTemplate.Template.text("/v1/consoles/section/payments_oncall_dashboard_overall_performance"))
                        .contentType("application/json")
                        .headers(Map.of(
                                "Accept", List.of(HttpCallTemplate.Template.text("application/json")),
                                "Authorization", List.of(HttpCallTemplate.Template.textSubstitutor(AUTH_HEADER))))
                        .build())
                .build();


        HandlebarHelper.registerHelper("getQuery", (context, options) ->
                getQuery(context.toString(),
                        options.param(0),
                        options.param(1)));

        val templateString = """
                {{{getQuery widget start end}}}
                """;

        final var queryTool = TemplatizedHttpTool.builder()
                .metadata(HttpToolMetadata.builder() //Create tool metadata
                        .name("executeFoxtrotQuery")
                        .description("Execute foxtrot query")
                        .parameters(Map.of( //Define parameters for the tool
                                "widget", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Widget data", STRING),
                                "start", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Start time specified in the user input", LONG),
                                "end", new HttpToolMetadata.HttpToolParameterMeta(
                                        "End time specified in the user input", LONG)))
                        .build())
                .template(HttpCallTemplate.builder()
                        .method(HttpCallSpec.HttpMethod.POST)
                        .path(HttpCallTemplate.Template.text("/v2/analytics"))
                        .body(HttpCallTemplate.Template.handlebars(templateString))
//                        .body(HttpCallTemplate.Template.functionCall(this::getQuery))
                        .contentType("application/json")
                        .headers(Map.of(
                                "Authorization", List.of(HttpCallTemplate.Template.textSubstitutor(AUTH_HEADER))))
                        .build())
                .build();

        return InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
                .build()
                .register("foxtrot", widgetTool, queryTool);
    }

    private String test(Object o) {
        return "hey";
    }


    private String getQuery(final String widget,
                            final long start,
                            final long end) {
//        String widgetData = (String) arguments.get("widget");
        String widgetData = widget;

        log.info("Widget Data: {}", widgetData);
        if (widgetData.startsWith("\"") && widgetData.endsWith("\"")) {
            widgetData = widgetData.substring(1, widgetData.length() - 1);
        }

        try {
            val widgetNode = objectMapper.readTree(widgetData);
            val filters = widgetNode.get("filters");
            if (filters.isEmpty()) {
                log.warn("No filters found for widget");
                return null;
            }

//            val startTime = (Long) arguments.get("start") - 3600 * 1000;
//            val endTime = (Long) arguments.get("end");

            val startTime = start - 3600 * 1000;
            val endTime = end;

            if (filters.isArray()) {
                val arrayNode = (ArrayNode) filters;
                val timestampFilterJson = "{" +
                        "\"operator\": \"between\"," +
                        "\"field\": \"_timestamp\"," +
                        "\"temporal\": true," +
                        "\"from\":  \"" + startTime + "\"," +
                        "\"to\":   \"" + endTime + "\"" +
                        "}";
                arrayNode.add(objectMapper.readTree(timestampFilterJson));

            }

            val type = widgetNode.get("type").asText();
            val table = widgetNode.get("table").asText();


            if (Objects.equals(type, "STACKED_LINE")) {
                return "{" +
                        "\"opcode\": \"trend\"," +
                        "\"table\": \"" + table + "\"," +
                        "\"field\": \"" + widgetNode.get("field").asText() + "\"," +
                        "\"requestTags\": { \"serviceName\": \"agent\"}," +
                        "\"sourceType\": \"SERVICE\"," +
                        "\"period\": \"minutes\"," +
                        "\"uniqueCountOn\": \"groupingKey\"," +
                        "\"filters\": " + objectMapper.writeValueAsString(filters) +
                        "}";
            }
        } catch (JsonProcessingException e) {
            log.error("Error processing widget data", e);
        }
        return null;
    }
}
