package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KbankProductLinkDiscoverer extends SearchBackedProductLinkDiscoverer {
    private static final String HOME_URL = "https://www.kbanknow.com/";
    private static final String DETAIL_URL = "https://www.kbanknow.com/ib20/mnu/%s";
    private static final Pattern MENU_PRODUCT_PATTERN = Pattern.compile(
            "\\{\"CMN_CD_NM\":\"([^\"]+)\",[^}]*\"CMN_CD_ABRV_NM\":\"([A-Z0-9]+)\""
    );

    public KbankProductLinkDiscoverer(StaticHtmlClient htmlClient) {
        super(htmlClient);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.KBANK;
    }

    @Override
    protected List<SearchRequest> searchRequests(ProductSearchKeyword keyword) {
        return List.of(SearchRequest.get(HOME_URL));
    }

    @Override
    protected List<ProductCandidate> extract(ProductSearchKeyword keyword, StaticHtmlClient.FetchedPage page) {
        Matcher matcher = MENU_PRODUCT_PATTERN.matcher(page.html());
        List<ProductCandidate> candidates = new ArrayList<>();
        while (matcher.find()) {
            String title = matcher.group(1).replaceAll("\\s+", " ").trim();
            String menuId = matcher.group(2).trim();
            if (title.isBlank() || !matches(keyword, title)) {
                continue;
            }
            candidates.add(candidate(keyword, title, DETAIL_URL.formatted(menuId)));
        }
        return candidates;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct("케이뱅크 코드K 자유적금", "https://www.kbanknow.com/ib20/mnu/FPMDPT080000", "코드K 자유적금", "자유적금"),
                new KnownProduct("케이뱅크 코드K 정기예금", "https://www.kbanknow.com/ib20/mnu/FPMDPT070000", "코드K 정기예금")
        );
    }
}
