package apptive.fin.apicollector.client.ontong;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.client.OntongYouthClient;
import apptive.fin.apicollector.config.CollectorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OntongYouthClientTest {

    private static final int PAGE_SIZE = 2;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OntongYouthClient client = new OntongYouthClient(properties(), builder.build());

    @Test
    void 마지막_페이지까지_모은다() {
        server.expect(queryParam("pageNum", "1")).andRespond(page("A", "B"));
        server.expect(queryParam("pageNum", "2")).andRespond(page("C"));

        List<JsonNode> policies = client.fetchAll();

        server.verify();
        assertThat(policies).extracting(policy -> policy.path("plcyNo").asString()).containsExactly("A", "B", "C");
    }

    // 온통청년은 가끔 JSON 대신 HTML 오류 페이지를 돌려준다.
    @Test
    void HTML_응답이면_같은_페이지를_다시_요청한다() {
        server.expect(queryParam("pageNum", "1")).andRespond(html());
        server.expect(queryParam("pageNum", "1")).andRespond(page("A"));

        assertThat(client.fetchAll()).hasSize(1);
        server.verify();
    }

    @Test
    void 세_번_모두_실패하면_중단한다() {
        server.expect(times(3), queryParam("pageNum", "1")).andRespond(html());

        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret-key");
        server.verify();
    }

    private static ResponseCreator page(String... policyNos) {
        StringBuilder items = new StringBuilder();
        for (String policyNo : policyNos) {
            if (!items.isEmpty()) {
                items.append(',');
            }
            items.append("{\"plcyNo\":\"").append(policyNo).append("\"}");
        }
        return withSuccess("{\"result\":{\"youthPolicyList\":[" + items + "]}}", MediaType.APPLICATION_JSON);
    }

    private static ResponseCreator html() {
        return withSuccess("<!DOCTYPE html><html></html>", MediaType.TEXT_HTML);
    }

    private static CollectorProperties properties() {
        return new CollectorProperties(
                true, Source.CALENDAR, Mode.SYNC, 1, 100, 1,
                new CollectorProperties.OntongYouth("http://localhost", "secret-key", PAGE_SIZE),
                null, null);
    }
}
