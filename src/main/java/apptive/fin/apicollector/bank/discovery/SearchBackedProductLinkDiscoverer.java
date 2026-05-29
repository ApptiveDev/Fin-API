package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.keyword.ProductNameSimilarity;
import apptive.fin.apicollector.bank.model.CandidateSource;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
abstract class SearchBackedProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    protected final StaticHtmlClient htmlClient;

    protected SearchBackedProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        this.htmlClient = htmlClient;
    }

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        List<CompletableFuture<List<ProductCandidate>>> futures = searchRequests(keyword).stream()
                .map(request -> request.fetchAsync(htmlClient)
                        .thenApply(page -> extract(keyword, page))
                        .exceptionally(e -> {
                            log.warn(
                                    "Bank product search failed. bank={}, keyword={}, url={}",
                                    bankCode(),
                                    keyword.value(),
                                    request.url(),
                                    e
                            );
                            return List.of();
                        }))
                .toList();

        for (CompletableFuture<List<ProductCandidate>> future : futures) {
            for (ProductCandidate candidate : future.join()) {
                candidates.putIfAbsent(candidate.url(), candidate);
            }
        }
        for (ProductCandidate candidate : super.discover(keyword)) {
            candidates.putIfAbsent(candidate.url(), candidate);
        }
        return new ArrayList<>(candidates.values());
    }

    protected abstract List<SearchRequest> searchRequests(ProductSearchKeyword keyword);

    protected abstract List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page);

    protected List<ProductCandidate> knownCandidates(ProductSearchKeyword keyword) {
        return super.discover(keyword);
    }

    protected ProductCandidate candidate(ProductSearchKeyword keyword, String title, String url) {
        int score = ProductNameSimilarity.score(keyword.canonicalName(), title);
        return new ProductCandidate(bankCode(), keyword.canonicalName(), title, url, CandidateSource.BANK_SEARCH, score);
    }

    protected boolean matches(ProductSearchKeyword keyword, String title) {
        return ProductNameSimilarity.isSimilar(keyword.canonicalName(), title);
    }

    protected String absoluteUrl(String baseUrl, String href) {
        return URI.create(baseUrl).resolve(href).toString();
    }

    protected record SearchRequest(
            Method method,
            String url,
            Map<String, String> data,
            Map<String, String> headers,
            Map<String, String> cookies
    ) {
        protected static SearchRequest get(String url) {
            return new SearchRequest(Method.GET, url, Map.of(), Map.of(), Map.of());
        }

        protected static SearchRequest post(String url, Map<String, String> data) {
            return new SearchRequest(Method.POST, url, data, Map.of(), Map.of());
        }

        protected static SearchRequest post(String url, Map<String, String> data, Map<String, String> headers) {
            return new SearchRequest(Method.POST, url, data, headers, Map.of());
        }

        private CompletableFuture<StaticHtmlClient.FetchedPage> fetchAsync(StaticHtmlClient htmlClient) {
            return method == Method.POST
                    ? htmlClient.postAsync(url, data, headers, cookies)
                    : htmlClient.fetchAsync(url);
        }
    }

    protected enum Method {
        GET,
        POST
    }
}
