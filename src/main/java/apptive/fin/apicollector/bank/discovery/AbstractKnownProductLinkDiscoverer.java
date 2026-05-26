package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.ProductLinkDiscoverer;
import apptive.fin.apicollector.bank.keyword.ProductNameSimilarity;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.CandidateSource;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;

import java.util.List;

abstract class AbstractKnownProductLinkDiscoverer implements ProductLinkDiscoverer {

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        return products().stream()
                .filter(product -> product.matches(keyword.canonicalName()))
                .map(product -> product.toCandidate(bankCode(), keyword.canonicalName()))
                .toList();
    }

    protected abstract List<KnownProduct> products();

    protected record KnownProduct(
            String title,
            String url,
            List<String> aliases
    ) {
        protected KnownProduct(String title, String url, String... aliases) {
            this(title, url, List.of(aliases));
        }

        private boolean matches(String canonicalName) {
            return ProductNameSimilarity.isSimilar(canonicalName, title)
                    || aliases.stream().anyMatch(alias -> ProductNameSimilarity.isSimilar(canonicalName, alias));
        }

        private ProductCandidate toCandidate(BankCode bankCode, String canonicalName) {
            int score = Math.max(
                    ProductNameSimilarity.score(canonicalName, title),
                    aliases.stream()
                            .map(alias -> ProductNameSimilarity.score(canonicalName, alias))
                            .max(Integer::compareTo)
                            .orElse(0)
            );
            return new ProductCandidate(bankCode, canonicalName, title, url, CandidateSource.BANK_SEARCH, score);
        }
    }
}
