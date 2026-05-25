package apptive.fin.apicollector.bank;

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
