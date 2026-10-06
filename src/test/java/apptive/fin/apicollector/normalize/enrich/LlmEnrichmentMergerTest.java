package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.llm.LlmProductEnrichment;
import apptive.fin.apicollector.normalize.dto.PreferentialRateDraft;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.ProductType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmEnrichmentMergerTest {

    private final LlmEnrichmentMerger merger = new LlmEnrichmentMerger();

    @Test
    void deposit_fillsMinDepositAmountFromLlmWhenAbsent() {
        ProductDraft result = merger.merge(draft(ProductType.DEPOSIT, null), enrichmentWithMinDeposit(1_000_000L));

        assertThat(result.properties().getFirst().minDepositAmount()).isEqualTo(1_000_000L);
    }

    @Test
    void deposit_prefersDeterministicMinDepositAmountOverLlm() {
        ProductDraft result = merger.merge(draft(ProductType.DEPOSIT, 500_000L), enrichmentWithMinDeposit(1_000_000L));

        assertThat(result.properties().getFirst().minDepositAmount()).isEqualTo(500_000L);
    }

    @Test
    void saving_forcesMinDepositAmountNullEvenIfPresent() {
        // minDepositAmount는 예금 전용 컬럼이다. 적금이면 LLM/기존 값과 무관하게 null이어야 한다.
        ProductDraft result = merger.merge(draft(ProductType.SAVING, 999_999L), enrichmentWithMinDeposit(1_000_000L));

        assertThat(result.properties().getFirst().minDepositAmount()).isNull();
    }

    @Test
    void kfb_keepsOnlyPreferentialRatesWrittenAsPercentInDisclosure() {
        // 공시에 "N%"로 적힌 숫자만 우대금리로 받는다. 최고금리-기본금리로 계산한 2.15, 조건만 있고 숫자가 없는 항목에
        // 지어낸 0.30, "2백만원"의 2처럼 %가 아닌 숫자와 겹치는 2.00은 모두 버린다.
        ProductDraft draft = kfbDraft("마케팅동의 0.10%(신규시) / 첫 거래 고객 (보너스이율0.5%) / 급여이체 실적 충족 시 우대금리 제공 / 잔액 중 2백만원 이하");

        ProductDraft result = merger.merge(draft, enrichmentWithRates(
                rate(KeywordValueEnum.BANK_MARKETING, "0.1", "마케팅동의"),
                rate(KeywordValueEnum.BANK_FIRST_TRANSACTION, "0.50", "첫거래 고객"),
                rate(KeywordValueEnum.BANK_SALARY_TRANSFER, "2.15", "급여이체"),
                rate(KeywordValueEnum.BANK_AUTO_TRANSFER, "0.30", "자동이체"),
                rate(KeywordValueEnum.BANK_CARD_USAGE, "2.00", "카드 실적")
        ));

        assertThat(result.properties().getFirst().preferentialRates())
                .extracting(PreferentialRateDraft::keywordCode)
                .containsExactly(KeywordValueEnum.BANK_MARKETING, KeywordValueEnum.BANK_FIRST_TRANSACTION);
    }

    @Test
    void fss_keepsPreferentialRatesWithoutDisclosureGrounding() {
        // 원문 대조 가드는 KFB 실측으로만 검증했다. FSS 동작은 그대로 둔다.
        ProductDraft result = merger.merge(draft(ProductType.SAVING, null), enrichmentWithRates(
                rate(KeywordValueEnum.BANK_SALARY_TRANSFER, "0.30", "급여이체")
        ));

        assertThat(result.properties().getFirst().preferentialRates()).hasSize(1);
    }

    private ProductDraft kfbDraft(String content) {
        return ProductDraft.builder()
                .rawId(1L)
                .rawSource(Source.KFB)
                .normalizerVersion(1)
                .sourceCode("KFB")
                .type(ProductType.PARKING)
                .productCode("KFB:PARKING:001:파킹통장")
                .productName("파킹통장")
                .content(content)
                .properties(List.of(ProductPropertyDraft.builder()
                        .providerCode("001")
                        .providerName("테스트은행")
                        .build()))
                .build();
    }

    private PreferentialRateDraft rate(KeywordValueEnum keyword, String rate, String description) {
        return PreferentialRateDraft.builder()
                .keywordCode(keyword)
                .rate(new BigDecimal(rate))
                .description(description)
                .build();
    }

    private LlmProductEnrichment enrichmentWithRates(PreferentialRateDraft... rates) {
        return new LlmProductEnrichment(
                null, List.of(), null, null, null, null, null, null, null,
                false, false, null, null, null, null, null, false, false, null, List.of(), List.of(rates));
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
