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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
class StaticBankProductScraper implements BankProductScraper {
    private final BankCode bankCode;
    private final StaticHtmlClient htmlClient;
    private final ProductPageVerifier verifier;
    private final ProductInfoExtractor extractor;
    private final BankProductSeedCatalog seedCatalog;
    private final List<ProductLinkDiscoverer> discoverers;
    private final ConcurrentMap<String, CompletableFuture<StaticHtmlClient.FetchedPage>> pageCache = new ConcurrentHashMap<>();

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

        List<CompletableFuture<ProductCandidate>> futures = deduplicateByUrl(candidates).stream()
                .map(this::verifyAsync)
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
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
        return extractor.extract(candidate, fetchPage(candidate.url()).join());
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

    private CompletableFuture<ProductCandidate> verifyAsync(ProductCandidate candidate) {
        return fetchPage(candidate.url()).thenApply(page -> verify(candidate, page));
    }

    private ProductCandidate verify(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        VerificationResult result = verifier.verify(candidate, page);
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

    private CompletableFuture<StaticHtmlClient.FetchedPage> fetchPage(String url) {
        return pageCache.computeIfAbsent(url, htmlClient::fetchAsync);
    }

    private boolean matches(ProductCandidate candidate, ProductSearchKeyword keyword) {
        return ProductNameSimilarity.isSimilar(candidate.title(), keyword.canonicalName())
                || ProductNameSimilarity.isSimilar(candidate.keyword(), keyword.canonicalName());
    }
}
