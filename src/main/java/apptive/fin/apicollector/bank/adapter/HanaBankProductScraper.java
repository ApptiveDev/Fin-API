package apptive.fin.apicollector.bank.adapter;

import apptive.fin.apicollector.bank.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HanaBankProductScraper extends AbstractStaticBankProductScraper {

    public HanaBankProductScraper(
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor,
            BankProductSeedCatalog seedCatalog,
            List<ProductLinkDiscoverer> discoverers
    ) {
        super(htmlClient, verifier, extractor, seedCatalog, discoverers);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.HANA;
    }

}
