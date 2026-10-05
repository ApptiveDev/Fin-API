package apptive.fin.apicollector.llm.gemini;

import apptive.fin.apicollector.Source;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiEnrichmentSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiEnrichmentSchema schema = new GeminiEnrichmentSchema(objectMapper);

    private List<String> values(JsonNode enumArray) {
        List<String> result = new ArrayList<>();
        enumArray.forEach(node -> result.add(node.asString()));
        return result;
    }

    @Test
    void build_declaresAllTopLevelKeysAsRequired() {
        ObjectNode built = schema.build(Source.FSS);

        assertThat(values(built.get("required")))
                .hasSize(21)
                .contains("summaryContent", "keywords", "minDepositAmount", "requiredKeywords", "preferentialRates", "militaryMaxAge");
    }

    // 파킹 전용 필드는 KFB 스키마에만 있다. FSS 스키마가 바뀌면 FSS LLM 결과를 모두 다시 받아야 한다.
    @Test
    void build_addsMaxRateApplicableRangeOnlyForKfb() {
        assertThat(values(schema.build(Source.FSS).get("required")))
                .doesNotContain("maxRateApplicableMinAmount", "maxRateApplicableMaxAmount");
        assertThat(values(schema.build(Source.KFB).get("required")))
                .hasSize(23)
                .contains("maxRateApplicableMinAmount", "maxRateApplicableMaxAmount");
    }

    @Test
    void build_keywordsEnumExcludesTermKeywords() {
        JsonNode keywordsEnum = schema.build(Source.FSS)
                .get("properties").get("keywords").get("items").get("enum");

        assertThat(values(keywordsEnum)).isNotEmpty().noneMatch(v -> v.startsWith("TERM_"));
    }

    @Test
    void build_requiredKeywordsEnumContainsOnlyStatus() {
        JsonNode enumValues = schema.build(Source.FSS)
                .get("properties").get("requiredKeywords")
                .get("items").get("properties").get("keywordCode").get("enum");

        assertThat(values(enumValues)).isNotEmpty().allMatch(v -> v.startsWith("STATUS_"));
    }

    @Test
    void build_preferentialRatesEnumContainsOnlyBank() {
        JsonNode enumValues = schema.build(Source.FSS)
                .get("properties").get("preferentialRates")
                .get("items").get("properties").get("keywordCode").get("enum");

        assertThat(values(enumValues)).isNotEmpty().allMatch(v -> v.startsWith("BANK_"));
    }
}
