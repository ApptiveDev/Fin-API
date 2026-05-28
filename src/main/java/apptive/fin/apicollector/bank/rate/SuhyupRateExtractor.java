package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import org.springframework.stereotype.Component;

@Component
public class SuhyupRateExtractor extends GenericHtmlRateExtractorSupport implements BankRateExtractor {
    @Override
    public BankCode bankCode() {
        return BankCode.SUHYUP;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        return genericExtract(page);
    }
}
