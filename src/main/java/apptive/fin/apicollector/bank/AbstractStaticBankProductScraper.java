package apptive.fin.apicollector.bank;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public abstract class AbstractStaticBankProductScraper implements BankProductScraper {
    private final StaticHtmlClient htmlClient;
    private final ProductPageVerifier verifier;
    private final ProductInfoExtractor extractor;
    private final BankProductSeedCatalog seedCatalog;
    private final List<ProductLinkDiscoverer> discoverers;

    protected AbstractStaticBankProductScraper(
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

    @Override
    public List<ProductCandidate> search(ProductScrapeContext context) {
        String compactKeyword = context.keyword().compact();
        List<ProductCandidate> discoveredCandidates = discoverCandidates(context.keyword());
        List<ProductCandidate> matchedSeedCandidates = seedCandidates().stream()
                .filter(candidate -> matches(candidate, compactKeyword))
                .toList();

        List<ProductCandidate> candidates = new java.util.ArrayList<>(discoveredCandidates);
        candidates.addAll(matchedSeedCandidates);

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

    protected List<ProductCandidate> seedCandidates() {
        return seedCatalog.candidates(bankCode());
    }

    private List<ProductCandidate> discoverCandidates(ProductSearchKeyword keyword) {
        return discoverers.stream()
                .filter(discoverer -> discoverer.bankCode() == bankCode())
                .flatMap(discoverer -> discoverer.discover(keyword).stream())
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

    private boolean matches(ProductCandidate candidate, String compactKeyword) {
        String title = candidate.title() == null ? "" : candidate.title().replaceAll("\\s+", "");
        String keyword = candidate.keyword() == null ? "" : candidate.keyword().replaceAll("\\s+", "");
        return title.contains(compactKeyword)
                || compactKeyword.contains(title)
                || keyword.contains(compactKeyword)
                || compactKeyword.contains(keyword);
    }

}
