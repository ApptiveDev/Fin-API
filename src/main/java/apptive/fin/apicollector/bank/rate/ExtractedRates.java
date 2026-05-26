package apptive.fin.apicollector.bank.rate;

import java.math.BigDecimal;

public record ExtractedRates(
        BigDecimal baseRate,
        BigDecimal maxRate
) {
    public static ExtractedRates empty() {
        return new ExtractedRates(null, null);
    }

    public boolean hasAnyRate() {
        return baseRate != null || maxRate != null;
    }
}
