package com.phonepe.sentinelai.mcp.debugging;

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
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiOGIyMDJmNzUtY2Y4OC00ODFiLWJjOGUtYWE2ZWI5YTA1NTZlIiwic2lkIjoiNTQ5NmY3NjYtOTBhZS00OGI4LWFkYjUtMDMyZWIyZWI4M2M2IiwiaWF0IjoxNzQ2OTA1MjIxLCJleHAiOjE3NDc1MTAwMjB9.GCy-KqGe8_N9OjC6kSLvpFd2Hzz8zvnlY2YAD6m759NanB725lmlztoq4DKV7BFJ0lv3ZHIICXVB9m2eBKZUGg";
    public static final String STAGE_AUTH_HEADER =
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiYmEzOGEwMGItODJkYi00NjgwLWFkYjMtMDUyNzk5NzgxZGE5Iiwic2lkIjoiMGJmOWQzMGUtYzFjNi00MWY5LTg0Y2EtMWFlMzU2MzY1NTIyIiwiaWF0IjoxNzQ3MTk0Njg4LCJleHAiOjE3NDc3OTk0ODh9.GM5kpm5KH4ONCmIl4zrvqy9BT4u4iMI-O8VHoEdVurnynbcjWjoSnNYiAd-j3hnsjS9w1pIyl2HKBUqCw4D4hA";

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
