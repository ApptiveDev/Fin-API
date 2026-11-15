package apptive.fin.apicollector.youthpolicy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 온통청년 정책이 B1 캘린더 1계층 정책상품(manual-products.json)과 같은 사업인지 이름으로 판정한다.
 * 2계층은 Y-Fin 상품 DB에 없는 정책만 보여주므로(B1 PRD), 같은 사업이면 youth_policy에 넣지 않는다.
 *
 * <p>온통청년 이름은 상품명에 연도·시군·"지원사업"을 덧붙이거나 괄호 안에 상품명을 적는다
 * (예: "자산형성지원사업(청년내일저축계좌)", "청년근로자 사랑채움사업"). 그래서 상품명을 띄어쓰기로 나눈
 * 단어가 모두 정책명(공백·기호 제거)에 들어 있으면 같은 사업으로 본다. 2026-11 측정에서 겹치는 51건 중
 * 50건을 잡고 오탐이 없었다. 은행 상품에 쓰는 ProductNameSimilarity는 괄호 안을 지워서 48건이었다.
 * 지역이 이름에 없고 기관에만 있는 경우(대구 "청년희망적금")는 놓친다.
 */
@Component
public class PolicyProductMatcher {

    static final String RESOURCE = "/manual-products.json";

    private final List<PolicyProduct> products;

    @Autowired
    public PolicyProductMatcher(ObjectMapper objectMapper) {
        this(load(objectMapper));
    }

    public PolicyProductMatcher(List<PolicyProduct> products) {
        this.products = List.copyOf(products);
    }

    public Optional<PolicyProduct> match(String policyName) {
        String compactName = compact(policyName);
        return products.stream()
                .filter(product -> Arrays.stream(product.productName().split("\\s+"))
                        .allMatch(word -> compactName.contains(compact(word))))
                .findFirst();
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-zA-Z가-힣]", "").toLowerCase(Locale.ROOT);
    }

    static List<PolicyProduct> load(ObjectMapper objectMapper) {
        try (InputStream in = PolicyProductMatcher.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Policy products resource not found: " + RESOURCE);
            }
            List<PolicyProduct> products = new ArrayList<>();
            for (JsonNode product : objectMapper.readTree(in)) {
                products.add(new PolicyProduct(
                        product.get("productCode").asString(), product.get("productName").asString()));
            }
            return products;
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to load policy products from " + RESOURCE, e);
        }
    }

    public record PolicyProduct(String productCode, String productName) {
    }
}
