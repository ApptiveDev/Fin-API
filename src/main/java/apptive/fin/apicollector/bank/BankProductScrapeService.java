package apptive.fin.apicollector.bank;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.bank.keyword.ProductSearchKeywordExtractor;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductScrapeContext;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import apptive.fin.apicollector.bank.scraper.BankProductScraper;
import apptive.fin.apicollector.bank.scraper.BankProductScraperFactory;
import apptive.fin.apicollector.bank.sync.BankProductPropertySyncService;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.repository.ProductRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankProductScrapeService {
    private static final int BANK_SCRAPE_PARALLELISM = Math.min(8, BankCode.values().length);

    private final ProductRepository productRepository;
    private final ProductSearchKeywordExtractor keywordExtractor;
    private final BankProductScraperFactory scraperFactory;
    private final BankProductPropertySyncService syncService;
    private final ExecutorService bankScrapeExecutor = Executors.newFixedThreadPool(
            BANK_SCRAPE_PARALLELISM,
            namedThreadFactory()
    );

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
                List<CompletableFuture<BankScrapeResult>> futures = Arrays.stream(BankCode.values())
                        .map(bankCode -> CompletableFuture.supplyAsync(
                                () -> scrapeBank(context, bankCode),
                                bankScrapeExecutor
                        ))
                        .toList();

                List<BankScrapeResult> results = futures.stream()
                        .map(CompletableFuture::join)
                        .toList();

                candidateCount += results.stream().mapToInt(BankScrapeResult::candidateCount).sum();
                savedCount += results.stream().mapToInt(BankScrapeResult::savedCount).sum();
                failedCount += results.stream().mapToInt(BankScrapeResult::failedCount).sum();
            }
        }

        return new ScrapeSummary(productCount, candidateCount, savedCount, failedCount);
    }

    @PreDestroy
    void shutdownExecutor() {
        bankScrapeExecutor.shutdown();
    }

    private BankScrapeResult scrapeBank(ProductScrapeContext context, BankCode bankCode) {
        BankProductScraper scraper = scraperFactory.create(bankCode);
        try {
            List<ProductCandidate> candidates = scraper.search(context);
            var selected = scraper.select(context, candidates);
            if (selected.isEmpty()) {
                return new BankScrapeResult(candidates.size(), 0, 0);
            }
            BankProductInfo info = scraper.extractInfo(selected.get());
            syncService.sync(context.product(), info);
            return new BankScrapeResult(candidates.size(), 1, 0);
        }
        catch (Exception e) {
            log.warn(
                    "Bank product scrape failed. productId={}, keyword={}, bank={}",
                    context.product().getId(),
                    context.keyword().value(),
                    scraper.bankCode(),
                    e
            );
            return new BankScrapeResult(0, 0, 1);
        }
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "bank-product-scrape-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public record ScrapeSummary(
            int productCount,
            int candidateCount,
            int savedCount,
            int failedCount
    ) {
    }

    private record BankScrapeResult(
            int candidateCount,
            int savedCount,
            int failedCount
    ) {
    }
}
