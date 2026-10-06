package apptive.fin.apicollector.normalize.normalizer;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 은행연합회 공시의 상품 링크를 아웃링크(apply_url)로 저장할 수 있는 형태로 정규화한다.
 *
 * <p>공시 원본에는 쿼리에 공백이 섞인 링크(IBK "?lncd= 01")와 깨진 링크
 * ("https://자유입출금상품>예금상품상세 - 우리은행")가 함께 있다. 브라우저처럼 URI에 못 쓰는 문자만 인코딩해 살리고,
 * 인코딩하면 깨진 링크도 문법상 유효해지므로 호스트는 도메인 형식인지 따로 본다.
 */
@Component
public class KfbProductUrlNormalizer {

    private static final int APPLY_URL_MAX_LENGTH = 500; // product_properties.apply_url VARCHAR(500)
    // 마지막 라벨(TLD)을 영문자로 제한해 IP 주소 호스트(127.0.0.1 등)는 받지 않는다.
    private static final Pattern DOMAIN = Pattern.compile("^([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");
    private static final String URI_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%";

    public Optional<String> normalize(String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        String encoded = encodeIllegalUriChars(href.trim());
        return isStorableHttpUrl(encoded) ? Optional.of(encoded) : Optional.empty();
    }

    private static boolean isStorableHttpUrl(String url) {
        if (url.length() > APPLY_URL_MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            boolean isHttp = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            return isHttp && uri.getHost() != null && DOMAIN.matcher(uri.getHost()).matches();
        }
        catch (URISyntaxException e) {
            return false;
        }
    }

    // 이미 인코딩된 %xx와 예약 문자는 그대로 두어 이중 인코딩을 피한다.
    private static String encodeIllegalUriChars(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (codePoint < 128 && URI_CHARS.indexOf(codePoint) >= 0) {
                result.appendCodePoint(codePoint);
                return;
            }
            for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
                result.append('%').append(String.format("%02X", b & 0xFF));
            }
        });
        return result.toString();
    }
}
