package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JbProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String BASE_URL = "https://www.jbbank.co.kr/";
    private static final String LEGACY_SAVING_LIST_URL = BASE_URL + "gdnc_szmy.act";
    private static final String PRODUCT_API_URL = BASE_URL + "EBCIB_GDSBS_M_R001.jct";
    private static final Pattern LOCATION_PATTERN = Pattern.compile("jb_location\\('([^']+)'\\)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ProductMallCategory> PRODUCT_MALL_CATEGORIES = List.of(
            new ProductMallCategory(BASE_URL + "NFF_FRDP_OPAC.act", "10"),
            new ProductMallCategory(BASE_URL + "NFF_FRDP_SZMY.act", "20"),
            new ProductMallCategory(BASE_URL + "NFF_FRDP_SMYR.act", "30"),
            new ProductMallCategory(BASE_URL + "NFF_FRDP_HSBC.act", "40"),
            new ProductMallCategory(BASE_URL + "NFF_FRDP_MRKT.act", "50")
    );

    public JbProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.JB;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        List<SearchRequest> requests = new ArrayList<>();
        requests.add(SearchRequest.get(LEGACY_SAVING_LIST_URL));
        for (ProductMallCategory category : PRODUCT_MALL_CATEGORIES) {
            requests.add(SearchRequest.get(category.url()));
            requests.add(SearchRequest.post(
                    PRODUCT_API_URL,
                    Map.of("_JSON_", productMallPayload(keyword, category)),
                    Map.of(
                            "Accept", "application/json, text/javascript, */*; q=0.01",
                            "Referer", category.url(),
                            "X-Requested-With", "XMLHttpRequest"
                    )
            ));
        }
        return requests;
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        List<ProductCandidate> candidates = new ArrayList<>();
        candidates.addAll(extractLegacySavingLinks(keyword, page));
        candidates.addAll(extractProductMallJson(keyword, page));
        return candidates;
    }

    private List<ProductCandidate> extractLegacySavingLinks(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element item : document.select("dt:has(a[onclick*=jb_location])")) {
            Element titleElement = item.selectFirst("strong");
            Element link = item.selectFirst("a[onclick*=jb_location]");
            if (titleElement == null || link == null) {
                continue;
            }
            String title = titleElement.text().replaceAll("\\s+", " ").trim();
            Matcher matcher = LOCATION_PATTERN.matcher(link.attr("onclick"));
            if (title.isBlank() || !matcher.find() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(keyword, title, absoluteUrl(page.url(), matcher.group(1))));
        }
        return candidates;
    }

    private List<ProductCandidate> extractProductMallJson(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Optional<String> json = jsonText(page);
        if (json.isEmpty()) {
            return List.of();
        }

        List<ProductCandidate> candidates = new ArrayList<>();
        try {
            JsonNode records = OBJECT_MAPPER.readTree(json.get()).path("REC");
            if (!records.isArray()) {
                return List.of();
            }
            for (JsonNode record : records) {
                String title = firstText(
                        record,
                        "GDS_NM",
                        "GDS_NAME",
                        "GDS_TITL",
                        "BRND_GDS_NM",
                        "GDS_KORN_NM",
                        "PRD_NM",
                        "PROD_NM"
                );
                if (title == null || !matches(keyword, title)) {
                    continue;
                }
                candidates.add(candidate(keyword, title, productMallDetailUrl(record)));
            }
        }
        catch (Exception e) {
            return List.of();
        }
        return candidates;
    }

    private Optional<String> jsonText(StaticHtmlClient.FetchedPage page) {
        String text = page.text() == null ? "" : page.text().trim();
        if (text.startsWith("{")) {
            return Optional.of(text);
        }

        String html = page.html() == null ? "" : page.html().trim();
        int start = html.indexOf("{\"REC\"");
        if (start < 0) {
            return Optional.empty();
        }
        int end = html.lastIndexOf('}');
        if (end <= start) {
            return Optional.empty();
        }
        return Optional.of(html.substring(start, end + 1));
    }

    private String productMallDetailUrl(JsonNode record) {
        String explicitUrl = firstText(
                record,
                "LINK_URL",
                "DETAIL_URL",
                "DTL_URL",
                "GDS_DTL_URL",
                "GDS_URL",
                "MENU_URL",
                "URL"
        );
        if (explicitUrl != null) {
            return absoluteUrl(BASE_URL, explicitUrl);
        }

        String productCode = firstText(record, "GDS_WHOL_CD", "GDS_CD", "GDS_NO", "GDS_ID");
        String categoryUrl = categoryUrl(record);
        if (productCode == null) {
            return categoryUrl;
        }
        return categoryUrl + "#product-" + productCode;
    }

    private String categoryUrl(JsonNode record) {
        return switch (String.valueOf(firstText(record, "MCCD", "MCD"))) {
            case "10" -> BASE_URL + "NFF_FRDP_OPAC.act";
            case "30" -> BASE_URL + "NFF_FRDP_SMYR.act";
            case "40" -> BASE_URL + "NFF_FRDP_HSBC.act";
            case "50" -> BASE_URL + "NFF_FRDP_MRKT.act";
            default -> BASE_URL + "NFF_FRDP_SZMY.act";
        };
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.asString(null);
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private String productMallPayload(ProductSearchKeyword keyword, ProductMallCategory category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("PT_HEADER", Map.of());
        payload.put("LCCD", "10");
        payload.put("MCCD", category.middleCode());
        payload.put("SCCD", "10");
        payload.put("GDS_STCD", "20");
        payload.put("INBN_NEW_YN", "");
        payload.put("SMPH_NEW_YN", "");
        payload.put("BOB_NEW_YN", "");
        payload.put("MBL_NEW_YN", "");
        payload.put("GDS_NM", keyword.value());
        payload.put("INBN_MRK_YN", "Y");
        payload.put("ORDER_BROF_NM", "LNUP_SER");
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to build JB product mall payload", e);
        }
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "JB 장병내일준비적금",
                        "https://www.jbbank.co.kr/GDNC_NTAR_PRPR.act",
                        "장병내일준비적금",
                        "JB장병내일준비적금"
                ),
                new KnownProduct(
                        "행복키움 통장",
                        "https://www.jbbank.co.kr/gdnc_spnd_comp_detail01.act",
                        "희망드림 자산형성저축",
                        "행복키움통장"
                )
        );
    }

    private record ProductMallCategory(String url, String middleCode) {
    }
}
