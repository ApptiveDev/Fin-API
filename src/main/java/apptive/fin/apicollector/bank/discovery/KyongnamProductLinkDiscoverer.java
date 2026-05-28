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
public class KyongnamProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final Pattern PRODUCT_JSON_PATTERN = Pattern.compile(
            "\"FNC_PRD_NO\"\\s*:\\s*\"([^\"]+)\".*?\"KOR_PRD_NM\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
    );
    private static final String DETAIL_URL = "https://m.knbank.co.kr/ib20/mnu/MBWSFM030103000"
            + "?fnc_prd_no=%s&ib20_cur_mnu=MBWSFM030000000&ib20_cur_wgt=MBWSFM024JNGV01M"
            + "&ib20_wc=MBWSFM024JNGV01M%%3AMBWSFM024JNGV02M";

    public KyongnamProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.KYONGNAM;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(
                SearchRequest.get("https://www.knbank.co.kr/ib20/mnu/FPMDPT020103000"),
                SearchRequest.get("https://www.knbank.co.kr/ib20/mnu/FPMDPT020109000"),
                SearchRequest.get("https://www.knbank.co.kr/ib20/mnu/FPMDPT020107000")
        );
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Matcher matcher = PRODUCT_JSON_PATTERN.matcher(page.html());
        List<ProductCandidate> candidates = new ArrayList<>();
        while (matcher.find()) {
            String productNumber = matcher.group(1);
            String title = matcher.group(2).replace("\\/", "/").replaceAll("\\s+", " ").trim();
            if (title.isBlank() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(keyword, title, DETAIL_URL.formatted(productNumber)));
        }
        return candidates;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "경남은행 청년도약계좌",
                        "https://m.knbank.co.kr/ib20/mnu/MBWSFM030103000?fnc_prd_no=0000206467&ib20_cur_mnu=MBWSFM030000000&ib20_cur_wgt=MBWSFM024JNGV01M&ib20_wc=MBWSFM024JNGV01M%3AMBWSFM024JNGV02M",
                        "청년도약계좌"
                )
        );
    }
}
