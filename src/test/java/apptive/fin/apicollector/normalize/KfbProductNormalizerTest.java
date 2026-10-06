package apptive.fin.apicollector.normalize;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.normalize.extractor.FssRequiredKeywordExtractor;
import apptive.fin.apicollector.normalize.extractor.KeywordExtractor;
import apptive.fin.apicollector.normalize.extractor.KfbLimitClassifier;
import apptive.fin.apicollector.normalize.extractor.keywords.BenefitKeywordRecognizer;
import apptive.fin.apicollector.normalize.extractor.keywords.InterestKeywordRecognizer;
import apptive.fin.apicollector.normalize.extractor.keywords.RegionKeywordRecognizer;
import apptive.fin.apicollector.normalize.extractor.keywords.TermKeywordRecognizer;
import apptive.fin.apicollector.normalize.normalizer.FssBankNameNormalizer;
import apptive.fin.apicollector.normalize.normalizer.FssBankUrlNormalizer;
import apptive.fin.apicollector.normalize.normalizer.KfbProductNormalizer;
import apptive.fin.apicollector.normalize.normalizer.KfbProductUrlNormalizer;
import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.raw.ProductRaw;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KfbProductNormalizerTest {

    private final KfbProductNormalizer normalizer = new KfbProductNormalizer(
            new ObjectMapper(),
            properties(),
            keywordExtractor(),
            new FssRequiredKeywordExtractor(),
            new FssBankNameNormalizer(),
            new FssBankUrlNormalizer(),
            new KfbLimitClassifier(),
            new KfbProductUrlNormalizer()
    );

    @Test
    void normalizesKfbParkingProduct() {
        ProductDraft draft = normalizer.normalize(raw("""
                {
                  "source": "KFB",
                  "productType": "PARKING",
                  "bankCode": "0015130",
                  "bankName": "카카오뱅크",
                  "productName": "세이프박스",
                  "productUrl": "https://www.kakaobank.com/products/safebox",
                  "baseRate": 1.60,
                  "maxRate": 1.80,
                  "interestPayment": "수시지급,월지급",
                  "joinMethod": "스마트뱅킹",
                  "preferentialCondition": "※별도 우대조건 없음",
                  "joinRestriction": "제한없음",
                  "joinTarget": "만 14세 이상의 실명의 개인",
                  "etcNote": "1. 상품설명 : 계좌 속 금고",
                  "maxLimit": 100000000
                }
                """));

        assertThat(draft.sourceCode()).isEqualTo("KFB");
        assertThat(draft.type()).isEqualTo(ProductType.PARKING);
        assertThat(draft.productCode()).isEqualTo("KFB:PARKING:0015130:세이프박스");
        assertThat(draft.productName()).isEqualTo("세이프박스");
        assertThat(draft.joinMethod()).isEqualTo("스마트뱅킹");
        assertThat(draft.eligibilityText()).isEqualTo("만 14세 이상의 실명의 개인");
        assertThat(draft.cautionText()).isEqualTo("1. 상품설명 : 계좌 속 금고");
        assertThat(draft.content()).contains("이자지급방식: 수시지급,월지급");
        assertThat(draft.properties()).hasSize(1);

        ProductPropertyDraft property = draft.properties().getFirst();
        assertThat(property.providerCode()).isEqualTo("0015130");
        assertThat(property.providerName()).isEqualTo("카카오뱅크");
        assertThat(property.applyUrl()).isEqualTo("https://www.kakaobank.com/products/safebox");
        assertThat(property.baseRate()).isEqualByComparingTo("1.60");
        assertThat(property.maxRate()).isEqualByComparingTo("1.80");
        assertThat(property.interestPaymentMethod()).isEqualTo("수시지급,월지급");
        assertThat(property.saveTerm()).isNull();
        assertThat(property.keywords()).contains(KeywordValueEnum.INTEREST_SAVINGS);
    }

    @Test
    void storesDepositLimitAsMaxDepositAmount() {
        ProductPropertyDraft property = normalizer.normalize(raw(parkingJson(
                "※별도 우대조건 없음", "가입금액: 1만원 이상 500만원 이하", 5_000_000L
        ))).properties().getFirst();

        assertThat(property.maxDepositAmount()).isEqualTo(5_000_000L);
        assertThat(property.preferentialRateLimitAmount()).isNull();
        assertThat(property.maxMonthlyLimit()).isNull();
    }

    // 우대금리 적용 한도를 예치 한도 컬럼에 넣으면 예치액 필터가 상품을 잘못 걸러낸다.
    @Test
    void keepsPreferentialRateLimitOutOfDepositAmount() {
        ProductPropertyDraft property = normalizer.normalize(raw(parkingJson(
                "제한없음", "매일의 최종 잔액 중 5백만원 이하의 금액에 대해 우대금리 제공", 5_000_000L
        ))).properties().getFirst();

        assertThat(property.maxDepositAmount()).isNull();
        assertThat(property.preferentialRateLimitAmount()).isEqualTo(5_000_000L);
        assertThat(property.maxMonthlyLimit()).isNull();
    }

    // 파킹통장 우대조건 칸에는 잔액 구간별 금리("1억원초과 : 0.01%")가 섞여 있어, 규칙 추출기는 이를 우대금리로 잘못 잡는다.
    // 구간금리와 우대금리를 나눌 수 있을 때(2단계 LLM)까지 우대금리를 싣지 않는다.
    @Test
    void doesNotTreatBalanceTierRatesAsPreferentialRates() {
        ProductPropertyDraft property = normalizer.normalize(raw(parkingJson(
                "매일 최종 잔액에 대하여 금액구간별 금리 차등적용\\nⅠ.1천만원이하 : 1.50%\\nⅡ.1억원초과 : 0.10%\\n마케팅동의 0.10%(신규시)",
                "없음", null
        ))).properties().getFirst();

        assertThat(property.preferentialRates()).isEmpty();
    }

    // raw에는 공시 원본 링크가 그대로 있고, 아웃링크(applyUrl)로 쓸 때 정규화한다(규칙은 KfbProductUrlNormalizerTest).
    @Test
    void dropsBrokenProductUrlFromApplyUrl() {
        ProductPropertyDraft property = normalizer.normalize(raw(linkJson(
                "0010001", "https://자유입출금상품>예금상품상세 - 우리은행"
        ))).properties().getFirst();

        assertThat(property.applyUrl()).isNull();
    }

    @Test
    void encodesSpacesInProductUrlForApplyUrl() {
        ProductPropertyDraft property = normalizer.normalize(raw(linkJson(
                "0010026", "https://mybank.ibk.co.kr/uib/PNTR701000_i2.jsp?lncd= 01&grcd= 11"
        ))).properties().getFirst();

        assertThat(property.applyUrl()).isEqualTo("https://mybank.ibk.co.kr/uib/PNTR701000_i2.jsp?lncd=%2001&grcd=%2011");
    }

    private static String linkJson(String bankCode, String productUrl) {
        return """
                {
                  "bankCode": "%s",
                  "bankName": "은행",
                  "productName": "통장",
                  "productUrl": "%s"
                }
                """.formatted(bankCode, productUrl);
    }

    private static ProductRaw raw(String json) {
        return new ProductRaw(Source.KFB, "KFB:PARKING:0015130:세이프박스", "hash", json, ProductType.PARKING);
    }

    private static String parkingJson(String preferentialCondition, String etcNote, Long maxLimit) {
        return """
                {
                  "bankCode": "0013909",
                  "bankName": "하나은행",
                  "productName": "원픽 통장",
                  "baseRate": 0.10,
                  "maxRate": 2.00,
                  "preferentialCondition": "%s",
                  "etcNote": "%s",
                  "maxLimit": %s
                }
                """.formatted(preferentialCondition, etcNote, maxLimit);
    }

    private CollectorProperties properties() {
        return new CollectorProperties(
                true,
                Source.KFB,
                Mode.NORMALIZE_ONLY,
                3,
                500,
                7,
                null,
                null,
                new CollectorProperties.Llm(false, "GEMINI", "gemini-test", 1, 1, 10, 3, 0.1, "http://localhost", "")
        );
    }

    private KeywordExtractor keywordExtractor() {
        return new KeywordExtractor(List.of(
                new BenefitKeywordRecognizer(),
                new InterestKeywordRecognizer(),
                new RegionKeywordRecognizer(),
                new TermKeywordRecognizer()
        ));
    }
}
