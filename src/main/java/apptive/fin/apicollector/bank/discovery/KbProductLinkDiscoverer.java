package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbProductLinkDiscoverer implements ProductLinkDiscoverer {
    private static final String SEARCH_URL = "https://obank.kbstar.com/quics?page=C016613&cc=b061496:b061496";
    private static final List<String> DEPOSIT_KIND_CODES = List.of("00027", "00169", "00000");
    private static final Pattern DETAIL_PATTERN = Pattern.compile("dtlDeposit\\('([^']+)'\\s*,\\s*'([^']+)'");

    private final StaticHtmlClient htmlClient;

    @Override
    public BankCode bankCode() {
        return BankCode.KB;
    }

    @Override
    public List<ProductCandidate> discover(ProductSearchKeyword keyword) {
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        for (String depositKindCode : DEPOSIT_KIND_CODES) {
            try {
                StaticHtmlClient.FetchedPage page = htmlClient.post(SEARCH_URL, searchForm(keyword.value(), depositKindCode));
                for (ProductCandidate candidate : extract(keyword, page.html())) {
                    candidates.putIfAbsent(candidate.url(), candidate);
                }
            }
            catch (Exception e) {
                log.warn("KB product search failed. keyword={}, depositKind={}", keyword.value(), depositKindCode, e);
            }
        }
        return new ArrayList<>(candidates.values());
    }

    private Map<String, String> searchForm(String keyword, String depositKindCode) {
        return Map.ofEntries(
                Map.entry("page", "C016613"),
                Map.entry("cc", "b061496:b061496"),
                Map.entry("브랜드상품명", keyword),
                Map.entry("예금종류", depositKindCode),
                Map.entry("정렬조건", "B1"),
                Map.entry("현재페이지", "1"),
                Map.entry("노드코드", "00007"),
                Map.entry("개편", "1"),
                Map.entry("상품유형코드", "00000"),
                Map.entry("가입방법코드", "00"),
                Map.entry("가입기간", "")
        );
    }

    private List<ProductCandidate> extract(ProductSearchKeyword keyword, String html) {
        Document document = Jsoup.parse(html);
        List<ProductCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a[onclick*=dtlDeposit]")) {
            Matcher matcher = DETAIL_PATTERN.matcher(link.attr("onclick"));
            if (!matcher.find()) {
                continue;
            }
            String productCode = matcher.group(1);
            String isNew = matcher.group(2);
            String title = link.text();
            if (!matches(keyword.value(), title)) {
                continue;
            }
            String url = "https://obank.kbstar.com/quics?cc=b061496%3Ab061645&page=C016613"
                    + "&isNew=" + isNew
                    + "&prcode=" + productCode;
            candidates.add(new ProductCandidate(bankCode(), keyword.value(), title, url, CandidateSource.BANK_SEARCH, 0));
        }
        return candidates;
    }

    private boolean matches(String keyword, String title) {
        String compactKeyword = compact(keyword);
        String compactTitle = compact(title);
        return compactTitle.contains(compactKeyword) || compactKeyword.contains(compactTitle);
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
