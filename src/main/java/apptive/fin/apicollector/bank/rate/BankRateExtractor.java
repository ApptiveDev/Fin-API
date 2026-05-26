package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;

public interface BankRateExtractor {
    BankCode bankCode();

    ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page);
}
