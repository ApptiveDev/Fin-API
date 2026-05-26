package apptive.fin.apicollector.bank.keyword;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProductNameSimilarity {
    private static final int SIMILARITY_THRESHOLD = 55;
    private static final List<String> DISTINCTIVE_PRODUCT_TERMS = List.of(
            "청년도약계좌",
            "청년미래적금",
            "장병내일준비적금",
            "청년주택드림청약통장",
            "주택드림청약통장",
            "부산청년기쁨두배통장",
            "기쁨두배통장",
            "행복키움통장",
            "희망키움통장",
            "청년내일저축계좌",
            "내일저축계좌",
            "청년희망적금",
            "새희망홀씨",
            "햇살론유스"
    );

    private ProductNameSimilarity() {
    }

    public static boolean isSimilar(String left, String right) {
        if (hasConflictingDistinctiveTerms(left, right)) {
            return false;
        }
        return score(left, right) >= SIMILARITY_THRESHOLD;
    }

    public static int score(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return 0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 100;
        }
        if (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)) {
            int shorter = Math.min(normalizedLeft.length(), normalizedRight.length());
            int longer = Math.max(normalizedLeft.length(), normalizedRight.length());
            return Math.max(70, shorter * 100 / longer);
        }

        Set<String> leftBigrams = bigrams(normalizedLeft);
        Set<String> rightBigrams = bigrams(normalizedRight);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0;
        }

        long intersection = leftBigrams.stream()
                .filter(rightBigrams::contains)
                .count();
        return (int) (intersection * 200 / (leftBigrams.size() + rightBigrams.size()));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replace("(", "")
                .replace(")", "")
                .replaceAll("[^가-힣a-z0-9]", "")
                .replace("상품", "")
                .replace("지원", "")
                .trim();
    }

    private static boolean hasConflictingDistinctiveTerms(String left, String right) {
        Set<String> leftTerms = distinctiveTerms(left);
        Set<String> rightTerms = distinctiveTerms(right);
        return !leftTerms.isEmpty()
                && !rightTerms.isEmpty()
                && leftTerms.stream().noneMatch(rightTerms::contains);
    }

    private static Set<String> distinctiveTerms(String value) {
        String normalized = normalize(value);
        Set<String> result = new LinkedHashSet<>();
        for (String term : DISTINCTIVE_PRODUCT_TERMS) {
            String normalizedTerm = normalize(term);
            if (normalized.contains(normalizedTerm)) {
                result.add(normalizedTerm);
            }
        }
        return result;
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value.length() == 1) {
            result.add(value);
            return result;
        }
        for (int index = 0; index < value.length() - 1; index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }
}
