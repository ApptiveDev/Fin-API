package apptive.fin.apicollector.bank;

import java.math.BigDecimal;
import java.time.Instant;

public record BankProductInfo(
        BankCode bankCode,
        String productName,
        String productType,
        String productUrl,
        String joinTarget,
        String joinPeriod,
        String joinAmount,
        String joinMethod,
        BigDecimal baseRate,
        BigDecimal maxRate,
        String baseRateText,
        String maxRateText,
        String preferentialCondition,
        String depositorProtectionText,
        String sourceHash,
        Instant scrapedAt
) {
}
