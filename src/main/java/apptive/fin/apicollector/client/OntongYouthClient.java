package apptive.fin.apicollector.client;

import apptive.fin.apicollector.config.CollectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class OntongYouthClient {

    private static final int MAX_ATTEMPTS_PER_PAGE = 3;

    private final CollectorProperties properties;
    private final RestClient ontongYouthRestClient;

    public OntongYouthClient(
            CollectorProperties properties,
            @Qualifier("ontongYouthRestClient") RestClient ontongYouthRestClient
    ) {
        this.properties = properties;
        this.ontongYouthRestClient = ontongYouthRestClient;
    }

    public List<JsonNode> fetchAll() {
        List<JsonNode> result = new ArrayList<>();

        int page = 1;
        int pageSize = properties.ontongYouth().pageSize();

        while (true) {
            JsonNode response = fetchPage(ontongYouthRestClient, page, pageSize);
            List<JsonNode> items = extractItems(response);

            if (items.isEmpty()) {
                break;
            }

            result.addAll(items);

            if (items.size() < pageSize) {
                break;
            }

            page++;
        }

        return result;
    }

    // 온통청년은 가끔 JSON 대신 HTML 오류 페이지를 돌려준다(2026-11 확인, 바로 다시 요청하면 정상).
    // I/O 오류 메시지에는 API 키가 든 요청 URL이 들어가므로 예외 종류만 남긴다.
    private JsonNode fetchPage(RestClient restClient, int page, int pageSize) {
        String lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_PAGE; attempt++) {
            try {
                return requestPage(restClient, page, pageSize);
            }
            catch (RestClientException e) {
                lastFailure = e.getClass().getSimpleName();
                log.warn("OntongYouthClient: page {} attempt {}/{} failed. {}", page, attempt, MAX_ATTEMPTS_PER_PAGE, lastFailure);
            }
        }
        throw new IllegalStateException(
                "Ontong youth page " + page + " failed " + MAX_ATTEMPTS_PER_PAGE + " times. last=" + lastFailure);
    }

    private JsonNode requestPage(RestClient restClient, int page, int pageSize) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/go/ythip/getPlcy")
                        .queryParam("apiKeyNm", properties.ontongYouth().apiKey())
                        .queryParam("pageNum", page)
                        .queryParam("pageSize", pageSize)
                        .queryParam("rtnType", "json")
                        .build()
                )
                .retrieve()
                .body(JsonNode.class);
    }

    private List<JsonNode> extractItems(JsonNode response) {
        JsonNode items = response
                .path("result")
                .path("youthPolicyList");

        if (!items.isArray()) {
            return List.of();
        }

        List<JsonNode> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }
}
