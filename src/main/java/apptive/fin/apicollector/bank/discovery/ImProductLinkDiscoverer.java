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
public class ImProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String LIST_URL = "https://www.imbank.co.kr/com_ebz_fpm_sub_main.jsp";
    private static final Pattern DETAIL_PATTERN = Pattern.compile(
            "goProductDetailByPdCd\\('([^']*)','([^']*)','([^']*)','D'\\)"
    );

    public ImProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.IM;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get(LIST_URL));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a[href*=goProductDetailByPdCd]")) {
            Matcher matcher = DETAIL_PATTERN.matcher(link.attr("href"));
            String title = title(link);
            if (title.isBlank() || !matcher.find() || !matches(keyword, title)) {
                continue;
            }
            String productCode = matcher.group(1) + matcher.group(2) + matcher.group(3);
            candidates.add(candidate(keyword, title, LIST_URL + "#product-" + productCode));
        }
        return candidates;
    }

    private String title(Element link) {
        Element list = link.parents().select("dl").first();
        if (list != null) {
            Element title = list.select("dt span").last();
            if (title != null) {
                return title.text().replaceAll("\\s+", " ").trim();
            }
        }
        String imageTitle = link.parents().select("img[alt]").stream()
                .map(image -> image.attr("alt").replaceAll("\\s+", " ").trim())
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
        if (!imageTitle.isBlank()) {
            return imageTitle.split(" ")[0].trim();
        }
        return "";
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "iM자유적금",
                        LIST_URL + "#product-10527001000001000",
                        "DGB자유적금",
                        "자유적금"
                ),
                new KnownProduct(
                        "iM청년도약계좌",
                        "https://www.imbank.co.kr/",
                        "DGB청년도약계좌",
                        "청년도약계좌"
                )
        );
    }
}
