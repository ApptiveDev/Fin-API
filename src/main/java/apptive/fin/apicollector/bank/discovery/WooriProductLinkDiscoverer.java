package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.ProductLinkDiscoverer;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.keyword.ProductNameSimilarity;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.CandidateSource;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WooriProductLinkDiscoverer implements ProductLinkDiscoverer {
    private static final String SEARCH_URL = "https://spot.wooribank.com/pot/jcc?withyou=PODEP0019&__ID=c007656";
    private static final Pattern DETAIL_PATTERN = Pattern.compile("goDetails\\(\\s*\\d+\\s*,\\s*'([^']+)'");

    private final StaticHtmlClient htmlClient;

    @Override
    public BankCode bankCode() {
        return BankCode.WOORI;
    }

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        try {
            StaticHtmlClient.FetchedPage page = htmlClient.post(SEARCH_URL, searchForm(keyword.value()));
            return extract(keyword, page.html());
        }
        catch (Exception e) {
            log.warn("Woori product search failed. keyword={}", keyword.value(), e);
            return List.of();
        }
    }

    private Map<String, String> searchForm(String keyword) {
        return Map.ofEntries(
                Map.entry("alignGb", "RCM"),
                Map.entry("PRD_CD_SCH", ""),
                Map.entry("NowPage", "1"),
                Map.entry("PAGE_ID", "PODEP0019"),
                Map.entry("ALL_GB", ""),
                Map.entry("depKindP", ""),
                Map.entry("prdCharP", ""),
                Map.entry("joinTermP", ""),
                Map.entry("depTypeP", ""),
                Map.entry("prdNameP", keyword),
                Map.entry("intnPrdP", ""),
                Map.entry("depKind", ""),
                Map.entry("prdName", keyword)
        );
    }

    private List<ProductCandidate> extract(ProductSearchKeyword keyword, String html) {
        Document document = Jsoup.parse(html);
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        for (Element link : document.select("dt.name a[onclick*=goDetails]")) {
            Matcher matcher = DETAIL_PATTERN.matcher(link.attr("onclick"));
            if (!matcher.find()) {
                continue;
            }
            String productCode = matcher.group(1);
            String title = link.text();
            if (!matches(keyword.canonicalName(), title)) {
                continue;
            }
            int score = ProductNameSimilarity.score(keyword.canonicalName(), title);
            String url = "https://spot.wooribank.com/pot/Dream?withyou=PODEP0019"
                    + "&cc=c007095%3Ac009166%3Bc012263%3Ac012399"
                    + "&PLM_PDCD=" + productCode
                    + "&PRD_CD=" + productCode
                    + "&ALL_GB=&depKind=";
            candidates.putIfAbsent(url, new ProductCandidate(bankCode(), keyword.canonicalName(), title, url, CandidateSource.BANK_SEARCH, score));
        }
        return new ArrayList<>(candidates.values());
    }

    private boolean matches(String keyword, String title) {
        return ProductNameSimilarity.isSimilar(keyword, title);
    }
}
