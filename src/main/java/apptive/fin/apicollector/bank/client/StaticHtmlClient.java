package apptive.fin.apicollector.bank.client;

import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class StaticHtmlClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "Mozilla/5.0 api-collector/1.0";

    private final HttpClient httpClient;

    public StaticHtmlClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    StaticHtmlClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public FetchedPage fetch(String url) {
        return join(fetchAsync(url), "Failed to fetch static HTML. url=" + url);
    }

    public CompletableFuture<FetchedPage> fetchAsync(String url) {
        HttpRequest request = requestBuilder(url).GET().build();
        return sendPage(request, "Failed to fetch static HTML. url=" + url);
    }

    public FetchedPageWithCookies fetchWithResponseCookies(String url) {
        return join(fetchWithResponseCookiesAsync(url), "Failed to fetch static HTML. url=" + url);
    }

    public CompletableFuture<FetchedPageWithCookies> fetchWithResponseCookiesAsync(String url) {
        HttpRequest request = requestBuilder(url).GET().build();
        return send(request, "Failed to fetch static HTML. url=" + url)
                .thenApply(response -> new FetchedPageWithCookies(
                        toFetchedPage(response),
                        responseCookies(response)
                ));
    }

    public FetchedPage post(String url, Map<String, String> data) {
        return post(url, data, Map.of(), Map.of());
    }

    public CompletableFuture<FetchedPage> postAsync(String url, Map<String, String> data) {
        return postAsync(url, data, Map.of(), Map.of());
    }

    public FetchedPage post(
            String url,
            Map<String, String> data,
            Map<String, String> headers,
            Map<String, String> cookies
    ) {
        return join(postAsync(url, data, headers, cookies), "Failed to post static HTML. url=" + url);
    }

    public CompletableFuture<FetchedPage> postAsync(
            String url,
            Map<String, String> data,
            Map<String, String> headers,
            Map<String, String> cookies
    ) {
        HttpRequest.Builder builder = requestBuilder(url)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(data), StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookieHeader(cookies));
        }
        return sendPage(builder.build(), "Failed to post static HTML. url=" + url);
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT);
    }

    private CompletableFuture<FetchedPage> sendPage(HttpRequest request, String errorMessage) {
        return send(request, errorMessage).thenApply(this::toFetchedPage);
    }

    private CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request, String errorMessage) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException(errorMessage + ", status=" + response.statusCode());
                    }
                    return response;
                })
                .exceptionally(e -> {
                    throw new CompletionException(new IllegalStateException(errorMessage, unwrap(e)));
                });
    }

    private FetchedPage toFetchedPage(HttpResponse<byte[]> response) {
        return toFetchedPage(response.uri().toString(), parse(response));
    }

    private FetchedPage toFetchedPage(String url, Document document) {
        String html = document.html();
        return new FetchedPage(
                url,
                document.title(),
                document.text().replaceAll("\\s+", " ").trim(),
                html,
                sha256(html)
        );
    }

    private Document parse(HttpResponse<byte[]> response) {
        try {
            return Jsoup.parse(
                    new ByteArrayInputStream(response.body()),
                    charsetName(response),
                    response.uri().toString()
            );
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to parse static HTML. url=" + response.uri(), e);
        }
    }

    private String charsetName(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(String::toLowerCase)
                .map(contentType -> {
                    int charsetIndex = contentType.indexOf("charset=");
                    if (charsetIndex < 0) {
                        return null;
                    }
                    return contentType.substring(charsetIndex + "charset=".length())
                            .split(";")[0]
                            .trim()
                            .replace("\"", "");
                })
                .orElse(null);
    }

    private String formBody(Map<String, String> data) {
        StringBuilder body = new StringBuilder();
        data.forEach((key, value) -> {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return body.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String cookieHeader(Map<String, String> cookies) {
        StringBuilder header = new StringBuilder();
        cookies.forEach((name, value) -> {
            if (!header.isEmpty()) {
                header.append("; ");
            }
            header.append(name).append('=').append(value);
        });
        return header.toString();
    }

    private Map<String, String> responseCookies(HttpResponse<?> response) {
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String header : response.headers().allValues("Set-Cookie")) {
            for (HttpCookie cookie : HttpCookie.parse(header)) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        return Map.copyOf(cookies);
    }

    private <T> T join(CompletableFuture<T> future, String errorMessage) {
        try {
            return future.join();
        }
        catch (CompletionException e) {
            Throwable cause = unwrap(e);
            if (cause instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException(errorMessage, cause);
        }
    }

    private Throwable unwrap(Throwable e) {
        if (e instanceof CompletionException && e.getCause() != null) {
            return unwrap(e.getCause());
        }
        return e;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to calculate hash", e);
        }
    }

    public record FetchedPage(
            String url,
            String title,
            String text,
            String html,
            String sourceHash
    ) {
    }

    public record FetchedPageWithCookies(
            FetchedPage page,
            Map<String, String> cookies
    ) {
    }
}
