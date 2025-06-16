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
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiYWE5ZjI4NGEtOTI5YS00MGFiLTgyMDQtMjBmZTRjYzY4ZWVlIiwic2lkIjoiMDc4Y2Y5OTItOWE2MS00MTNlLThkOTEtMjVkM2U4OTgzMWZlIiwiaWF0IjoxNzQ5NTM4NTE0LCJleHAiOjE3NDk2MjQ5MTR9.gNXwy95xrSHOfCtVxdFshxNpv2CCboRUEkUY7p77XQMG6DHd9R90YPB9ln0DzMs5K4_84he2cooJWRXuWdQLBg";

    public static final String STAGE_AUTH_HEADER =
"O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiYTFmMjUyNDctMDdhZS00M2Y1LWJjMTctYjEwN2Y3NDgzZTI1Iiwic2lkIjoiN2NmYzBjNzUtOTQ5ZC00OTczLWFiMDktZDhiOTg5NDEwY2M3IiwiaWF0IjoxNzQ5NTQwODMzLCJleHAiOjE3NTAxNDU2MzJ9.OENcfLIG-S07SxQOE2slpImKzZnRuNEUVCdgALTj2EMZPWonCm9I4hdEDYuadT9gW1D7su9HgtEWCFpT5aQ0RQ";
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
