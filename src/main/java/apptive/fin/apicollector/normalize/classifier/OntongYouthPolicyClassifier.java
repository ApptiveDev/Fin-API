package apptive.fin.apicollector.normalize.classifier;

import apptive.fin.apicollector.normalize.ProductClassification;
import apptive.fin.apicollector.normalize.normalizer.AbstractProductNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Component
public class OntongYouthPolicyClassifier extends AbstractProductNormalizer {

    private static final String FINANCE_CATEGORY = "취약계층 및 금융지원";
    private static final String LOAN_KEYWORD = "대출";
    private static final List<String> LOAN_METHOD_CODES = List.of("42003", "42007");

    private static final Map<String, Integer> FINANCIAL_KEY_MAP = Map.ofEntries(
            Map.entry("통장", 60),
            Map.entry("적금", 50),
            Map.entry("예금", 50),
            Map.entry("저축", 3),
            Map.entry("자산형성", 4),
            Map.entry("계좌", 4),
            Map.entry("내일저축", 70),
            Map.entry("미래적금", 70),
            Map.entry("청년도약계좌", 70),
            Map.entry("내일채움", 50),
            Map.entry("주택드림", 60),
            Map.entry("청약", 3),
            Map.entry("비과세", 4),
            Map.entry("기여금", 3),
            Map.entry("공제", 4),
            Map.entry("적립", 2),
            Map.entry("납입", 2),
            Map.entry("매칭", 1),
            Map.entry("분양", 1)
    );

    public ProductClassification classify(JsonNode policy) {
        if (!isFinanceCandidate(policy)) {
            return ProductClassification.EXCLUDED;
        }

        if (isLoan(policy)) {
            return ProductClassification.LOAN_EXCLUDED;
        }

        if (financeScore(policy) >= 10) {
            return ProductClassification.FINANCIAL_PRODUCT;
        }

        return ProductClassification.UNCLASSIFIED;
    }

    private boolean isFinanceCandidate(JsonNode policy) {
        String category = text(policy, "mclsfNm");
        return FINANCE_CATEGORY.equals(category);
    }

    private boolean isLoan(JsonNode policy) {
        String keywords = text(policy, "plcyKywdNm");
        String methodCode = text(policy, "plcyPvsnMthdCd");

        return contains(keywords, LOAN_KEYWORD)
                || LOAN_METHOD_CODES.stream().anyMatch(code -> hasCode(methodCode, code));
    }

    private int financeScore(JsonNode policy) {
        String value = String.join(" ",
                defaultString(text(policy, "plcyNm")),
                defaultString(text(policy, "plcySprtCn")),
                defaultString(text(policy, "mclsfNm")),
                defaultString(text(policy, "plcyKywdNm"))
        );

        int keywordScore = FINANCIAL_KEY_MAP.entrySet().stream()
                .map(entry -> StringUtils.countOccurrencesOf(value, entry.getKey()) * entry.getValue())
                .reduce(Integer::sum)
                .orElse(0);

        if (hasMonthlyAmount(value)) {
            return Math.max(keywordScore, 6);
        }

        return keywordScore;
    }

    private boolean hasMonthlyAmount(String value) {
        return value.matches(".*\\d+.*") && value.contains("만원");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean hasCode(String rawCode, String targetCode) {
        return rawCode != null && rawCode.replaceFirst("^0+", "").equals(targetCode);
    }

    private boolean contains(String value, String token) {
        return value != null && value.contains(token);
    }
}
