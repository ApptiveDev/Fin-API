package apptive.fin.apicollector.bank.scraper;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.bank.seed.BankProductSeedCatalog;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.extract.ProductInfoExtractor;
import apptive.fin.apicollector.bank.ProductLinkDiscoverer;
import apptive.fin.apicollector.bank.keyword.ProductNameSimilarity;
import apptive.fin.apicollector.bank.verify.ProductPageVerifier;
import apptive.fin.apicollector.bank.model.ProductScrapeContext;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.VerificationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
class StaticBankProductScraper implements BankProductScraper {
    private final BankCode bankCode;
    private final StaticHtmlClient htmlClient;
    private final ProductPageVerifier verifier;
    private final ProductInfoExtractor extractor;
    private final BankProductSeedCatalog seedCatalog;
    private final List<ProductLinkDiscoverer> discoverers;

    StaticBankProductScraper(
            BankCode bankCode,
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor,
            BankProductSeedCatalog seedCatalog,
            List<ProductLinkDiscoverer> discoverers
    ) {
        this.bankCode = bankCode;
        this.htmlClient = htmlClient;
        this.verifier = verifier;
        this.extractor = extractor;
        this.seedCatalog = seedCatalog;
        this.discoverers = discoverers;
    }

    @Override
    public BankCode bankCode() {
        return bankCode;
    }

    @Override
    public List<ProductCandidate> search(ProductScrapeContext context) {
        List<ProductCandidate> candidates = new ArrayList<>(discoverCandidates(context.keyword()));
        candidates.addAll(matchedSeedCandidates(context.keyword()));

        return deduplicateByUrl(candidates).stream()
                .map(this::verify)
                .filter(candidate -> candidate.score() >= 40)
                .toList();
    }

    @Override
    public Optional<ProductCandidate> select(ProductScrapeContext context, List<ProductCandidate> candidates) {
        return BankProductScraper.super.select(context, candidates)
                .filter(candidate -> candidate.score() >= 70);
    }

    @Override
    public BankProductInfo extractInfo(ProductCandidate candidate) {
        return extractor.extract(candidate, htmlClient.fetch(candidate.url()));
    }

    private List<ProductCandidate> discoverCandidates(ProductSearchKeyword keyword) {
        return discoverers.stream()
                .filter(discoverer -> discoverer.bankCode() == bankCode)
                .flatMap(discoverer -> discoverer.discover(keyword).stream())
                .toList();
    }

    private List<ProductCandidate> matchedSeedCandidates(ProductSearchKeyword keyword) {
        return seedCatalog.candidates(bankCode).stream()
                .filter(candidate -> matches(candidate, keyword))
                .toList();
    }

    private List<ProductCandidate> deduplicateByUrl(List<ProductCandidate> candidates) {
        Map<String, ProductCandidate> deduplicated = new LinkedHashMap<>();
        for (ProductCandidate candidate : candidates) {
            deduplicated.putIfAbsent(candidate.url(), candidate);
        }
        return deduplicated.values().stream().toList();
    }

    private ProductCandidate verify(ProductCandidate candidate) {
        VerificationResult result = verifier.verify(candidate, htmlClient.fetch(candidate.url()));
        log.info(
                "Bank product candidate verified. bank={}, keyword={}, title={}, score={}, status={}, url={}",
                candidate.bankCode(),
                candidate.keyword(),
                candidate.title(),
                result.score(),
                result.status(),
                candidate.url()
        );
        return candidate.withScore(result.score());
    }

    private boolean matches(ProductCandidate candidate, ProductSearchKeyword keyword) {
        return ProductNameSimilarity.isSimilar(candidate.title(), keyword.canonicalName())
                || ProductNameSimilarity.isSimilar(candidate.keyword(), keyword.canonicalName());
    }
}
