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

@Component
public class SuhyupProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String LIST_URL = "https://www.suhyup-bank.com/ib20/mnu/FPD00124";

    public SuhyupProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.SUHYUP;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get(LIST_URL));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a.go-detail")) {
            String title = title(link);
            if (title.isBlank() || !matches(keyword, title)) {
                continue;
            }
            String index = link.attr("href").replace("#", "").trim();
            candidates.add(candidate(keyword, title, LIST_URL + "#product-" + index));
        }
        return candidates;
    }

    private String title(Element link) {
        String title = link.attr("title").replace("상세보기", "").trim();
        if (!title.isBlank()) {
            return title;
        }
        Element term = link.selectFirst("dt");
        return term == null ? "" : term.text().replaceAll("\\s+", " ").trim();
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct("Sh월복리자유적금", LIST_URL + "#product-258", "월복리자유적금"),
                new KnownProduct("헤이(Hey)정기예금", LIST_URL + "#product-801", "Hey정기예금")
        );
    }
}
