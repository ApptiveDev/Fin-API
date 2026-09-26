package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.llm.LlmProductEnrichment;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.raw.ProductRaw;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FssEnrichmentMergerTest {

    private final FssEnrichmentMerger merger = new FssEnrichmentMerger(new ObjectMapper());

    @Test
    void deposit_fillsMinDepositAmountFromLlmWhenAbsent() {
        ProductDraft result = merger.merge(raw(), draft(ProductType.DEPOSIT, null), enrichmentWithMinDeposit(1_000_000L));

        assertThat(result.properties().getFirst().minDepositAmount()).isEqualTo(1_000_000L);
    }

    @Test
    void deposit_prefersDeterministicMinDepositAmountOverLlm() {
        ProductDraft result = merger.merge(raw(), draft(ProductType.DEPOSIT, 500_000L), enrichmentWithMinDeposit(1_000_000L));

        assertThat(result.properties().getFirst().minDepositAmount()).isEqualTo(500_000L);
    }

    @Test
    void saving_forcesMinDepositAmountNullEvenIfPresent() {
        // minDepositAmount는 예금 전용 컬럼이다. 적금이면 LLM/기존 값과 무관하게 null이어야 한다.
        ProductDraft result = merger.merge(raw(), draft(ProductType.SAVING, 999_999L), enrichmentWithMinDeposit(1_000_000L));

        assertThat(result.properties().getFirst().minDepositAmount()).isNull();
    }

    private ProductRaw raw() {
        return new ProductRaw(Source.FSS, "FSS:DEPOSIT:001:ABC", "hash", "{\"base\":{}}", ProductType.DEPOSIT);
    }

    private ProductDraft draft(ProductType type, Long existingMinDepositAmount) {
        return ProductDraft.builder()
                .rawId(1L)
                .rawSource(Source.FSS)
                .normalizerVersion(1)
                .sourceCode("FSS")
                .type(type)
                .productCode("FSS:PRODUCT:001:ABC")
                .productName("상품")
                .content("원문 설명")
                .properties(List.of(ProductPropertyDraft.builder()
                        .providerCode("001")
                        .providerName("테스트은행")
                        .minDepositAmount(existingMinDepositAmount)
                        .build()))
                .build();
    }

    private LlmProductEnrichment enrichmentWithMinDeposit(Long minDepositAmount) {
        return new LlmProductEnrichment(
                null, List.of(), null, null, minDepositAmount, null, null, null, null,
                false, false, null, null, null, null, null, false, false, null, List.of(), List.of());
    }
}
