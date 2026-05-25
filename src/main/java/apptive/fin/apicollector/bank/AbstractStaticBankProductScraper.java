package apptive.fin.apicollector.bank;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class AbstractStaticBankProductScraper implements BankProductScraper {
    private final StaticHtmlClient htmlClient;
    private final ProductPageVerifier verifier;
    private final ProductInfoExtractor extractor;

    protected AbstractStaticBankProductScraper(
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor
    ) {
        this.htmlClient = htmlClient;
        this.verifier = verifier;
        this.extractor = extractor;
    }

    @Override
    public List<ProductCandidate> search(ProductScrapeContext context) {
        String compactKeyword = context.keyword().compact();
        return seedCandidates(context.keyword().value()).stream()
                .filter(candidate -> matches(candidate, compactKeyword))
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

    protected abstract List<ProductCandidate> seedCandidates(String keyword);

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

    protected ProductCandidate seed(String keyword, String title, String url) {
        return new ProductCandidate(bankCode(), keyword, title, url, CandidateSource.MANUAL_SEED, 0);
    }
}
