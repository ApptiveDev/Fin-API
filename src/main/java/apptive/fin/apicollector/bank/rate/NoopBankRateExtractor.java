package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;

public class NoopBankRateExtractor implements BankRateExtractor {
    private final BankCode bankCode;

    public NoopBankRateExtractor(BankCode bankCode) {
        this.bankCode = bankCode;
    }

    @Override
    public BankCode bankCode() {
        return bankCode;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        return ExtractedRates.empty();
    }
}
