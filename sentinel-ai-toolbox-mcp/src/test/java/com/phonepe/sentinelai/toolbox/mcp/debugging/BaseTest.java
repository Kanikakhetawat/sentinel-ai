package com.phonepe.sentinelai.toolbox.mcp.debugging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

public class BaseTest {

    public static final String AUTH_HEADER =
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiZjY1NzcxNDYtMzU2Mi00ZTY3LWEyZGItYjNlMDQyOTQ2Mzc5Iiwic2lkIjoiOWVkNzZiMjUtMGY1OC00MzJlLTg0MjktMWRjYmFiZTg0M2QzIiwiaWF0IjoxNzQ4ODQwOTIzLCJleHAiOjE3NDg5MjczMjN9.z3LwkOgMHy_H16etmCRESzru3seIKAqwga5n3EvosxGWTso6mo9pcQeHRhFv4_2hd_IEWLje8xY3qZjWCpIOzA";

    public static final String STAGE_AUTH_HEADER =
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiOTNhYzFjMjktZjFlYi00NjE2LTk5MGUtMTlkOTdlNDQwZTJiIiwic2lkIjoiYjlkNGM4Y2EtZGVlYS00ZDI3LWI2YzYtMTAyZGE0MTllNWVmIiwiaWF0IjoxNzQ4ODU4MzkwLCJleHAiOjE3NDk0NjMxODl9.iPcE8X1n19kRnuMguKZp4TDax7TO0ziF7ZzjN8_IZanGwd9o3nPy5L91-3OF-5e-H4pRFp1BDwW-B7GUOnHInw";

    public static OkHttpClient httpClientWithProxy;
    public static final ObjectMapper objectMapper = JsonUtils.createMapper();


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

}
