package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.llm.gemini.GeminiEnrichmentSchema;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.raw.ProductRaw;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class KfbEnrichmentPromptBuilderTest {

    private final KfbEnrichmentPromptBuilder promptBuilder = new KfbEnrichmentPromptBuilder();

    @Test
    void build_embedsDraftFactsAndRawJson() {
        ProductRaw raw = raw("{\"productName\":\"파킹통장\",\"preferentialCondition\":\"마케팅동의 0.10%\"}");

        String prompt = promptBuilder.build(raw, draft());

        assertThat(prompt)
                .contains("productName=파킹통장")
                .contains("productType=PARKING")
                .contains("content=이자지급방식: 월지급")
                .contains(raw.getRawJson())
                .contains("JSON skeleton:");
    }

    @Test
    void build_excludesBalanceTierRatesFromPreferentialRates() {
        String prompt = promptBuilder.build(raw("{}"), draft());

        assertThat(prompt)
                .contains("잔액 구간별 적용금리는 우대금리가 아니다")
                .contains("예치기간별 적용금리")
                .contains("minMonthlyLimit, maxMonthlyLimit, minDepositAmount는 항상 null");
    }

    @Test
    void build_skeletonContainsEverySchemaProperty() {
        String prompt = promptBuilder.build(raw("{}"), draft());

        new GeminiEnrichmentSchema(new ObjectMapper()).build().path("properties").propertyNames()
                .forEach(name -> assertThat(prompt).contains("\"" + name + "\":"));
    }

    private ProductRaw raw(String rawJson) {
        return new ProductRaw(Source.KFB, "KFB:PARKING:0010001:파킹통장", "hash", rawJson, ProductType.PARKING);
    }

    private ProductDraft draft() {
        return ProductDraft.builder()
                .productName("파킹통장")
                .type(ProductType.PARKING)
                .content("이자지급방식: 월지급")
                .build();
    }
}
