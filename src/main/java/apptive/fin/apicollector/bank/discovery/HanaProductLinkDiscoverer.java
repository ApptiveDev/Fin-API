package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HanaProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String SEARCH_URL = "https://www.kebhana.com/cont/mall/mall08/mall0805/index.jsp";

    public HanaProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.HANA;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(
                SearchRequest.post(SEARCH_URL, searchForm(keyword.value(), "spb_2811", "pro_total")),
                SearchRequest.post(SEARCH_URL, searchForm(keyword.value(), "spb_2812", "pro_total")),
                SearchRequest.post(SEARCH_URL, searchForm(keyword.value(), "spb_2821,spb_2822,spb_2823,spb_2824,spb_2825,spb_2826", "pro_total"))
        );
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("em a[href*=/cont/mall/mall08/], .productList a[href*=/cont/mall/mall08/]")) {
            String title = link.text().replaceAll("\\s+", " ").trim();
            if (title.isBlank() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(keyword, title, absoluteUrl(page.url(), link.attr("href"))));
        }
        return candidates;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "하나 청년도약계좌",
                        "https://www.kebhana.com/cont/mall/mall08/mall0801/mall080102/1492305_115157.jsp",
                        "청년도약계좌"
                )
        );
    }

    private Map<String, String> searchForm(String keyword, String catId, String collection) {
        return Map.ofEntries(
                Map.entry("catId", catId),
                Map.entry("startCount", "0"),
                Map.entry("sort", "search_order/DESC"),
                Map.entry("collection", collection),
                Map.entry("range", "A"),
                Map.entry("searchField", "ALL"),
                Map.entry("reQuery", "2"),
                Map.entry("realQuery", ""),
                Map.entry("tabStatus", "1"),
                Map.entry("save_money", ""),
                Map.entry("sortId", ""),
                Map.entry("query", keyword)
        );
    }
}
