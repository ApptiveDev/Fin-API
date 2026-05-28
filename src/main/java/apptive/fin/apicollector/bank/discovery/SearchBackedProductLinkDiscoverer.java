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

@Slf4j
abstract class SearchBackedProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    protected final StaticHtmlClient htmlClient;

    protected SearchBackedProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        this.htmlClient = htmlClient;
    }

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        for (SearchRequest request : searchRequests(keyword)) {
            try {
                StaticHtmlClient.FetchedPage page = request.fetch(htmlClient);
                for (ProductCandidate candidate : extract(keyword, page)) {
                    candidates.putIfAbsent(candidate.url(), candidate);
                }
            }
            catch (Exception e) {
                log.warn("Bank product search failed. bank={}, keyword={}, url={}", bankCode(), keyword.value(), request.url(), e);
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
            Map<String, String> data
    ) {
        protected static SearchRequest get(String url) {
            return new SearchRequest(Method.GET, url, Map.of());
        }

        protected static SearchRequest post(String url, Map<String, String> data) {
            return new SearchRequest(Method.POST, url, data);
        }

        private StaticHtmlClient.FetchedPage fetch(StaticHtmlClient htmlClient) {
            return method == Method.POST
                    ? htmlClient.post(url, data)
                    : htmlClient.fetch(url);
        }
    }

    protected enum Method {
        GET,
        POST
    }
}
