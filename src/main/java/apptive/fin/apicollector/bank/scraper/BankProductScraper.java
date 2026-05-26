package apptive.fin.apicollector.bank.scraper;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductScrapeContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface BankProductScraper {
    BankCode bankCode();

    List<ProductCandidate> search(ProductScrapeContext context);

    default Optional<ProductCandidate> select(ProductScrapeContext context, List<ProductCandidate> candidates) {
        return candidates.stream()
                .max(Comparator.comparingInt(ProductCandidate::score));
    }

    BankProductInfo extractInfo(ProductCandidate candidate);
}
