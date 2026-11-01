package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.policyround.ProductRounds;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 사람이 고치는 manual-product-rounds.json이 읽히고, 상품 코드가 manual-products.json과 맞는지 확인한다. */
class ManualProductRoundsFileTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 회차_파일은_형식이_맞고_정부상품_코드를_빠짐없이_가진다() throws Exception {
        List<ProductRounds> entries = SyncPolicyProductRoundTasklet.load(objectMapper);

        Set<String> roundCodes = new HashSet<>();
        entries.forEach(entry -> assertThat(roundCodes.add(entry.productCode())).as("중복 " + entry.productCode()).isTrue());
        assertThat(roundCodes).isEqualTo(manualProductCodes());
    }

    private Set<String> manualProductCodes() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/manual-products.json")) {
            Set<String> codes = new HashSet<>();
            for (JsonNode product : objectMapper.readTree(in)) {
                codes.add(product.get("productCode").asString());
            }
            return codes;
        }
    }
}
