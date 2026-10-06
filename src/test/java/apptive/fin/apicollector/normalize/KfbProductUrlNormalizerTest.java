package apptive.fin.apicollector.normalize;

import apptive.fin.apicollector.bankurl.scraper.BankProductScraper;
import apptive.fin.apicollector.bankurl.scraper.ScrapedProduct;
import apptive.fin.apicollector.normalize.normalizer.KfbProductUrlNormalizer;
import com.microsoft.playwright.Browser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KfbProductUrlNormalizerTest {

    private static final String IBK = "0010026";
    private static final String KAKAO = "0015130";
    private static final String BUSAN = "0010017";
    private static final String JEONBUK = "0010022";

    private final KfbProductUrlNormalizer normalizer = new KfbProductUrlNormalizer(List.of(
            new StubScraper(IBK, Set.of("ibk.co.kr")),
            new StubScraper(KAKAO, Set.of("kakaobank.com")),
            new StubScraper(BUSAN, Set.of("busanbank.co.kr")),
            new StubScraper(JEONBUK, Set.of("jbbank.co.kr"))
    ));

    // 브라우저가 요청할 때처럼 경로·쿼리의 공백·한글은 인코딩해서 살린다(IBK 공시 링크는 "?lncd= 01&grcd= 11").
    // 호스트는 은행 도메인의 하위 도메인(mybank.ibk.co.kr)이어도 된다.
    @Test
    void encodesIllegalCharsLikeBrowser() {
        assertThat(normalizer.normalize(IBK, "https://mybank.ibk.co.kr/상품 안내?lncd= 01&name=파킹"))
                .contains("https://mybank.ibk.co.kr/%EC%83%81%ED%92%88%20%EC%95%88%EB%82%B4?lncd=%2001&name=%ED%8C%8C%ED%82%B9");
    }

    @Test
    void keepsAlreadyEncodedUrlAsIs() {
        assertThat(normalizer.normalize(KAKAO, "https://www.kakaobank.com/a%20b?q=%ED%8C%8C"))
                .contains("https://www.kakaobank.com/a%20b?q=%ED%8C%8C");
    }

    // 부산은행 공시 링크는 앱 딥링크(AppsFlyer onelink) 호스트라 은행 도메인이 아니다. 스크래퍼가 채우도록 버린다.
    @Test
    void rejectsLinkOutsideBankDomain() {
        assertThat(normalizer.normalize(BUSAN, "https://busanbank.onelink.me/uNm5/obq17x3d")).isEmpty();
        assertThat(normalizer.normalize(KAKAO, "https://www.ibk.co.kr/product")).isEmpty();
    }

    // 전북은행 공시는 상품 대신 홈페이지를 링크한다. 은행 홈페이지는 providerApplyUrl이 이미 맡는다.
    @Test
    void rejectsBankHomePage() {
        assertThat(normalizer.normalize(JEONBUK, "https://www.jbbank.co.kr")).isEmpty();
        assertThat(normalizer.normalize(JEONBUK, "https://www.jbbank.co.kr/")).isEmpty();
        assertThat(normalizer.normalize(JEONBUK, "https://www.jbbank.co.kr/?menu=deposit")).isPresent();
        assertThat(normalizer.normalize(JEONBUK, "https://www.jbbank.co.kr/deposit")).isPresent();
    }

    // 한국씨티은행처럼 스크래퍼가 없는 은행은 허용 도메인을 몰라 링크를 받지 않는다.
    @Test
    void rejectsLinkOfBankWithoutScraper() {
        assertThat(normalizer.normalize("0010006", "https://www.citibank.co.kr/product")).isEmpty();
    }

    // 전부 인코딩하면 깨진 링크도 문법상 유효한 URL이 되지만, 호스트가 은행 도메인이 아니라 떨어진다.
    @Test
    void rejectsBrokenUrlWhoseHostIsNotDomain() {
        assertThat(normalizer.normalize(IBK, "https://자유입출금상품>예금상품상세 - 우리은행")).isEmpty();
    }

    @Test
    void rejectsIpAddressHost() {
        assertThat(normalizer.normalize(IBK, "http://127.0.0.1/internal")).isEmpty();
    }

    @Test
    void rejectsNonHttpOrMissingUrl() {
        assertThat(normalizer.normalize(KAKAO, "javascript:alert(1)")).isEmpty();
        assertThat(normalizer.normalize(KAKAO, null)).isEmpty();
        assertThat(normalizer.normalize(KAKAO, "  ")).isEmpty();
    }

    // apply_url 컬럼은 VARCHAR(500)이다.
    @Test
    void rejectsUrlLongerThanApplyUrlColumn() {
        assertThat(normalizer.normalize(KAKAO, "https://www.kakaobank.com/" + "a".repeat(475))).isEmpty();
        assertThat(normalizer.normalize(KAKAO, "https://www.kakaobank.com/" + "a".repeat(474))).isPresent();
    }

    private record StubScraper(String providerCode, Set<String> allowedDomains) implements BankProductScraper {

        @Override
        public ScrapedProduct scrape(Browser browser, String productName, int timeoutMillis) {
            throw new UnsupportedOperationException();
        }
    }
}
