package apptive.fin.apicollector.bankurl.scraper;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.RequestOptions;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class JejuBankScraper extends AbstractBankProductScraper {

    // 예적금 목록 화면(예금·적금·입출금)이 부르는 JSON. 유형 필터(typCdVal)를 비우면 전체가 한 번에 온다(2026-09-27 전체 52개).
    // 목록 화면 HTML은 이 JSON으로 나중에 채워져서 페이지를 열어 읽으면 상품이 없다.
    private static final String PRODUCT_LIST_API = "https://www.jejubank.co.kr/hmpg/prdGdnc/sid/mndp.doax"
            + "?orderBy=rcmd&typCdVal=&jnptVal=&joingTrmCdVal=&chrcCdVal=&viewCount=500&isMore=0&moreIdx=&lastId=&keyword=";
    private static final String DETAIL_URL = "https://www.jejubank.co.kr/hmpg/prdGdnc/sid/mndp.do?mode=detail&prdId=";
    // 목록에서 못 찾을 때만 쓴다. 띄어쓰기가 다르면(사이트 "퍼스트 적금" vs "퍼스트적금") 결과가 없다.
    private static final String SEARCH_URL = "https://www.jejubank.co.kr/hmpg/intgSrch.do?intgSrchText={q}";

    private final ObjectMapper objectMapper;

    public JejuBankScraper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerCode() {
        return "0010020";
    }

    @Override
    public Set<String> allowedDomains() {
        return Set.of("jejubank.co.kr");
    }

    @Override
    protected List<ProductCandidate> search(BrowserContext context, String productName) {
        List<ProductCandidate> candidates = new ArrayList<>(extractProductsFromApi(requestProducts(context)));
        if (candidates.stream().noneMatch(candidate -> compact(candidate.name()).contains(compact(productName)))) {
            for (String query : jejuQueryVariants(productName)) {
                candidates.addAll(searchPages(context, query, List.of(SEARCH_URL), this::extractBankProducts, false));
                if (candidates.stream().anyMatch(candidate -> compact(candidate.name()).contains(compact(productName)))) {
                    break;
                }
            }
        }
        List<ProductCandidate> preferred = preferMatchingInterestType(productName, dedupe(candidates));
        String target = compact(productName);
        return preferred.stream()
                .map(candidate -> target.isBlank() || !compact(candidate.name()).contains(target)
                        ? candidate
                        : new ProductCandidate(cleanText(productName), candidate.url()))
                .toList();
    }

    String requestProducts(BrowserContext context) {
        APIResponse response = context.request().get(PRODUCT_LIST_API, RequestOptions.create()
                .setHeader("Accept", "application/json")
                .setHeader("X-Requested-With", "XMLHttpRequest")
                .setHeader("Referer", "https://www.jejubank.co.kr/hmpg/prdGdnc/sid.do")
                .setTimeout(30_000));
        try {
            if (!response.ok()) {
                throw new IllegalStateException("Jeju Bank product list returned HTTP " + response.status());
            }
            return response.text();
        } finally {
            response.dispose();
        }
    }

    List<ProductCandidate> extractProductsFromApi(String responseBody) {
        List<ProductCandidate> candidates = new ArrayList<>();
        JsonNode products = objectMapper.readTree(responseBody).path("data");
        if (!products.isArray()) {
            return candidates;
        }
        for (JsonNode product : products) {
            String name = cleanText(product.path("prdNm").asString(""));
            String id = cleanText(product.path("prdId").asString(""));
            if (!name.isBlank() && !id.isBlank()) {
                candidates.add(new ProductCandidate(name, DETAIL_URL + id));
            }
        }
        return dedupe(candidates);
    }

    List<ProductCandidate> extractBankProducts(Document document, String currentUrl) {
        List<ProductCandidate> candidates = new ArrayList<>(extractProductsWithSelectors(
                document,
                currentUrl,
                List.of(".product_list li", ".product-list > li", ".list-con-area", ".result_list li"),
                List.of(".tit a", ".name a", "dt a", "strong a", ".tit", ".name", "strong"),
                false
        ));
        for (Element row : document.select("#fnncPrdTable tbody tr")) {
            Element nameElement = row.selectFirst("p.size18");
            Element anchor = row.selectFirst("a.view-btn[href]");
            String name = cleanText(nameElement == null ? "" : nameElement.text());
            String url = anchor == null ? "" : urlFromAnchor(anchor, currentUrl);
            if (!name.isBlank() && !url.isBlank()) {
                candidates.add(new ProductCandidate(name, url));
            }
        }
        return dedupe(candidates);
    }

    // 지급식 변형(예: 제주Dream정기예금 월이자형/만기형)을 가른다.
    List<ProductCandidate> preferMatchingInterestType(
            String productName,
            List<ProductCandidate> candidates
    ) {
        return preferVariantOfSameProduct(productName, candidates, List.of("만기", "월이자", "선이자"));
    }

    private List<String> jejuQueryVariants(String productName) {
        List<String> variants = new ArrayList<>(queryVariants(productName));
        for (String variant : List.copyOf(variants)) {
            String compact = variant.replaceAll("[^0-9a-zA-Z가-힣]", "");
            variants.add(compact);
            variants.add(compact.toLowerCase(Locale.ROOT));
            variants.add(compact.replaceFirst("(?i)^jbank", ""));
        }
        return variants.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    private String compact(String value) {
        return cleanText(value).replaceAll("[^0-9a-zA-Z가-힣]", "").toLowerCase(Locale.ROOT);
    }
}
