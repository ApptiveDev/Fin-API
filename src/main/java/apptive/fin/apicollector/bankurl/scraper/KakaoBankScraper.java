package apptive.fin.apicollector.bankurl.scraper;

import com.microsoft.playwright.BrowserContext;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class KakaoBankScraper extends AbstractBankProductScraper {

    private static final String LIST_URL = "https://www.kakaobank.com/products/withdrawal";

    @Override
    public String providerCode() {
        return "0015130";
    }

    @Override
    public Set<String> allowedDomains() {
        return Set.of("kakaobank.com");
    }

    @Override
    protected List<ProductCandidate> search(BrowserContext context, String productName) {
        return searchPages(context, productName, List.of(LIST_URL), this::extractProductLinks, false);
    }

    List<ProductCandidate> extractProductLinks(Document document, String currentUrl) {
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element anchor : document.select("a[href*=/products/],a[href*=/p/]")) {
            // 목록 링크는 "계좌 속 여유자금을 안전하게 세이프박스"처럼 소개 문구와 상품명(<strong>)이 함께 있다.
            Element strong = anchor.selectFirst("strong");
            String name = cleanText(strong == null ? anchor.text() : strong.text());
            String url = urlFromAnchor(anchor, currentUrl);
            if (isCandidateName(name) && !url.isBlank()) {
                candidates.add(new ProductCandidate(name, url));
            }
        }
        return dedupe(candidates);
    }
}
