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
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiNWI4Nzk2ODctYmE0Mi00MTliLWI0NjQtMWFkNTM0MzRjMTA1Iiwic2lkIjoiZGRlNTJiNDktNzY4NC00ODM5LWFiYzItNGMzMDZkYjAxY2M0IiwiaWF0IjoxNzQ1OTQzNjA4LCJleHAiOjE3NDYwMzAwMDd9.MUTWnd3KZRt8qruEY7B5Vju-IrYO6JOR4hSajRpbv_SyLSoTxalBumzTQoiPI_MwmNCgM0soB9HAanD5kZolgA";

    public static final String STAGE_AUTH_HEADER =
            "O-Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJpZGVudGl0eU1hbmFnZXIiLCJ2ZXJzaW9uIjoiNC4wIiwidGlkIjoiM2QzODliMzAtMTZkOS00M2RhLTgzNTgtYjEzMWMwZTAzMmY1Iiwic2lkIjoiOTk4ZmFhMDUtM2QzNy00YWVjLWI0MGYtNDU3NTIwMzljZWM5IiwiaWF0IjoxNzQ1OTgyMTQ0LCJleHAiOjE3NDY1ODY5NDR9.SrLgmOHdcBffg71KIkMcnfhQR5fuvhlZbEV8lxUWoUQgBuUIVhgmaIdPRB1PN_YhHGv1PHrI_leIcSgol6CA_w";

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
