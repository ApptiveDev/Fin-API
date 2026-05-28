package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Component
public class IbkProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String LIST_URL = "https://mybank.ibk.co.kr/uib/jsp/guest/ntr/ntr70/ntr7010/PNTR701000_m.jsp"
            + "?APLY_EFNC_MENU_ID=P0104010000&SCRE_ID=PNTR701000_m&MENU_DIV=GNB";
    private static final String DETAIL_URL = "https://mybank.ibk.co.kr/uib/jsp/guest/ntr/ntr70/ntr7010/PNTR701000_i2.jsp"
            + "?MENU_DIV=GNB&lncd=%s&grcd=%s&tmcd=%s&pdcd=%s&wvcd=%s&i_trns_biz_kncd=%s";
    private static final Pattern DETAIL_PATTERN = Pattern.compile(
            "uf_showDetail\\('([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'\\)"
    );

    public IbkProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.IBK;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(pageIndex -> SearchRequest.post(LIST_URL, Map.of(
                        "pageIndex", String.valueOf(pageIndex),
                        "product_flag", "Y",
                        "product_nm", "",
                        "prdcSaleYN", "Y"
                )))
                .toList();
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a.stit[onclick*=uf_showDetail], strong.stit[onclick*=uf_showDetail]")) {
            String title = link.text().replaceAll("\\s+", " ").trim();
            Matcher matcher = DETAIL_PATTERN.matcher(link.attr("onclick"));
            if (title.isBlank() || !matcher.find() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(
                    keyword,
                    title,
                    DETAIL_URL.formatted(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            matcher.group(4),
                            matcher.group(5).replace("*", "%2A"),
                            urlEncode(matcher.group(6))
                    )
            ));
        }
        return candidates;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "IBK청년도약계좌",
                        "https://mybank.ibk.co.kr/uib/jsp/guest/ntr/ntr70/ntr7010/PNTR701000_i2.jsp?MENU_DIV=GNB&lncd=01&grcd=21&tmcd=121&pdcd=0125&wvcd=%2A%2A%2A%2A%2A%2A%2A%2A%2A%2A%2A&i_trns_biz_kncd=IBK%EC%B2%AD%EB%85%84%EB%8F%84%EC%95%BD%EA%B3%84%EC%A2%8C",
                        "청년도약계좌"
                )
        );
    }
}
