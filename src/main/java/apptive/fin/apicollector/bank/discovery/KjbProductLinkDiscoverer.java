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
public class KjbProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String LIST_URL = "https://www.kjbank.com/ib20/mnu/FPMDPTR030001";
    private static final String DETAIL_URL = "https://www.kjbank.com/ib20/mnu/FPMDPTR030100"
            + "?INBN_GDS_NO=%s&INBN_GDS_CLCD=%s&INBN_GDS_ATRB_CD=%s";

    public KjbProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.KJB;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get(LIST_URL));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element item : document.select("ul#list_goods > li")) {
            Element titleElement = item.selectFirst("strong.tit a.btn_guide");
            Element number = item.selectFirst("input[id^=INBN_GDS_NO_]");
            Element classCode = item.selectFirst("input[id^=INBN_GDS_CLCD_]");
            Element attributeCode = item.selectFirst("input[id^=INBN_GDS_ATRB_CD_]");
            if (titleElement == null || number == null || classCode == null || attributeCode == null) {
                continue;
            }
            String title = titleElement.text().replaceAll("\\s+", " ").trim();
            if (title.isBlank() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(
                    keyword,
                    title,
                    DETAIL_URL.formatted(
                            number.attr("value").trim(),
                            classCode.attr("value").trim(),
                            attributeCode.attr("value").trim()
                    )
            ));
        }
        return candidates;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of();
    }
}
