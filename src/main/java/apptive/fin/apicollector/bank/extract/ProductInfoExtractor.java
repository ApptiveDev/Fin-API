package apptive.fin.apicollector.bank.extract;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.rate.BankRateExtractor;
import apptive.fin.apicollector.bank.rate.ExtractedRates;
import apptive.fin.apicollector.bank.rate.NoopBankRateExtractor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductInfoExtractor {
    private final Map<BankCode, BankRateExtractor> rateExtractors;

    public ProductInfoExtractor(List<BankRateExtractor> rateExtractors) {
        Map<BankCode, BankRateExtractor> result = new EnumMap<>(BankCode.class);
        for (BankRateExtractor rateExtractor : rateExtractors) {
            result.put(rateExtractor.bankCode(), rateExtractor);
        }
        this.rateExtractors = Map.copyOf(result);
    }

    public BankProductInfo extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        String text = page.text().replaceAll("\\s+", " ").trim();
        ExtractedRates rates = rateExtractor(candidate.bankCode()).extract(candidate, page);

        return new BankProductInfo(
                candidate.bankCode(),
                firstNonBlank(candidate.title(), page.title(), candidate.keyword()),
                productType(text),
                candidate.url(),
                rates.baseRate(),
                rates.maxRate()
        );
    }

    private BankRateExtractor rateExtractor(BankCode bankCode) {
        return rateExtractors.getOrDefault(bankCode, new NoopBankRateExtractor(bankCode));
    }

    private String productType(String text) {
        if (text.contains("대출")) {
            return "LOAN";
        }
        if (text.contains("청약")) {
            return "SUBSCRIPTION";
        }
        if (text.contains("예금")) {
            return "DEPOSIT";
        }
        if (text.contains("적금") || text.contains("저축")) {
            return "SAVING";
        }
        return "POLICY_FINANCE";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
