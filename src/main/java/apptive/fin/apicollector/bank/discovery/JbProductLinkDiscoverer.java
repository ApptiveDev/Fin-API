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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JbProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final Pattern LOCATION_PATTERN = Pattern.compile("jb_location\\('([^']+)'\\)");

    public JbProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.JB;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get("https://www.jbbank.co.kr/gdnc_spnd.act"));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element item : document.select("dt:has(a[onclick*=jb_location])")) {
            Element titleElement = item.selectFirst("strong");
            Element link = item.selectFirst("a[onclick*=jb_location]");
            if (titleElement == null || link == null) {
                continue;
            }
            String title = titleElement.text().replaceAll("\\s+", " ").trim();
            Matcher matcher = LOCATION_PATTERN.matcher(link.attr("onclick"));
            if (title.isBlank() || !matcher.find() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(keyword, title, absoluteUrl(page.url(), matcher.group(1))));
        }
        return candidates;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "행복키움 통장",
                        "https://www.jbbank.co.kr/gdnc_spnd_comp_detail01.act",
                        "희망드림 자산형성저축",
                        "행복키움통장"
                )
        );
    }
}
