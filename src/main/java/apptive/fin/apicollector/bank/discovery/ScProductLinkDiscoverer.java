package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String LIST_URL = "https://www.standardchartered.co.kr/np/kr/pl/se/SavingList.jsp";
    private static final Pattern DETAIL_PATTERN = Pattern.compile("SavingDetail\\.jsp\\?id=([0-9]+).*?PRDCT_NM['\"]?\\)?[:=]?\\s*['\"]([^'\"]+)");

    public ScProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.SC;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get(LIST_URL));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Matcher matcher = DETAIL_PATTERN.matcher(page.html());
        List<ProductCandidate> candidates = new ArrayList<>();
        while (matcher.find()) {
            String title = matcher.group(2).replaceAll("\\s+", " ").trim();
            if (title.isBlank() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(
                    keyword,
                    title,
                    "https://www.standardchartered.co.kr/np/kr/pl/se/SavingDetail.jsp?id=" + matcher.group(1)
            ));
        }
        return candidates;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct("제일EZ통장", "https://www.standardchartered.co.kr/np/kr/pl/se/SavingDetail.jsp?id=3989"),
                new KnownProduct("e-그린세이브예금", "https://www.standardchartered.co.kr/np/kr/pl/se/SavingDetail.jsp?id=2345"),
                new KnownProduct("두드림적금", "https://www.standardchartered.co.kr/np/kr/pl/se/SavingDetail.jsp?id=1591"),
                new KnownProduct("세이프저축예금", "https://www.standardchartered.co.kr/np/kr/pl/se/SavingDetail.jsp?id=47")
        );
    }
}
