package apptive.fin.apicollector.normalize.dto;

import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.ProductPropertyOrigin;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
public record ProductPropertyDraft(
        ProductPropertyOrigin propertyOrigin,
        String providerCode,
        String providerName,
        String intrRateType,
        String intrRateTypeName,
        Integer saveTerm,
        BigDecimal baseRate,
        BigDecimal maxRate,
        BigDecimal govContributionRate,
        Long minMonthlyLimit,
        Long maxMonthlyLimit,
        Integer minAge,
        Integer maxAge,
        Long earnMaxAmt,
        Integer earnPercent,
        Integer minTenureMonths,
        Boolean requiresHomeless,
        Boolean requiresHouseholder,
        String applyUrl,
        String joinTarget,
        String joinPeriodText,
        String joinAmountText,
        String joinMethod,
        String baseRateText,
        String maxRateText,
        String preferentialCondition,
        String depositorProtectionText,
        String sourceHash,
        Instant scrapedAt,
        List<KeywordValueEnum> keywords
) {
    public ProductPropertyDraft {
        propertyOrigin = propertyOrigin == null ? ProductPropertyOrigin.NORMALIZED : propertyOrigin;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        requiresHomeless = requiresHomeless != null && requiresHomeless;
        requiresHouseholder = requiresHouseholder != null && requiresHouseholder;
    }
}
