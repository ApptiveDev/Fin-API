package apptive.fin.apicollector.client.kfb;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KfbClientTest {

    private static final Charset EUC_KR = Charset.forName("MS949");

    @Test
    void searchesAllBanksInOneRequestAndDecodesEucKr() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KfbClient client = new KfbClient(builder.build(), new KfbFreeDepositParser());

        server.expect(requestTo("http://localhost/compare/free_deposit.php"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(eucKr("""
                        <input type="checkbox" name="BankSelect" id="Bankselect1" value="0010001"/><label for="Bankselect1">우리은행</label>
                        <input type="checkbox" name="BankSelect" id="Bankselect2" value="0015130"/><label for="Bankselect2">카카오뱅크</label>
                        """));
        server.expect(requestTo("http://localhost/compare/free_deposit_search_result_sort.php"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("BankValue", "0010001|0015130")))
                .andRespond(eucKr(resultTable(row("우리은행", "WON통장") + row("카카오뱅크", "세이프박스"))));

        List<KfbRawProduct> products = client.fetchAll();

        server.verify();
        assertThat(products)
                .extracting(KfbRawProduct::bankCode, KfbRawProduct::bankName, KfbRawProduct::productName)
                .containsExactly(
                        tuple("0010001", "우리은행", "WON통장"),
                        tuple("0015130", "카카오뱅크", "세이프박스")
                );
    }

    private static DefaultResponseCreator eucKr(String html) {
        return withSuccess(html.getBytes(EUC_KR), new MediaType("text", "html", EUC_KR));
    }

    private static String resultTable(String rows) {
        return "<table class=\"resultList_ty02\"><tbody>" + rows + "</tbody></table>";
    }

    private static String row(String bankName, String productName) {
        return """
                <tr><td>%s&nbsp;</td><td class="tl"><a href="https://bank.example/p">%s</a></td>
                <td>0.10</td><td>2.00</td><td>월지급</td><td>보기</td></tr>
                """.formatted(bankName, productName);
    }
}
