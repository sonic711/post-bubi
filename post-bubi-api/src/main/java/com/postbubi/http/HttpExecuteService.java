package com.postbubi.http;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.storage.FileStorageService;
import com.postbubi.web.dto.HttpExecuteRequest;
import com.postbubi.web.dto.HttpExecuteResponse;
import com.postbubi.web.dto.HttpFormDataPart;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.error.ApiException;

@Service
public class HttpExecuteService {

    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int DEFAULT_TIMEOUT_MILLIS = 30000;
    private static final int MAX_TIMEOUT_MILLIS = 300000;

    private final FileStorageService fileStorageService;
    private final RequestHistoryService requestHistoryService;

    public HttpExecuteService(FileStorageService fileStorageService, RequestHistoryService requestHistoryService) {
        this.fileStorageService = fileStorageService;
        this.requestHistoryService = requestHistoryService;
    }

    public HttpExecuteResponse execute(HttpExecuteRequest request, ExecutionHandle execution) {
        String method = normalizeMethod(request.method());
        URI uri = buildUri(request.url(), request.params());
        int timeoutMillis = normalizeTimeout(request.timeoutMillis());

        HttpUriRequestBase httpRequest = new HttpUriRequestBase(method, uri);
        applyHeaders(httpRequest, request.headers(), request.bodyType());
        applyBody(httpRequest, method, request.bodyType(), request.body(), request.formData());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMillis))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMillis))
                .setRedirectsEnabled(Boolean.TRUE.equals(request.followRedirects()))
                .build();

        long startNanos = System.nanoTime();
        try (CloseableHttpClient client = createClient(Boolean.TRUE.equals(request.ignoreSslVerification()), requestConfig)) {
            execution.registerCancellationAction(() -> closeQuietly(client));
            throwIfCancelled(execution);
            HttpExecuteResponse executeResponse = client.execute(httpRequest, response -> {
                byte[] bodyBytes = readBody(response.getEntity());
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                return new HttpExecuteResponse(
                        response.getCode(),
                        response.getReasonPhrase(),
                        durationMillis,
                        (long) bodyBytes.length,
                        toHeaders(response.getHeaders()),
                        new String(bodyBytes, StandardCharsets.UTF_8),
                        false
                );
            });
            requestHistoryService.record(request, executeResponse, null);
            return executeResponse;
        } catch (ApiException exception) {
            recordCancellationIfNeeded(request, execution);
            throw exception;
        } catch (Exception exception) {
            if (execution.isCancelled()) {
                requestHistoryService.record(request, null, "HTTP 請求已由使用者取消。");
                throw cancellationException();
            }
            requestHistoryService.record(request, null, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "HTTP_EXECUTE_FAILED",
                    "HTTP 請求執行失敗。",
                    java.util.Map.of("reason", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
            );
        }
    }

    private void throwIfCancelled(ExecutionHandle execution) {
        if (execution.isCancelled()) {
            throw cancellationException();
        }
    }

    private void recordCancellationIfNeeded(HttpExecuteRequest request, ExecutionHandle execution) {
        if (execution.isCancelled()) {
            requestHistoryService.record(request, null, "HTTP 請求已由使用者取消。");
        }
    }

    private void closeQuietly(CloseableHttpClient client) {
        try {
            client.close();
        } catch (IOException ignored) {
            // The execution thread will handle the original cancellation result.
        }
    }

    private ApiException cancellationException() {
        return new ApiException(HttpStatus.CONFLICT, "HTTP_REQUEST_CANCELLED", "HTTP 請求已取消。");
    }

    private String normalizeMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            throw badRequest("HTTP_METHOD_REQUIRED", "HTTP method 不可空白。");
        }
        String normalized = method.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(normalized)) {
            throw badRequest("HTTP_METHOD_NOT_SUPPORTED", "目前不支援指定的 HTTP method。");
        }
        return normalized;
    }

    private URI buildUri(String url, List<HttpNameValue> params) {
        if (url == null || url.trim().isEmpty()) {
            throw badRequest("HTTP_URL_REQUIRED", "URL 不可空白。");
        }
        try {
            URIBuilder builder = new URIBuilder(url.trim());
            for (HttpNameValue param : enabledEntries(params)) {
                builder.addParameter(param.name().trim(), param.value() == null ? "" : param.value());
            }
            URI uri = builder.build();
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw badRequest("HTTP_URL_INVALID", "URL 必須包含 scheme 與 host，例如 http://localhost:18080/api/health。");
            }
            return uri;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("HTTP_URL_INVALID", "URL 格式錯誤。");
        }
    }

    private void applyHeaders(HttpUriRequestBase request, List<HttpNameValue> headers, String bodyType) {
        boolean multipart = "form-data".equals(normalizeBodyType(bodyType));
        for (HttpNameValue header : enabledEntries(headers)) {
            if (multipart && "content-type".equals(header.name().trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            request.addHeader(header.name().trim(), header.value() == null ? "" : header.value());
        }
    }

    private void applyBody(HttpUriRequestBase request, String method, String bodyType, String body, List<HttpFormDataPart> formData) {
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return;
        }

        String normalizedBodyType = normalizeBodyType(bodyType);
        String content = body == null ? "" : body;
        switch (normalizedBodyType) {
            case "none" -> {
            }
            case "json" -> request.setEntity(new StringEntity(content, ContentType.APPLICATION_JSON));
            case "raw" -> request.setEntity(new StringEntity(content, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)));
            case "x-www-form-urlencoded" -> request.setEntity(new StringEntity(content, ContentType.APPLICATION_FORM_URLENCODED.withCharset(StandardCharsets.UTF_8)));
            case "form-data" -> request.setEntity(buildMultipartEntity(formData));
            default -> throw badRequest("HTTP_BODY_TYPE_NOT_SUPPORTED", "目前不支援指定的 body type。");
        }
    }

    private HttpEntity buildMultipartEntity(List<HttpFormDataPart> formData) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        int partCount = 0;
        for (HttpFormDataPart part : enabledFormDataParts(formData)) {
            String name = part.name().trim();
            String type = normalizeFormDataType(part.type());
            if ("file".equals(type)) {
                Path file = fileStorageService.findUploadedFile(part.fileId());
                String filename = part.fileName() == null || part.fileName().trim().isEmpty()
                        ? file.getFileName().toString()
                        : part.fileName().trim();
                builder.addBinaryBody(name, file.toFile(), resolveContentType(part.contentType(), file), filename);
            } else {
                builder.addTextBody(name, part.value() == null ? "" : part.value(), ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
            }
            partCount++;
        }
        if (partCount == 0) {
            throw badRequest("HTTP_FORM_DATA_REQUIRED", "form-data 至少需要一個啟用的欄位。");
        }
        return builder.build();
    }

    private CloseableHttpClient createClient(boolean ignoreSslVerification, RequestConfig requestConfig) throws Exception {
        if (!ignoreSslVerification) {
            return HttpClients.custom()
                    .disableAutomaticRetries()
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        }

        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial((chain, authType) -> true)
                .build();
        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();

        return HttpClients.custom()
                .disableAutomaticRetries()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    private byte[] readBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        return EntityUtils.toByteArray(entity);
    }

    private List<HttpNameValue> toHeaders(Header[] headers) {
        List<HttpNameValue> result = new ArrayList<>();
        for (Header header : headers) {
            result.add(new HttpNameValue(header.getName(), header.getValue(), true));
        }
        return result;
    }

    private int normalizeTimeout(Integer timeoutMillis) {
        int timeout = timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
        if (timeout <= 0 || timeout > MAX_TIMEOUT_MILLIS) {
            throw badRequest("HTTP_TIMEOUT_INVALID", "Timeout 必須介於 1 到 300000 毫秒。");
        }
        return timeout;
    }

    private String normalizeBodyType(String bodyType) {
        return bodyType == null ? "none" : bodyType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFormDataType(String type) {
        String normalized = type == null ? "text" : type.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("text", "file").contains(normalized)) {
            throw badRequest("HTTP_FORM_DATA_TYPE_NOT_SUPPORTED", "form-data 欄位類型只支援 text 或 file。");
        }
        return normalized;
    }

    private ContentType resolveContentType(String contentType, Path file) {
        String value = contentType;
        try {
            if (value == null || value.trim().isEmpty()) {
                value = Files.probeContentType(file);
            }
        } catch (IOException ignored) {
            value = null;
        }
        if (value == null || value.trim().isEmpty()) {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
        return ContentType.parse(value.trim());
    }

    private List<HttpNameValue> enabledEntries(List<HttpNameValue> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry != null && !Boolean.FALSE.equals(entry.enabled()))
                .filter(entry -> entry.name() != null && !entry.name().trim().isEmpty())
                .toList();
    }

    private List<HttpFormDataPart> enabledFormDataParts(List<HttpFormDataPart> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry != null && !Boolean.FALSE.equals(entry.enabled()))
                .filter(entry -> entry.name() != null && !entry.name().trim().isEmpty())
                .toList();
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
