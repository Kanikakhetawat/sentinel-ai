package configuredagents.debuggingagent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.ResponseTransformerConfig;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.HandlebarHelper;
import configuredagents.AgentConfiguration;
import configuredagents.AgentRegistry;
import configuredagents.ConfiguredAgentFactory;
import configuredagents.HttpToolboxFactory;
import configuredagents.InMemoryAgentConfigurationSource;
import configuredagents.MCPToolBoxFactory;
import configuredagents.capabilities.AgentCapabilities;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.LONG;
import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.STRING;

@Slf4j
public class DebuggingAgentTest {

    public static final String AUTH_HEADER =
            "";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final class PlannerAgent extends Agent<UserInput, String, PlannerAgent> {
        @Builder
        public PlannerAgent(@NonNull AgentSetup setup, @Singular List<AgentExtension<UserInput, String, PlannerAgent>> extensions) {
            super(String.class,
                    """
                           You are a debugging agent. You need to find the root cause of the issue and provide a solution.
                           You can use other agents to achieve this.
                           Extract the `startTime` and `endTime` values from the `UserInput` and pass both the values to other agents as input
                          
                           Combine all the above results from agents and return response in following manner.
                              a. Service level degradation:
                                   a. Server_api which are degraded and the deviation values along with root cause from rca tool
                              b. Business level degradation
                                   (i). Metrics which are anomalous. Show the usual values and the current value with the deviation
                                   (ii). Vector DB and slack insights along with relevant links or dashboards
                          
                          """,
                    setup,
                    extensions,
                    Map.of());
        }

        @Override
        public String name() {
            return "planner-agent";
        }
    }

    @JsonClassDescription("User input")
    public record UserInput(String data, String service, long startTime, long endTime) {
    }


    @SneakyThrows
    @Test
    void test() {
        final var agentSource = new InMemoryAgentConfigurationSource();
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

        final var httpClientWithProxy = new OkHttpClient.Builder()
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

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
                .build();
        toolSource.register("spyglass", getSpyglassTools());
        toolSource.register("foxtrot", getFoxtrotTools());


        Map<String, UpstreamResolver> upstreamResolvers = ImmutableMap.of(
                "spyglass", UpstreamResolver.direct("http://plt-platdrovee227.phonepe.nbr:32425"),
                "foxtrot", UpstreamResolver.direct("http://plt-platdrovee138.phonepe.nbr:25151")
        );

        // Function that resolves based on ID
        Function<String, UpstreamResolver> upstreamResolver = id -> {
            UpstreamResolver resolver = upstreamResolvers.get(id);
            if (resolver == null) {
                throw new IllegalArgumentException("No UpstreamResolver for id: " + id);
            }
            return resolver;
        };


        final var agentFactory = ConfiguredAgentFactory.builder()
                .httpToolboxFactory(new HttpToolboxFactory(httpClientWithProxy,
                        objectMapper,
                        toolSource,
                        upstreamResolver))
                .mcpToolboxFactory(MCPToolBoxFactory.builder()
                        .objectMapper(objectMapper)
                        .clientProvider(upstream -> {
                            throw new IllegalStateException("MCP is not supported in this test");
                        })
                        .build())
                .build();


        final var registry = AgentRegistry.<UserInput, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();

        registry.configureAgent(AgentConfiguration.builder()
                .agentName("Spyglass Agent")
                .description("Provides the service health and the root causes for the service issues.")
                .prompt("""
                       For all tools, use the `startTime`, `endTime` and service values from the agent input.

                        1. Run ServiceHealth tool to identify any issues with the service.
                        2. Run RCA tool to identify the root cause for each anomalous widget
                       """)
                .inputSchema(loadSchema(objectMapper, "debugging_agent_input_schema.json"))
                .outputSchema(loadSchema(objectMapper, "spyglass_agent_output_schema.json"))
                .capability(AgentCapabilities
                        .remoteHttpCalls(Map.of("spyglass", Set.of())))
                .build());

        registry.configureAgent(AgentConfiguration.builder()
                .agentName("Foxtrot Agent")
                .description("Provides business metrics for the service.")
                .prompt("""
                        1. Use `businessMetricWidgetTool` to fetch all business metric widgets.
                        2. Extract all the widget data associated with "widgets" key from the above json response.
                        3. Select the widgets using the following pseudocode logic:
                                        for widget in widgets:
                                           if widget["layout"]["coordinateY"] <= 10 and widget["type"] in ["STACKED_LINE"]:
                                                   Include this widget entire data in the next step
                        4. After step 3, For each selected widget, **call `executeFoxtrotQuery`** with the complete widget data.
                        """)
                .inputSchema(loadSchema(objectMapper, "debugging_agent_input_schema.json"))
                .capability(AgentCapabilities
                        .remoteHttpCalls(Map.of("foxtrot", Set.of())))
                .build());

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
        final var setup = AgentSetup.builder()
                .mapper(objectMapper)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();
        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<UserInput>builder()
                .request(new UserInput("""
                        Figure out the issue with the payment service.
                        """, "payment",
                        1753328100000L, 1753328411000L))
                        .build())
                .join();
        log.info("Agent response: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response.getData()));
    }

    private List<TemplatizedHttpTool> getSpyglassTools() {
        final var tool = TemplatizedHttpTool.builder()
                .metadata(HttpToolMetadata.builder() //Create tool metadata
                        .name("serviceHealthTool")
                        .description("Get service status")
                        .parameters(Map.of( //Define parameters for the tool
                                "start", new HttpToolMetadata.HttpToolParameterMeta(
                                        "Start time specified in the agent input", STRING),
                                "end", new HttpToolMetadata.HttpToolParameterMeta(
                                        "End time specified in the agent input", STRING),
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
                                        "Start time specified in the agent input", STRING),
                                "end", new HttpToolMetadata.HttpToolParameterMeta(
                                        "End time specified in the agent input", STRING),
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
        return List.of(tool, rcaTool);
    }

    private List<TemplatizedHttpTool> getFoxtrotTools() {
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
        return List.of(widgetTool, queryTool);
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


    @SneakyThrows
    private JsonNode loadSchema(ObjectMapper mapper, String schemaFilename) {
        val jsonNode = mapper.readTree(Files.readString(Path.of(Objects.requireNonNull(getClass().getResource(
                "/schema/%s".formatted(schemaFilename))).toURI())));
        return jsonNode;
    }
}
