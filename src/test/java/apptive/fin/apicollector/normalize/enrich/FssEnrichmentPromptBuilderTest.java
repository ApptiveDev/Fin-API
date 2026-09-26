package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.raw.ProductRaw;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FssEnrichmentPromptBuilderTest {

    private final FssEnrichmentPromptBuilder promptBuilder = new FssEnrichmentPromptBuilder();

    @Test
    void build_embedsDraftFactsAndRawJson() {
        ProductRaw raw = new ProductRaw(
                Source.FSS, "FSS:SAVING:001", "hash", "{\"base\":{\"fin_prdt_nm\":\"청년적금\"}}", ProductType.SAVING);
        ProductDraft draft = ProductDraft.builder()
                .productName("청년적금")
                .type(ProductType.SAVING)
                .content("원문 설명")
                .build();

        String prompt = promptBuilder.build(raw, draft);

        assertThat(prompt)
                .contains("productName=청년적금")
                .contains("productType=SAVING")
                .contains("content=원문 설명")
                .contains(raw.getRawJson())
                .contains("JSON skeleton:");
    }

    @Test
    void build_includesMinDepositAmountGuidanceAndSkeletonKey() {
        ProductRaw raw = new ProductRaw(
                Source.FSS, "FSS:DEPOSIT:001", "hash", "{\"base\":{\"fin_prdt_nm\":\"정기예금\"}}", ProductType.DEPOSIT);
        ProductDraft draft = ProductDraft.builder()
                .productName("정기예금")
                .type(ProductType.DEPOSIT)
                .content("최소가입한도 100만원")
                .build();

        String prompt = promptBuilder.build(raw, draft);

        assertThat(prompt)
                .contains("minDepositAmount")
                .contains("최소 가입금액")
                .contains("\"minDepositAmount\": null");
    }
}
