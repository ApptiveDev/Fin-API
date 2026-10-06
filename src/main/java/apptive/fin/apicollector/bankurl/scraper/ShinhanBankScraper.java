package apptive.fin.apicollector.bankurl.scraper;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.RequestOptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShinhanBankScraper extends AbstractBankProductScraper {

    private static final String SEARCH_URL = "https://bank.shinhan.com/index.jsp#020105010000";
    private static final String SITEMAP_URL = "https://m.shinhan.com/sitemap.xml";
    // sitemap에 없는 상품용. cr은 화면 경로이고 상품은 pcd로 열린다(예금·적금·입출금 모두 같은 cr로 확인).
    private static final String BRIDGE_URL = "https://bank.shinhan.com/bank_bridge.jsp?cr=020102010110&pcd=";
    private static final double SEARCH_BOX_TIMEOUT_MILLIS = 15_000;

    @Override
    public String providerCode() {
        return "0011625";
    }

    @Override
    public Set<String> allowedDomains() {
        return Set.of("shinhan.com");
    }

    @Override
    protected List<ProductCandidate> search(BrowserContext context, String productName) {
        Map<String, String> urlsByProductCode = fetchSitemapUrls(context);
        List<ProductCandidate> candidates = new ArrayList<>();
        try (Page page = context.newPage()) {
            navigate(page, SEARCH_URL);
            searchProduct(page, productName);
            settle(page);
            for (PageContent content : pageContents(page)) {
                for (ProductCandidate codeCandidate : extractProductCodes(
                        Jsoup.parse(content.html(), content.url()), content.url()
                )) {
                    String url = urlsByProductCode.getOrDefault(codeCandidate.url(), fallbackUrl(codeCandidate.url()));
                    candidates.add(new ProductCandidate(codeCandidate.name(), url));
                }
            }
        }
        return dedupe(candidates);
    }

    List<ProductCandidate> extractProductCodes(Document document, String currentUrl) {
        List<ProductCandidate> result = new ArrayList<>();
        for (Element block : document.select(".listTyProducts > li")) {
            String name = bestName(block, List.of(".prdtName a", ".prdtName"));
            String productCode = textByPartialId(block, "상품코드");
            if (looksLikeProductName(name) && !productCode.isBlank()) {
                result.add(new ProductCandidate(name, productCode));
            }
        }
        return result;
    }

    private Map<String, String> fetchSitemapUrls(BrowserContext context) {
        Map<String, String> result = new HashMap<>();
        try {
            APIResponse response = context.request().get(
                    SITEMAP_URL, RequestOptions.create().setTimeout(30_000)
            );
            Document document = Jsoup.parse(response.text(), "", Parser.xmlParser());
            for (Element location : document.select("loc")) {
                String url = cleanText(location.text());
                String productCode = queryValue(url, "pid");
                if (productCode.isBlank()) {
                    continue;
                }
                if (!result.containsKey(productCode) || "now".equals(queryValue(url, "type"))) {
                    result.put(productCode, url);
                }
            }
        } catch (RuntimeException ignored) {
            // Fallback URLs are generated from product codes when the sitemap is unavailable.
        }
        return result;
    }

    // 검색창이 늦게 뜨면 검색 없이 기본 목록(적금)에서 엉뚱한 후보를 고르게 된다. 끝내 뜨지 않으면 실패로 둔다.
    private void searchProduct(Page page, String productName) {
        Locator input = page.locator("#tbx_상품검색어").first();
        try {
            input.waitFor(new Locator.WaitForOptions().setTimeout(SEARCH_BOX_TIMEOUT_MILLIS));
        } catch (PlaywrightException e) {
            throw new IllegalStateException("Shinhan product search box did not appear", e);
        }
        try {
            input.fill(productName);
            Locator button = page.locator("#btn_검색").first();
            if (button.count() > 0) {
                button.click();
            } else {
                input.press("Enter");
            }
        } catch (PlaywrightException ignored) {
            // Empty result is handled by the shared service.
        }
    }

    private String textByPartialId(Element block, String partialId) {
        for (Element element : block.select("[id]")) {
            if (element.id().contains(partialId)) {
                String text = cleanText(element.text());
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    String fallbackUrl(String productCode) {
        return BRIDGE_URL + productCode;
    }

    private String queryValue(String url, String key) {
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(key) + "=([^&]+)").matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    @Override
    protected double settleMillis() {
        return 3_000;
    }
}
