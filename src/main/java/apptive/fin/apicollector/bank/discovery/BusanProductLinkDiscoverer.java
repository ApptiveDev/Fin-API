package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class BusanProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String SEARCH_URL = "https://www.busanbank.co.kr/ib20/mnu/FPMDPO012009001";
    private static final String DETAIL_URL = "https://www.busanbank.co.kr/ib20/mnu/FPMDPO012001002"
            + "?FPCD=%s&FP_HLV_DVCD=%s&TIT_NM=%%EC%%A0%%84%%EC%%B2%%B4%%EC%%83%%81%%ED%%92%%88"
            + "&MENU_ID=FPMDPO012009001";
    private static final Pattern PAGE_PATTERN = Pattern.compile("ibsGoPage\\((\\d+)\\)");

    public BusanProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.BUSAN;
    }

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        try {
            StaticHtmlClient.FetchedPage firstPage = htmlClient.fetch(SEARCH_URL);
            for (ProductCandidate candidate : extract(keyword, firstPage)) {
                candidates.putIfAbsent(candidate.url(), candidate);
            }

            Optional<String> token = requestToken(firstPage.html());
            if (token.isPresent()) {
                int maxPage = maxPage(firstPage.html());
                for (int page = 2; page <= maxPage; page++) {
                    StaticHtmlClient.FetchedPage pageResult = htmlClient.post(SEARCH_URL, pageForm(token.get(), page));
                    for (ProductCandidate candidate : extract(keyword, pageResult)) {
                        candidates.putIfAbsent(candidate.url(), candidate);
                    }
                }
            }
        }
        catch (Exception e) {
            log.warn("Busan product paged search failed. keyword={}", keyword.value(), e);
        }

        for (ProductCandidate candidate : knownCandidates(keyword)) {
            candidates.putIfAbsent(candidate.url(), candidate);
        }
        return new ArrayList<>(candidates.values());
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get(SEARCH_URL));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.url());
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a.FPCD_DTL[FPCD]")) {
            String title = link.text().replaceAll("\\s+", " ").trim();
            String productCode = link.attr("FPCD").trim();
            String highLevelCode = firstNonBlank(link.attr("FP_HLV_DVCD"), "00101");
            if (title.isBlank() || productCode.isBlank() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(keyword, title, DETAIL_URL.formatted(productCode, highLevelCode)));
        }
        return candidates;
    }

    private Optional<String> requestToken(String html) {
        Document document = Jsoup.parse(html, SEARCH_URL);
        Element token = document.selectFirst("form#form1 input[name=REQUEST_TOKEN_KEY]");
        if (token == null || token.attr("value").isBlank()) {
            return Optional.empty();
        }
        return Optional.of(token.attr("value"));
    }

    private int maxPage(String html) {
        Matcher matcher = PAGE_PATTERN.matcher(html == null ? "" : html);
        int max = 1;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private Map<String, String> pageForm(String token, int page) {
        return Map.ofEntries(
                Map.entry("action_type", "wgt"),
                Map.entry("ib20_action", "/ib20/wgt/FPMPDT012INQV1AM"),
                Map.entry("ib20_cur_mnu", "FPMDPO012009001"),
                Map.entry("ib20_cur_wgt", "FPMPDT012INQV1AM"),
                Map.entry("ib20_change_wgt", ""),
                Map.entry("REQUEST_TOKEN_KEY", token),
                Map.entry("ibs_current_page", String.valueOf(page)),
                Map.entry("MKPD_LRG_CLACD", "00"),
                Map.entry("PDT_LNUP_CD", "00"),
                Map.entry("QTY_CHO", "12"),
                Map.entry("LIST_DVCD", "L"),
                Map.entry("CMP_BOX_LIST", ""),
                Map.entry("IS_HIDE", "Y"),
                Map.entry("TIT_NM", "전체상품"),
                Map.entry("PDT_INQ_DV", "1"),
                Map.entry("INQ_CNTN", ""),
                Map.entry("SSP_PRSNL", ""),
                Map.entry("SSP_EPCO", ""),
                Map.entry("SSP_PRBZ", ""),
                Map.entry("ITN_NEW_PSBLYN", ""),
                Map.entry("PRSNL_MBL_NEW_PSBLYN", ""),
                Map.entry("EPCO_MBL_NEW_PSBLYN", ""),
                Map.entry("SLBR_NEW_PSBLYN", ""),
                Map.entry("SSP_NTAXN_DVCD", ""),
                Map.entry("SSP_TX_PR_DVCD", ""),
                Map.entry("SSP_INCDE_DVCD", ""),
                Map.entry("b_page_id", "")
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "부산은행 청년도약계좌",
                        "https://www.busanbank.co.kr/ib20/mnu/FPMDPO012001002?FPCD=0010100189&FP_HLV_DVCD=00101&TIT_NM=%EB%AA%A9%EB%8F%88%EB%A7%8C%EB%93%A4%EA%B8%B0&FP_LRG_CLACD=001010102&FP_MD_CLACD=000000000&MENU_ID=FPMDPO012002001",
                        "청년도약계좌"
                )
        );
    }
}
