package apptive.fin.apicollector.bank.model;

import java.math.BigDecimal;

public record BankProductInfo(
        BankCode bankCode,
        String productName,
        String productType,
        String productUrl,
        BigDecimal baseRate,
        BigDecimal maxRate
) {
}
