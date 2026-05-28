package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.CandidateSource;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import apptive.fin.apicollector.bank.keyword.ProductNameSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NhProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    private static final String LIST_PAGE_URL = "https://smartmarket.nonghyup.com/servlet/BFDCW1011R.view";
    private static final String LIST_FRAGMENT_URL = "https://smartmarket.nonghyup.com/servlet/BFDCW1016R.frag";
    private static final String DETAIL_URL = "https://smartmarket.nonghyup.com/servlet/BFDCW1021R.view"
            + "?psnFncWrsC=%s&serviceId=BFDCW1011R";
    private static final int COUNT_PER_PAGE = 9;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("window\\[\"TOKEN\"\\]\\s*=\\s*'([^']+)'");
    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile("lfGetDp\\('([^']+)'\\)");

    private final StaticHtmlClient htmlClient;
    private volatile List<SmartMarketProduct> cachedProducts;

    public NhProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        this.htmlClient = htmlClient;
    }

    @Override
    public BankCode bankCode() {
        return BankCode.NH;
    }

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        for (SmartMarketProduct product : smartMarketProducts()) {
            if (!ProductNameSimilarity.isSimilar(keyword.canonicalName(), product.title())) {
                continue;
            }
            String url = DETAIL_URL.formatted(product.code());
            int score = ProductNameSimilarity.score(keyword.canonicalName(), product.title());
            candidates.putIfAbsent(
                    url,
                    new ProductCandidate(bankCode(), keyword.canonicalName(), product.title(), url, CandidateSource.BANK_SEARCH, score)
            );
        }

        for (ProductCandidate candidate : super.discover(keyword)) {
            candidates.putIfAbsent(candidate.url(), candidate);
        }
        return new ArrayList<>(candidates.values());
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "NH청년도약계좌",
                        "https://nhlink.nonghyup.com/content/nhbank/html/sf/mk/dt/dt10001241_02.html",
                        "청년도약계좌"
                )
        );
    }

    private List<SmartMarketProduct> smartMarketProducts() {
        List<SmartMarketProduct> products = cachedProducts;
        if (products != null) {
            return products;
        }
        synchronized (this) {
            if (cachedProducts == null) {
                cachedProducts = fetchSmartMarketProducts();
            }
            return cachedProducts;
        }
    }

    private List<SmartMarketProduct> fetchSmartMarketProducts() {
        try {
            StaticHtmlClient.FetchedPageWithCookies entry = htmlClient.fetchWithResponseCookies(LIST_PAGE_URL);
            String token = token(entry.page().html()).orElse("");
            Map<String, String> headers = headers(token);
            Map<String, SmartMarketProduct> products = new LinkedHashMap<>();

            StaticHtmlClient.FetchedPage firstPage = fetchListPage(1, headers, entry.cookies());
            for (SmartMarketProduct product : extractProducts(firstPage)) {
                products.putIfAbsent(product.code(), product);
            }

            int totalCount = totalCount(firstPage).orElse(products.size());
            int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / COUNT_PER_PAGE));
            for (int page = 2; page <= totalPages; page++) {
                StaticHtmlClient.FetchedPage pageResult = fetchListPage(page, headers, entry.cookies());
                for (SmartMarketProduct product : extractProducts(pageResult)) {
                    products.putIfAbsent(product.code(), product);
                }
            }

            log.info("NH smart market products fetched. count={}", products.size());
            return new ArrayList<>(products.values());
        }
        catch (Exception e) {
            log.warn("NH smart market product list fetch failed.", e);
            return List.of();
        }
    }

    private StaticHtmlClient.FetchedPage fetchListPage(
            int page,
            Map<String, String> headers,
            Map<String, String> cookies
    ) {
        return htmlClient.post(LIST_FRAGMENT_URL, listForm(page), headers, cookies);
    }

    List<SmartMarketProduct> extractProducts(StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<SmartMarketProduct> products = new ArrayList<>();
        for (Element link : document.select("a.sbj[onclick*=lfGetDp]")) {
            Matcher matcher = PRODUCT_CODE_PATTERN.matcher(link.attr("onclick"));
            String title = link.text().replaceAll("\\s+", " ").trim();
            if (!matcher.find() || title.isBlank()) {
                continue;
            }
            products.add(new SmartMarketProduct(matcher.group(1), title));
        }
        return products;
    }

    private Optional<Integer> totalCount(StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        Element count = document.selectFirst("#list_count");
        if (count == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(count.text().replaceAll("[^0-9]", "")));
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<String> token(String html) {
        Matcher matcher = TOKEN_PATTERN.matcher(html == null ? "" : html);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Map<String, String> headers(String token) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", LIST_PAGE_URL);
        headers.put("SESSION_TOKEN", "null");
        headers.put("SF_NAAC_DS_DTLC", "1");
        if (!token.isBlank()) {
            headers.put("TOKEN", token);
        }
        return headers;
    }

    private Map<String, String> listForm(int page) {
        int startIndex = ((page - 1) * COUNT_PER_PAGE) + 1;
        int endIndex = page * COUNT_PER_PAGE;
        return Map.ofEntries(
                Map.entry("inq_bas_dt", ""),
                Map.entry("selYn", "1"),
                Map.entry("sortOrder", "0"),
                Map.entry("cntPerPage", String.valueOf(COUNT_PER_PAGE)),
                Map.entry("currentPage", String.valueOf(page)),
                Map.entry("startIndex", String.valueOf(startIndex)),
                Map.entry("endIndex", String.valueOf(endIndex)),
                Map.entry("serviceId", "BFDCW1011R")
        );
    }

    record SmartMarketProduct(
            String code,
            String title
    ) {
    }
}
