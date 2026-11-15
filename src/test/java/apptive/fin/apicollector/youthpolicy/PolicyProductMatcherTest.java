package apptive.fin.apicollector.youthpolicy;

import apptive.fin.apicollector.youthpolicy.PolicyProductMatcher.PolicyProduct;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// 정책명은 2026-11 온통청년 getPlcy 응답에서 실제로 나온 이름이고, 상품은 manual-products.json 그대로다.
class PolicyProductMatcherTest {

    private final PolicyProductMatcher matcher = new PolicyProductMatcher(new ObjectMapper());

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            청년내일저축계좌                               | POLICY001
            자산형성지원사업(청년내일저축계좌)                  | POLICY001
            청년내일저축계좌사업(차상위 이하, 초과 통합)          | POLICY001
            부산 청년 자산형성 지원(부산청년 기쁨두배통장)         | POLICY003
            청년 노동자 통장 운영                           | POLICY004
            청년발달장애인 자산형성지원(행복씨앗통장)              | POLICY006
            (접수 마감)2026년 11기 광주 청년 13(일+삶)통장      | POLICY008
            영암군 청년 희망 디딤돌 통장사업                     | POLICY009
            2025년 청년 디딤돌 2배 적금 지원사업                | POLICY011
            청년근로자 사랑채움사업                           | POLICY015
            """)
    void 정책상품명의_단어가_모두_들어있으면_그_상품으로_본다(String policyName, String productCode) {
        assertThat(matcher.match(policyName)).map(PolicyProduct::productCode).hasValue(productCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "청년 자산형성 지원(청년도약계좌)",
            "2026 희망두배 청년통장",
            "대구형 청년 그린 내일채움공제",
            "광주형 청년일자리 공제",
            "청년 학자금 대출 부실채무자 신용회복 지원",
    })
    void 정책상품명의_단어가_하나라도_빠지면_다른_정책이다(String policyName) {
        assertThat(matcher.match(policyName)).isEmpty();
    }
}
