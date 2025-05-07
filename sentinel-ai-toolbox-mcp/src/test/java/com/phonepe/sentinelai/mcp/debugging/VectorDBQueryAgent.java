package com.phonepe.sentinelai.mcp.debugging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.net.HttpHeaders;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.TermsQuery;
import com.phonepe.commons.columbus.search.query.text.MatchQuery;
import com.phonepe.dataplatform.severus.model.columbus.KnnQuery;
import com.phonepe.dataplatform.severus.model.request.DocSearchRequest;
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

@Slf4j
public class VectorDBQueryAgent extends BaseTest {

    @BeforeEach
    void setup() {
        super.setup();
    }

    private static final class TestAgent extends Agent<String, VectorDBAgentResponse, TestAgent> {

        @Builder
        public TestAgent(@NonNull AgentSetup agentSetup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(VectorDBAgentResponse.class,
                    """
                            1. Your job is to query vector store using the tool provided
                            2. Summarize the vector DB results in following manner:
                               a. List down the possible reasons of the issue mentioned in the results
                               b. List down the next steps which can be taken for addressing the issue
                               c. List down the dashboards/links mentioned to be checked
                            """,
                    agentSetup, extensions, tools);
        }

        @Override
        public String name() {
            return "query-vector-db-agent";
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
            val searchResponse = objectMapper.readValue(responseString, new TypeReference<com.phonepe.dataplatform.severus.model.response.SearchResponse>() {
            });
            log.info("Search response: {}", searchResponse);
            return searchResponse;
        }
    }


    @Test
    @SneakyThrows
    VectorDBAgentResponse test(final String query) {
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

        final var response = agent.execute(AgentInput.<String>builder()
                .request(query)
                .requestMetadata(requestMetadata)
                .build());

        log.info("VectorDBQueryAgent response: {}", response.getData());
        return response.getData();
    }
}
