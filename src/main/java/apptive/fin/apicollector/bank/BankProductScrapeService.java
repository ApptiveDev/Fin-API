package apptive.fin.apicollector.bank;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankProductScrapeService {
    private final ProductRepository productRepository;
    private final ProductSearchKeywordExtractor keywordExtractor;
    private final List<BankProductScraper> scrapers;
    private final BankProductPropertySyncService syncService;

    public ScrapeSummary scrapeOntongProducts() {
        int productCount = 0;
        int candidateCount = 0;
        int savedCount = 0;
        int failedCount = 0;

        for (Product product : productRepository.findAllBySourceCodeAndType(Source.ONTONG.name(), ProductType.POLICY)) {
            List<ProductSearchKeyword> keywords = keywordExtractor.extract(product);
            if (keywords.isEmpty()) {
                continue;
            }
            productCount++;
            log.info(
                    "Bank product scrape target. productId={}, productName={}, keywords={}",
                    product.getId(),
                    product.getProductName(),
                    keywords.stream().map(ProductSearchKeyword::value).toList()
            );

            for (ProductSearchKeyword keyword : keywords) {
                ProductScrapeContext context = new ProductScrapeContext(product, keyword);
                for (BankProductScraper scraper : scrapers) {
                    try {
                        List<ProductCandidate> candidates = scraper.search(context);
                        candidateCount += candidates.size();
                        var selected = scraper.select(context, candidates);
                        if (selected.isEmpty()) {
                            continue;
                        }
                        BankProductInfo info = scraper.extractInfo(selected.get());
                        syncService.sync(product, info);
                        savedCount++;
                    }
                    catch (Exception e) {
                        failedCount++;
                        log.warn(
                                "Bank product scrape failed. productId={}, keyword={}, bank={}",
                                product.getId(),
                                keyword.value(),
                                scraper.bankCode(),
                                e
                        );
                    }
                }
            }
        }

        return new ScrapeSummary(productCount, candidateCount, savedCount, failedCount);
    }

    public record ScrapeSummary(
            int productCount,
            int candidateCount,
            int savedCount,
            int failedCount
    ) {
    }
}
