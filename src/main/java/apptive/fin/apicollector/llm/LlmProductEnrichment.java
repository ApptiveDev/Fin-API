package apptive.fin.apicollector.llm;

import apptive.fin.apicollector.normalize.dto.PreferentialRateDraft;
import apptive.fin.apicollector.normalize.dto.RequiredKeywordDraft;
import apptive.fin.apicollector.product.ContributionType;

import java.math.BigDecimal;
import java.util.List;

public record LlmProductEnrichment(
        String summaryContent,
        List<String> keywords,
        Long minMonthlyLimit,
        Long maxMonthlyLimit,
        // 예금(DEPOSIT) 전용: 원문에 명시된 최소 가입금액(최소가입한도/최소예치금액). 적금·미명시면 null.
        Long minDepositAmount,
        Integer minAge,
        Integer maxAge,
        Long earnMaxAmt,
        Integer earnPercent,
        Boolean requiresHomeless,
        Boolean requiresHouseholder,
        BigDecimal govContributionRate,
        ContributionType govContributionType,
        BigDecimal govMatchingRatio,
        Long govMonthlyFixedContribution,
        Integer govContributionPeriodMonths,
        Boolean excludeFromRateComparison,
        Boolean allowsMilitaryAgeExtension,
        Integer militaryMaxAge,
        List<RequiredKeywordDraft> requiredKeywords,
        List<PreferentialRateDraft> preferentialRates
) {
    public LlmProductEnrichment {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        requiredKeywords = requiredKeywords == null ? List.of() : List.copyOf(requiredKeywords);
        preferentialRates = preferentialRates == null ? List.of() : List.copyOf(preferentialRates);
        excludeFromRateComparison = excludeFromRateComparison != null && excludeFromRateComparison;
        allowsMilitaryAgeExtension = allowsMilitaryAgeExtension != null && allowsMilitaryAgeExtension;
    }
}
