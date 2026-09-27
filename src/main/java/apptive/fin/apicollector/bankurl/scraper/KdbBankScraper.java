package apptive.fin.apicollector.bankurl.scraper;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KDB산업은행은 예금 목록 페이지(BMDEWP01N00.act)가 부르는 목록 API로 예금·적금·입출금 상품을 한 번에 받는다.
 * 카테고리 필터(CTG_N1_SNO)를 비우면 전체가 오고, 페이지당 개수를 넉넉히 줘서 한 요청으로 받는다(2026-09-27 전체 25개).
 */
@Component
public class KdbBankScraper extends AbstractBankProductScraper {

    private static final String PRODUCT_LIST_API = "https://banking.kdb.co.kr/bp/BMDEWP01R00.jct";
    private static final int PAGE_ROW_COUNT = 200;

    private final ObjectMapper objectMapper;

    public KdbBankScraper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerCode() {
        return "0010030";
    }

    @Override
    public Set<String> allowedDomains() {
        return Set.of("kdb.co.kr");
    }

    @Override
    protected List<ProductCandidate> search(BrowserContext context, String productName) {
        return extractProductsFromApi(requestProducts(context));
    }

    // 요청 값은 목록 페이지가 보내는 값 그대로다(XPO_TGT_C·WEB_PRD_CLSF_C는 의미 미확인). 페이지당 개수만 늘렸다.
    String requestProducts(BrowserContext context) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("REQ_PAGE_NO", 1);
        header.put("PAGE_ROW_COUNT", PAGE_ROW_COUNT);
        header.put("NEXT_PAGE_YN", "S");
        header.put("NEXTPAGDTT", "P");
        header.put("GRID_NEXTKEY_ITR_CND", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("_SECT_PGM_INSTALL_C_", "0");
        payload.put("CTG_N1_SNO", List.of());
        payload.put("PRD_JIN_TGT_C", List.of());
        payload.put("XPO_TGT_C", List.of(Map.of("value", 10)));
        payload.put("WEB_PRD_CLSF_C", 10);
        payload.put("ORD", "");
        payload.put("WGD_NM", "");
        payload.put("HEADER_STD_WEB", header);

        String encodedPayload = URLEncoder.encode(
                objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8
        );
        APIResponse response = context.request().post(PRODUCT_LIST_API, RequestOptions.create()
                .setForm(FormData.create().set("_JSON_", encodedPayload))
                .setHeader("Accept", "application/json")
                .setHeader("Referer", "https://banking.kdb.co.kr/bp/BMDEWP01N00.act")
                .setTimeout(30_000));
        try {
            if (!response.ok()) {
                throw new IllegalStateException("KDB product list API returned HTTP " + response.status());
            }
            return response.text();
        } finally {
            response.dispose();
        }
    }

    List<ProductCandidate> extractProductsFromApi(String responseBody) {
        List<ProductCandidate> candidates = new ArrayList<>();
        JsonNode products = objectMapper.readTree(responseBody).path("GRID_LIST");
        if (!products.isArray()) {
            return candidates;
        }
        for (JsonNode product : products) {
            String name = cleanText(product.path("WGD_NM").asString(""));
            String code = cleanText(product.path("PRD_C").asString(""));
            if (!name.isBlank() && !code.isBlank()) {
                candidates.add(new ProductCandidate(name, detailUrl(code)));
            }
        }
        return dedupe(candidates);
    }

    private String detailUrl(String code) {
        return "https://banking.kdb.co.kr/bp/BMDEWP01N10.act?PRD_C=" + code + "#prd=" + code;
    }
}
