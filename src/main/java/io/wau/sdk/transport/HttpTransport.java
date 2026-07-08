package io.wau.sdk.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.wau.sdk.exception.RateLimitException;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.exception.WauNotFoundException;
import io.wau.sdk.exception.WauServerException;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * HttpTransport — OkHttp 封装的 HTTP REST 客户端(per D63 §4.1.1)。
 *
 * <p>设计:
 * <ul>
 *   <li>OkHttp 4 + Jackson 2.17(per R24-Java BOM 缓解)</li>
 *   <li>统一异常映射:4xx → {@link WauNotFoundException} / 429 → {@link RateLimitException} / 5xx → {@link WauServerException}</li>
 *   <li>JSON encode/decode 用 {@link ObjectMapper}(Java 8 time module 启用,per {@code Instant})</li>
 *   <li>callable 接口 {@link Sender} 给 mock 用(测试无需 OkHttp MockWebServer)</li>
 * </ul>
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public final class HttpTransport {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final Sender sender; // for testing — null 表示用真 OkHttp

    /** Injectable sender(给 test mock 用,生产 = null). */
    public interface Sender {
        Response doRequest(Request req) throws IOException;
    }

    public HttpTransport() {
        this(null, defaultClient());
    }

    public HttpTransport(Sender sender) {
        this(sender, defaultClient());
    }

    private HttpTransport(Sender sender, OkHttpClient client) {
        this.sender = sender;
        this.client = client;
        this.mapper = newMapper();
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static ObjectMapper newMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return m;
    }

    public ObjectMapper mapper() { return mapper; }
    public OkHttpClient client() { return client; }

    // === GET / POST / PUT / DELETE helpers ===

    public <T> T get(String url, Map<String, String> headers, TypeReference<T> typeRef) {
        return execute(buildRequest(url, "GET", headers, null), typeRef);
    }

    public <T> T get(String url, Map<String, String> headers, Class<T> type) {
        return execute(buildRequest(url, "GET", headers, null), type);
    }

    public <T> T postJson(String url, Map<String, String> headers, Object body, Class<T> type) {
        return execute(buildRequest(url, "POST", headers, body), type);
    }

    public <T> T postJson(String url, Map<String, String> headers, Object body, TypeReference<T> typeRef) {
        return execute(buildRequest(url, "POST", headers, body), typeRef);
    }

    public <T> T putJson(String url, Map<String, String> headers, Object body, Class<T> type) {
        return execute(buildRequest(url, "PUT", headers, body), type);
    }

    public void delete(String url, Map<String, String> headers) {
        executeNoBody(buildRequest(url, "DELETE", headers, null));
    }

    // === Request building + execution ===

    private Request buildRequest(String url, String method, Map<String, String> headers, Object body) {
        Objects.requireNonNull(url, "url");
        Request.Builder b = new Request.Builder().url(url);
        Headers.Builder hb = new Headers.Builder();
        hb.add("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                hb.add(e.getKey(), e.getValue());
            }
        }
        b.headers(hb.build());
        if (body != null) {
            try {
                String json = mapper.writeValueAsString(body);
                b.method(method, RequestBody.create(json, JSON));
            } catch (Exception e) {
                throw new WauException("failed to serialize body to JSON", e);
            }
        } else {
            b.method(method, null);
        }
        return b.build();
    }

    private <T> T execute(Request req, Class<T> type) {
        try (Response resp = doCall(req)) {
            validateStatus(resp);
            ResponseBody rb = resp.body();
            if (rb == null) return null;
            try {
                byte[] bytes = rb.bytes();
                if (bytes.length == 0) return null;
                return mapper.readValue(bytes, type);
            } catch (IOException e) {
                throw new WauException("failed to read response", e);
            }
        }
    }

    private <T> T execute(Request req, TypeReference<T> typeRef) {
        try (Response resp = doCall(req)) {
            validateStatus(resp);
            ResponseBody rb = resp.body();
            if (rb == null) return null;
            try {
                byte[] bytes = rb.bytes();
                if (bytes.length == 0) return null;
                return mapper.readValue(bytes, typeRef);
            } catch (IOException e) {
                throw new WauException("failed to read response", e);
            }
        }
    }

    private void executeNoBody(Request req) {
        try (Response resp = doCall(req)) {
            validateStatus(resp);
        }
    }

    private Response doCall(Request req) {
        try {
            if (sender != null) return sender.doRequest(req);
            return client.newCall(req).execute();
        } catch (IOException e) {
            throw new WauException("http call failed: " + e.getMessage(), e);
        }
    }

    private void validateStatus(Response resp) {
        int code = resp.code();
        if (code >= 200 && code < 300) return;
        String body = "";
        try (ResponseBody rb = resp.body()) {
            if (rb != null) body = rb.string();
        } catch (IOException ignored) {}
        switch (code) {
            case 401:
            case 403:
                throw new io.wau.sdk.exception.AuthException("status " + code + ": " + body);
            case 404:
                throw new WauNotFoundException("not found: " + body);
            case 429:
                throw new RateLimitException("rate limited: " + body);
            default:
                if (code >= 500) {
                    throw new WauServerException(code, "upstream error " + code + ": " + body);
                }
                throw new WauException(code, "http error " + code + ": " + body);
        }
    }
}
