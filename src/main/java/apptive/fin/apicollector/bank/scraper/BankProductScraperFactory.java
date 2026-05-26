package apptive.fin.apicollector.bank.scraper;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.scraper.BankProductScraper;
import apptive.fin.apicollector.bank.seed.BankProductSeedCatalog;
import apptive.fin.apicollector.bank.extract.ProductInfoExtractor;
import apptive.fin.apicollector.bank.ProductLinkDiscoverer;
import apptive.fin.apicollector.bank.verify.ProductPageVerifier;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BankProductScraperFactory {
    private final StaticHtmlClient htmlClient;
    private final ProductPageVerifier verifier;
    private final ProductInfoExtractor extractor;
    private final BankProductSeedCatalog seedCatalog;
    private final List<ProductLinkDiscoverer> discoverers;

    public BankProductScraperFactory(
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor,
            BankProductSeedCatalog seedCatalog,
            List<ProductLinkDiscoverer> discoverers
    ) {
        this.htmlClient = htmlClient;
        this.verifier = verifier;
        this.extractor = extractor;
        this.seedCatalog = seedCatalog;
        this.discoverers = discoverers;
    }

    public BankProductScraper create(BankCode bankCode) {
        return new StaticBankProductScraper(
                bankCode,
                htmlClient,
                verifier,
                extractor,
                seedCatalog,
                discoverers
        );
    }
}
