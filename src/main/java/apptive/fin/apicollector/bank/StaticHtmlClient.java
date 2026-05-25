package apptive.fin.apicollector.bank;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class StaticHtmlClient {

    public FetchedPage fetch(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 api-collector/1.0")
                    .timeout(10_000)
                    .get();
            String html = document.html();
            return new FetchedPage(
                    url,
                    document.title(),
                    document.text().replaceAll("\\s+", " ").trim(),
                    html,
                    sha256(html)
            );
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to fetch static HTML. url=" + url, e);
        }
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
}
