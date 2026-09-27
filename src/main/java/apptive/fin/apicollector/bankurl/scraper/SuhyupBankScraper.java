package apptive.fin.apicollector.bankurl.scraper;

import com.microsoft.playwright.BrowserContext;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SuhyupBankScraper extends AbstractBankProductScraper {

    // 모바일 목록은 추천성 목록이라 일부 상품만 있다. 빠진 상품(Sh내가만든통장, Sh평생주거래우대통장, Sh월복리자유적금 등)은
    // PC 상품 카테고리 페이지에 있다: 입출금이자유로운상품(FPD00010), 목돈마련(FPD00008).
    private static final String LIST_URL = "https://m.suhyup-bank.com/ib20/mnu/WBK00172";
    private static final List<String> CATEGORY_URLS = List.of(
            "https://www.suhyup-bank.com/ib20/mnu/FPD00010",
            "https://www.suhyup-bank.com/ib20/mnu/FPD00008"
    );
    private static final Pattern INTEREST_PRODUCT_CODE = Pattern.compile(
            "interest\\(\\s*'[^']*'\\s*,\\s*'[^']*'\\s*,\\s*'(D\\d+)'"
    );
    private static final String DETAIL_URL = "https://www.suhyup-bank.com/ib20/mnu/FPD00118/"
            + "_menuId/FPD00124/_productCode/";

    @Override
    public String providerCode() {
        return "0014807";
    }

    @Override
    public Set<String> allowedDomains() {
        return Set.of("suhyup-bank.com");
    }

    @Override
    protected List<ProductCandidate> search(BrowserContext context, String productName) {
        List<ProductCandidate> candidates = new ArrayList<>(searchPages(
                context, productName, List.of(LIST_URL), this::extractMobileProducts, false
        ));
        candidates.addAll(searchPages(context, productName, CATEGORY_URLS, this::extractCategoryProducts, false));
        return dedupe(candidates);
    }

    List<ProductCandidate> extractCategoryProducts(Document document, String currentUrl) {
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a.go-detail")) {
            Element nameElement = link.selectFirst("dt");
            Element item = link.closest(".pro_list_area");
            Element interestButton = item == null ? null : item.selectFirst("[onclick*=interest(]");
            if (nameElement == null || interestButton == null) {
                continue;
            }
            String name = cleanText(nameElement.text());
            Matcher matcher = INTEREST_PRODUCT_CODE.matcher(interestButton.attr("onclick"));
            if (matcher.find() && isCandidateName(name)) {
                candidates.add(new ProductCandidate(name, DETAIL_URL + matcher.group(1)));
            }
        }
        return dedupe(candidates);
    }

    List<ProductCandidate> extractMobileProducts(Document document, String currentUrl) {
        List<ProductCandidate> candidates = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (Element item : document.select("li.item[data-prodcd]")) {
            String code = cleanText(item.attr("data-prodcd"));
            Element nameElement = item.selectFirst(".pdt-nm");
            String name = cleanText(nameElement == null ? "" : nameElement.text());
            if (code.isBlank() || "deduction".equals(code) || !seenCodes.add(code)
                    || !looksLikeProductName(name)) {
                continue;
            }
            candidates.add(new ProductCandidate(name, DETAIL_URL + code));
        }
        return dedupe(candidates);
    }

    @Override
    protected double settleMillis() {
        return 2_000;
    }
}
