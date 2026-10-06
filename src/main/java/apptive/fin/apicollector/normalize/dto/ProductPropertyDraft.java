package apptive.fin.apicollector.normalize.dto;

import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.ContributionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
public record ProductPropertyDraft(
        String variantCode,
        String providerCode,
        String providerName,
        String intrRateType,
        String intrRateTypeName,
        String reserveType,
        Integer saveTerm,
        BigDecimal baseRate,
        BigDecimal maxRate,
        BigDecimal govContributionRate,
        ContributionType govContributionType,
        BigDecimal govMatchingRatio,
        Long govMonthlyFixedContribution,
        Integer govContributionPeriodMonths,
        Boolean excludeFromRateComparison,
        Long minMonthlyLimit,
        Long maxMonthlyLimit,
        Long minDepositAmount,
        Long maxDepositAmount,
        Integer minAge,
        Integer maxAge,
        Boolean allowsMilitaryAgeExtension,
        Integer militaryMaxAge,
        Long earnMaxAmt,
        Integer earnPercent,
        Integer minTenureMonths,
        Boolean requiresHomeless,
        Boolean requiresHouseholder,
        String applyUrl,
        String providerApplyUrl,
        // 파킹통장 최고금리가 적용되는 잔액 범위(원). 하한은 초과, 상한은 이하. 예치 한도와 다르다.
        Long maxRateApplicableMinAmount,
        Long maxRateApplicableMaxAmount,
        // 이자지급방식 원문(예: "수시지급,월지급")
        String interestPaymentMethod,
        List<KeywordValueEnum> keywords,
        List<RequiredKeywordDraft> requiredKeywords,
        List<PreferentialRateDraft> preferentialRates
) {
    public ProductPropertyDraft {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        requiredKeywords = requiredKeywords == null ? List.of() : List.copyOf(requiredKeywords);
        preferentialRates = preferentialRates == null ? List.of() : List.copyOf(preferentialRates);
        excludeFromRateComparison = excludeFromRateComparison != null && excludeFromRateComparison;
        allowsMilitaryAgeExtension = allowsMilitaryAgeExtension != null && allowsMilitaryAgeExtension;
        requiresHomeless = requiresHomeless != null && requiresHomeless;
        requiresHouseholder = requiresHouseholder != null && requiresHouseholder;
    }
}
