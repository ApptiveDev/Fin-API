package apptive.fin.apicollector.bankurl.scraper;

import com.microsoft.playwright.BrowserContext;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KyongnamBankScraper extends AbstractBankProductScraper {

    // 통합검색은 띄어쓰기가 다르면(공시 "든든연금통장" vs 사이트 "든든 연금통장") 결과가 없어서 상품 목록도 함께 본다.
    // 적금(FPMDPT020103000)과 입출금(FPMDPT020102000, KFB 파킹통장) 목록.
    private static final List<String> URLS = List.of(
            "https://www.knbank.co.kr/ib20/mnu/UFSSER000000001?collection=ALL&query={q}&language=kr",
            "https://www.knbank.co.kr/ib20/mnu/FPMDPT020103000",
            "https://www.knbank.co.kr/ib20/mnu/FPMDPT020102000"
    );
    // 목록 URL에 상품번호를 붙이면 상세가 아니라 목록이 다시 열린다. 전체상품 상세는 이 형식으로 열린다.
    private static final String DETAIL_PATH = "/ib20/mnu/FPMCOM990000000?FNC_PRD_NO=";

    @Override
    public String providerCode() {
        return "0010024";
    }

    @Override
    public Set<String> allowedDomains() {
        return Set.of("knbank.co.kr");
    }

    @Override
    protected List<ProductCandidate> search(BrowserContext context, String productName) {
        List<ProductCandidate> candidates = new ArrayList<>();
        for (String query : queryVariants(productName)) {
            candidates.addAll(searchPages(context, query, URLS, this::extractBankProducts, false));
        }
        return dedupe(candidates);
    }

    List<ProductCandidate> extractBankProducts(Document document, String currentUrl) {
        List<ProductCandidate> candidates = new ArrayList<>(extractProductsWithSelectors(
                document,
                currentUrl,
                List.of(".product_list li", ".product-list > li", ".list-con-area", ".goods-list li"),
                List.of(".name a", ".tit a", ".tit", ".product_tit", "strong a", "strong", "a"),
                false
        ));
        for (Element anchor : document.select("dt a[href*=FNC_PRD_NO]")) {
            String name = cleanText(anchor.text());
            String url = urlFromAnchor(anchor, currentUrl);
            if (looksLikeProductName(name) && !url.isBlank()) {
                candidates.add(new ProductCandidate(name, url));
            }
        }
        Pattern detailPattern = Pattern.compile("goDetail\\(['\"]([^'\"]+)['\"]");
        for (Element anchor : document.select("a[onclick]")) {
            Matcher matcher = detailPattern.matcher(anchor.attr("onclick"));
            if (!matcher.find()) {
                continue;
            }
            String name = cleanText(anchor.text());
            if (looksLikeProductName(name)) {
                candidates.add(new ProductCandidate(
                        name,
                        absoluteUrl(DETAIL_PATH + matcher.group(1) + "&DUP_CHK=N", currentUrl)
                ));
            }
        }
        return dedupe(candidates);
    }
}
