package apptive.fin.apicollector.bank.adapter;

import apptive.fin.apicollector.bank.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IbkBankProductScraper extends AbstractStaticBankProductScraper {

    public IbkBankProductScraper(
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor
    ) {
        super(htmlClient, verifier, extractor);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.IBK;
    }

    @Override
    protected List<ProductCandidate> seedCandidates(String keyword) {
        return List.of();
    }
}
