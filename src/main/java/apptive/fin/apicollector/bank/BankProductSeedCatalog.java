package apptive.fin.apicollector.bank;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class BankProductSeedCatalog {
    private final BankProductSeedProperties properties;

    public BankProductSeedCatalog(BankProductSeedProperties properties) {
        this.properties = properties;
    }

    public List<ProductCandidate> candidates(BankCode bankCode) {
        return properties.seeds().stream()
                .filter(BankProductSeedProperties.Seed::enabled)
                .filter(seed -> seed.bankCode() == bankCode)
                .flatMap(seed -> keywords(seed).stream()
                        .map(keyword -> new ProductCandidate(
                                seed.bankCode(),
                                keyword,
                                seed.title(),
                                seed.url(),
                                CandidateSource.MANUAL_SEED,
                                0
                        )))
                .toList();
    }

    private Set<String> keywords(BankProductSeedProperties.Seed seed) {
        Set<String> keywords = new LinkedHashSet<>();
        if (seed.keyword() != null && !seed.keyword().isBlank()) {
            keywords.add(seed.keyword());
        }
        seed.aliases().stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .forEach(keywords::add);
        return keywords;
    }
}
