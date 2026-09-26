package apptive.fin.apicollector.normalize;

import apptive.fin.apicollector.normalize.normalizer.KfbProductUrlNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KfbProductUrlNormalizerTest {

    private final KfbProductUrlNormalizer normalizer = new KfbProductUrlNormalizer();

    // 브라우저가 요청할 때처럼 경로·쿼리의 공백·한글은 인코딩해서 살린다(IBK 공시 링크는 "?lncd= 01&grcd= 11").
    @Test
    void encodesIllegalCharsLikeBrowser() {
        assertThat(normalizer.normalize("https://mybank.ibk.co.kr/상품 안내?lncd= 01&name=파킹"))
                .contains("https://mybank.ibk.co.kr/%EC%83%81%ED%92%88%20%EC%95%88%EB%82%B4?lncd=%2001&name=%ED%8C%8C%ED%82%B9");
    }

    @Test
    void keepsAlreadyEncodedUrlAsIs() {
        assertThat(normalizer.normalize("https://www.kakaobank.com/a%20b?q=%ED%8C%8C"))
                .contains("https://www.kakaobank.com/a%20b?q=%ED%8C%8C");
    }

    // 부산은행 공시 링크는 앱 딥링크(AppsFlyer onelink) 호스트다. 도메인 형식이면 받는다.
    @Test
    void acceptsAppDeepLinkHost() {
        assertThat(normalizer.normalize("https://busanbank.onelink.me/uNm5/obq17x3d"))
                .contains("https://busanbank.onelink.me/uNm5/obq17x3d");
    }

    // 전부 인코딩하면 깨진 링크도 문법상 유효한 URL이 되므로, 호스트는 도메인 형식인지 따로 본다.
    @Test
    void rejectsBrokenUrlWhoseHostIsNotDomain() {
        assertThat(normalizer.normalize("https://자유입출금상품>예금상품상세 - 우리은행")).isEmpty();
    }

    // 은행 상품 페이지는 도메인으로 공시된다. IP 주소 호스트(내부망 포함)는 아웃링크로 받지 않는다.
    @Test
    void rejectsIpAddressHost() {
        assertThat(normalizer.normalize("http://127.0.0.1/internal")).isEmpty();
        assertThat(normalizer.normalize("https://10.0.0.1:8443/product")).isEmpty();
    }

    @Test
    void rejectsNonHttpOrMissingUrl() {
        assertThat(normalizer.normalize("javascript:alert(1)")).isEmpty();
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("  ")).isEmpty();
    }

    // apply_url 컬럼은 VARCHAR(500)이다.
    @Test
    void rejectsUrlLongerThanApplyUrlColumn() {
        assertThat(normalizer.normalize("https://www.kakaobank.com/" + "a".repeat(475))).isEmpty();
        assertThat(normalizer.normalize("https://www.kakaobank.com/" + "a".repeat(474))).isPresent();
    }
}
