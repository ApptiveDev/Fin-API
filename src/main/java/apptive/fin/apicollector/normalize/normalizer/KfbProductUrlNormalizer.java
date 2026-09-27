package apptive.fin.apicollector.normalize.normalizer;

import apptive.fin.apicollector.bankurl.BankUrlPolicy;
import apptive.fin.apicollector.bankurl.scraper.BankProductScraper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 은행연합회 공시의 상품 링크를 아웃링크(apply_url)로 저장할 수 있는 형태로 정규화한다.
 *
 * <p>공시 원본에는 쿼리에 공백이 섞인 링크(IBK "?lncd= 01"), 깨진 링크("https://자유입출금상품>예금상품상세 - 우리은행"),
 * 은행 홈페이지만 가리키는 링크(전북은행)가 섞여 있다. 브라우저처럼 URI에 못 쓰는 문자만 인코딩해 살린 뒤,
 * 은행 URL 스크래퍼와 같은 기준(그 은행의 허용 도메인, BankUrlPolicy)으로 검증하고 홈페이지 링크는 버린다.
 * 버린 상품은 은행 URL 스크래핑(BankProductUrlTasklet)이 채운다.
 */
@Component
public class KfbProductUrlNormalizer {

    private static final String URI_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%";

    private final Map<String, Set<String>> allowedDomainsByProviderCode;

    public KfbProductUrlNormalizer(List<BankProductScraper> scrapers) {
        this.allowedDomainsByProviderCode = scrapers.stream().collect(Collectors.toUnmodifiableMap(
                BankProductScraper::providerCode,
                BankProductScraper::allowedDomains
        ));
    }

    public Optional<String> normalize(String providerCode, String href) {
        // 스크래퍼가 없는 은행은 도메인을 확인할 수 없어 링크를 받지 않는다.
        Set<String> allowedDomains = allowedDomainsByProviderCode.get(providerCode);
        if (allowedDomains == null || href == null || href.isBlank()) {
            return Optional.empty();
        }
        String encoded = encodeIllegalUriChars(href.trim());
        if (BankUrlPolicy.validationError(encoded, allowedDomains).isPresent() || isHomePage(encoded)) {
            return Optional.empty();
        }
        return Optional.of(encoded);
    }

    // BankUrlPolicy가 파싱할 수 있다고 확인한 URL만 받는다.
    private static boolean isHomePage(String url) {
        URI uri = URI.create(url);
        String path = uri.getRawPath();
        return (path == null || path.isEmpty() || path.equals("/")) && uri.getRawQuery() == null;
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
