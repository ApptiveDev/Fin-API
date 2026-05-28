package apptive.fin.apicollector.bank.client;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
public class StaticHtmlClient {

    public FetchedPage fetch(String url) {
        try {
            return toFetchedPage(url, connection(url).get());
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to fetch static HTML. url=" + url, e);
        }
    }

    public FetchedPageWithCookies fetchWithResponseCookies(String url) {
        try {
            Connection.Response response = connection(url).execute();
            return new FetchedPageWithCookies(
                    toFetchedPage(response.url().toString(), response.parse()),
                    Map.copyOf(response.cookies())
            );
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to fetch static HTML. url=" + url, e);
        }
    }

    public FetchedPage post(String url, Map<String, String> data) {
        return post(url, data, Map.of(), Map.of());
    }

    public FetchedPage post(
            String url,
            Map<String, String> data,
            Map<String, String> headers,
            Map<String, String> cookies
    ) {
        try {
            Document document = connection(url)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .headers(headers)
                    .cookies(cookies)
                    .data(data)
                    .post();
            return toFetchedPage(url, document);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to post static HTML. url=" + url, e);
        }
    }

    private Connection connection(String url) {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 api-collector/1.0")
                .ignoreContentType(true)
                .timeout(10_000);
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
