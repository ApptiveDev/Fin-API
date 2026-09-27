package apptive.fin.apicollector.client.kfb;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 은행연합회 소비자포털 입출금자유예금 비교공시 수집.
 *
 * <p>목록은 검색 페이지의 JS가 부르는 결과 조각(HTML)을 그대로 받는다. 은행코드를 "|"로 이어 보내면
 * 전체 은행이 한 응답으로 온다. 응답은 EUC-KR이며, 확장 한글까지 안전하게 MS949로 디코딩한다.
 */
@Component
public class KfbClient {

    private static final String SEARCH_PAGE_PATH = "/compare/free_deposit.php";
    private static final String SEARCH_RESULT_PATH = "/compare/free_deposit_search_result_sort.php";
    private static final Charset CHARSET = Charset.forName("MS949");

    private final RestClient kfbRestClient;
    private final KfbFreeDepositParser parser;

    public KfbClient(
            @Qualifier("kfbRestClient") RestClient kfbRestClient,
            KfbFreeDepositParser parser
    ) {
        this.kfbRestClient = kfbRestClient;
        this.parser = parser;
    }

    public List<KfbRawProduct> fetchAll() {
        List<KfbBank> banks = parser.parseBanks(searchPage());
        String bankValue = banks.stream().map(KfbBank::code).collect(Collectors.joining("|"));
        return parser.parseProducts(searchResult(bankValue), banks);
    }

    private String searchPage() {
        return decode(kfbRestClient.get()
                .uri(SEARCH_PAGE_PATH)
                .retrieve()
                .body(byte[].class));
    }

    // 검색 페이지의 FP_FreeDepositSearch_sort()가 보내는 값 그대로. 은행 외 필터는 모두 "전체"다.
    private String searchResult(String bankValue) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("InterestType", "");
        form.add("BankValue", bankValue);
        form.add("InterestMonth", "BANK_ORDER");
        form.add("OrderByType", "ASC");
        form.add("JOIN_METHOD", "");
        form.add("SortType", "");

        return decode(kfbRestClient.post()
                .uri(SEARCH_RESULT_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(byte[].class));
    }

    private static String decode(byte[] body) {
        return body == null ? "" : new String(body, CHARSET);
    }
}
