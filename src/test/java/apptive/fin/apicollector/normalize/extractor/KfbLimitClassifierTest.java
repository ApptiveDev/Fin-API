package apptive.fin.apicollector.normalize.extractor;

import apptive.fin.apicollector.client.kfb.KfbFreeDepositParser;
import apptive.fin.apicollector.client.kfb.KfbRawProduct;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KfbLimitClassifierTest {

    private final KfbLimitClassifier classifier = new KfbLimitClassifier();

    @Test
    void treatsLimitAsPreferentialRateLimitWhenRateAppliesOnlyUpToAmount() {
        assertThat(classifier.isPreferentialRateLimit(
                "제한없음",
                "1. 1인 1 계좌\n2. 매일의 최종 잔액 중 5백만원 이하의 금액에 대해 우대금리 제공"
        )).isTrue();
    }

    @Test
    void treatsLimitAsDepositLimitWhenTextSaysNothingAboutRateLimit() {
        assertThat(classifier.isPreferentialRateLimit(
                "목표일까지 조건을 모두 충족한 계좌에 한하여 우대금리 제공 (최고 연 2.0%)",
                "가입금액: 1만원 이상 500만원 이하\n가입기간: 30일~200일 이하"
        )).isFalse();
    }

    @Test
    void treatsLimitAsDepositLimitWhenTextIsMissing() {
        assertThat(classifier.isPreferentialRateLimit(null, null)).isFalse();
    }

    // 우대금리 한도를 예치 한도로 잘못 저장하면 예치액 필터가 상품을 잘못 걸러내므로, 실제 공시에서 최고한도가 있는 전 건을 고정한다.
    @Test
    void classifiesEveryLimitInRealDisclosure() {
        Map<String, Boolean> byName = realProducts().stream()
                .filter(product -> product.maxLimit() != null)
                .collect(Collectors.toMap(
                        KfbRawProduct::productName,
                        product -> classifier.isPreferentialRateLimit(product.preferentialCondition(), product.etcNote())
                ));

        assertThat(byName).containsExactlyInAnyOrderEntriesOf(Map.of(
                "달달 하나 통장", true,
                "원픽 통장", true,
                "KB모임금고", false,
                "비상금박스", false,
                "챌린지박스", false,
                "세이프박스", false,
                "카카오뱅크 저금통", false
        ));
    }

    private static List<KfbRawProduct> realProducts() {
        try (InputStream in = KfbLimitClassifierTest.class.getResourceAsStream("/kfb/free_deposit_search_result_all_banks.html")) {
            String html = new String(in.readAllBytes(), Charset.forName("MS949"));
            return new KfbFreeDepositParser().parseProducts("any", html);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
