package apptive.fin.apicollector.normalize.normalizer;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.global.util.JsonNodes;
import apptive.fin.apicollector.normalize.ProductClassification;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.normalize.extractor.FssRequiredKeywordExtractor;
import apptive.fin.apicollector.normalize.extractor.KeywordExtractor;
import apptive.fin.apicollector.normalize.extractor.KfbLimitClassifier;
import apptive.fin.apicollector.raw.ProductRaw;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 은행연합회 입출금자유예금(파킹통장) raw를 정규화한다. 기간·적립 옵션이 없어 property는 하나다.
 * 은행코드가 FSS fin_co_no와 같은 체계라 은행명·은행 URL 매핑은 FSS 것을 그대로 쓴다.
 */
@Component
public class KfbProductNormalizer implements ProductNormalizer {

    private final CollectorProperties properties;
    private final KeywordExtractor keywordExtractor;
    private final FssRequiredKeywordExtractor requiredKeywordExtractor;
    private final FssBankNameNormalizer bankNameNormalizer;
    private final FssBankUrlNormalizer bankUrlNormalizer;
    private final KfbLimitClassifier limitClassifier;
    private final KfbProductUrlNormalizer productUrlNormalizer;
    private final RawJsonReader rawJsonReader;

    public KfbProductNormalizer(
            ObjectMapper objectMapper,
            CollectorProperties properties,
            KeywordExtractor keywordExtractor,
            FssRequiredKeywordExtractor requiredKeywordExtractor,
            FssBankNameNormalizer bankNameNormalizer,
            FssBankUrlNormalizer bankUrlNormalizer,
            KfbLimitClassifier limitClassifier,
            KfbProductUrlNormalizer productUrlNormalizer
    ) {
        this.properties = properties;
        this.keywordExtractor = keywordExtractor;
        this.requiredKeywordExtractor = requiredKeywordExtractor;
        this.bankNameNormalizer = bankNameNormalizer;
        this.bankUrlNormalizer = bankUrlNormalizer;
        this.limitClassifier = limitClassifier;
        this.productUrlNormalizer = productUrlNormalizer;
        this.rawJsonReader = new RawJsonReader(objectMapper, "KFB");
    }

    @Override
    public Source source() {
        return Source.KFB;
    }

    @Override
    public ProductDraft normalize(ProductRaw rawProduct) {
        JsonNode raw = rawJsonReader.read(rawProduct);
        String interestPayment = JsonNodes.text(raw, "interestPayment");

        var draft = ProductDraft.builder()
                .rawId(rawProduct.getId())
                .rawSource(rawProduct.getSource())
                .normalizerVersion(properties.normalizerVersion())
                .classification(ProductClassification.FINANCIAL_PRODUCT)
                .saveProduct(true)
                .sourceCode(Source.KFB.name())
                .type(rawProduct.getType())
                .productCode(rawProduct.getExternalId())
                .productName(rawJsonReader.required(JsonNodes.text(raw, "productName"), "productName", rawProduct))
                .content(content(raw, interestPayment))
                .joinMethod(JsonNodes.text(raw, "joinMethod"))
                .eligibilityText(JsonNodes.text(raw, "joinTarget"))
                .cautionText(JsonNodes.text(raw, "etcNote"))
                .properties(List.of(property(raw, interestPayment)))
                .build();

        return keywordExtractor.attachTo(draft);
    }

    private ProductPropertyDraft property(JsonNode raw, String interestPayment) {
        String providerCode = JsonNodes.text(raw, "bankCode");
        String preferentialCondition = JsonNodes.text(raw, "preferentialCondition");
        String etcNote = JsonNodes.text(raw, "etcNote");

        // 최고한도는 상품마다 예치 한도이기도, 우대금리 적용 한도이기도 하다. 예치 한도만 저장 컬럼에 넣는다.
        Long maxLimit = JsonNodes.longValueOrNullIfZero(raw, "maxLimit");
        boolean isPreferentialRateLimit = limitClassifier.isPreferentialRateLimit(preferentialCondition, etcNote);

        return ProductPropertyDraft.builder()
                .providerCode(providerCode)
                .providerName(bankNameNormalizer.normalize(providerCode, JsonNodes.text(raw, "bankName")))
                .providerApplyUrl(bankUrlNormalizer.normalize(providerCode).orElse(null))
                // 공시 원본 링크는 깨져 있거나 공백이 섞여 있기도 해서, 아웃링크로 쓸 수 있게 정규화한다.
                .applyUrl(productUrlNormalizer.normalize(JsonNodes.text(raw, "productUrl")).orElse(null))
                .baseRate(JsonNodes.decimal(raw, "baseRate"))
                .maxRate(JsonNodes.decimal(raw, "maxRate"))
                .maxDepositAmount(isPreferentialRateLimit ? null : maxLimit)
                .preferentialRateLimitAmount(isPreferentialRateLimit ? maxLimit : null)
                .interestPaymentMethod(interestPayment)
                .requiredKeywords(requiredKeywordExtractor.extract(JsonNodes.text(raw, "joinTarget"), etcNote))
                // 우대조건 칸에 잔액 구간별 금리("1억원초과 : 0.01%")가 섞여 있어 규칙 추출기가 이를 우대금리로 잘못 잡는다.
                // 구간금리와 우대금리를 나눌 수 있을 때(LLM 보강)까지 우대금리는 싣지 않는다.
                .build();
    }

    // 이자지급방식은 저장 컬럼이 없어 본문에라도 남긴다.
    private static String content(JsonNode raw, String interestPayment) {
        String details = JsonNodes.joinContent(raw, "joinMethod", "preferentialCondition", "joinRestriction", "joinTarget", "etcNote");
        if (interestPayment == null) {
            return details;
        }
        String paymentLine = "이자지급방식: " + interestPayment;
        return details == null ? paymentLine : paymentLine + "\n\n" + details;
    }
}
