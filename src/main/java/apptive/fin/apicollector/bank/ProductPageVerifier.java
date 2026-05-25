package apptive.fin.apicollector.bank;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ProductPageVerifier {
    private static final List<String> PRODUCT_LABELS = List.of(
            "상품안내",
            "상품 안내",
            "상품요약",
            "상품특징",
            "가입대상",
            "가입기간",
            "계약기간",
            "가입금액",
            "저축금액",
            "적립금액",
            "가입방법",
            "거래방법",
            "우대금리",
            "우대이율",
            "우대조건",
            "예금자보호"
    );

    private static final Pattern RATE_PATTERN = Pattern.compile("(최고\\s*)?연\\s*[0-9]+(?:\\.[0-9]+)?\\s*%\\s*p?");

    public VerificationResult verify(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        String text = defaultString(page.title()) + " " + defaultString(page.text());
        int score = candidate.score();
        boolean keywordMatched = containsKeyword(text, candidate.keyword());
        boolean titleMatched = containsKeyword(text, candidate.title());
        boolean officialHost = isOfficialHost(candidate);

        if (officialHost) {
            score += 10;
        }
        if (titleMatched) {
            score += 45;
        }
        else if (keywordMatched) {
            score += 20;
        }
        long labelCount = PRODUCT_LABELS.stream().filter(text::contains).count();
        score += Math.min(20, (int) labelCount * 5);
        boolean rateMatched = RATE_PATTERN.matcher(text).find();
        if (rateMatched) {
            score += 15;
        }
        if (text.contains("예금자보호")) {
            score += 10;
        }
        if (isNoiseUrl(candidate.url())) {
            score -= 30;
        }
        if (candidate.url().toLowerCase().endsWith(".pdf")) {
            score -= 40;
        }
        if (page.text() == null || page.text().length() < 200) {
            score -= 100;
        }
        boolean saleStopped = hasStoppedProductName(text, candidate);
        if (saleStopped) {
            score -= 100;
        }
        if (candidate.source() == CandidateSource.MANUAL_SEED
                && officialHost
                && labelCount >= 3
                && rateMatched
                && !saleStopped) {
            score = Math.max(score, 70);
        }

        VerificationStatus status = status(score);
        return new VerificationResult(status, score, matchedLabels(text));
    }

    private boolean containsKeyword(String text, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return text.contains(keyword) || text.replaceAll("\\s+", "").contains(keyword.replaceAll("\\s+", ""));
    }

    private boolean hasStoppedProductName(String text, ProductCandidate candidate) {
        String compactText = text.replaceAll("\\s+", "");
        return hasStoppedMarkerNearName(compactText, candidate.title())
                || hasStoppedMarkerNearName(compactText, candidate.keyword());
    }

    private boolean hasStoppedMarkerNearName(String compactText, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String compactName = name.replaceAll("\\s+", "");
        int nameIndex = compactText.indexOf(compactName);
        if (nameIndex < 0) {
            return false;
        }
        int start = Math.max(0, nameIndex - 30);
        int end = Math.min(compactText.length(), nameIndex + compactName.length() + 30);
        String nearby = compactText.substring(start, end);
        return nearby.contains("판매중지") || nearby.contains("판매종료");
    }

    private boolean isOfficialHost(ProductCandidate candidate) {
        try {
            String host = URI.create(candidate.url()).getHost();
            return host != null && host.endsWith(candidate.bankCode().officialHost());
        }
        catch (Exception e) {
            return false;
        }
    }

    private boolean isNoiseUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("notice")
                || lower.contains("event")
                || lower.contains("news")
                || lower.contains("press")
                || lower.contains("board");
    }

    private VerificationStatus status(int score) {
        if (score >= 70) {
            return VerificationStatus.VERIFIED;
        }
        if (score >= 40) {
            return VerificationStatus.REVIEW_REQUIRED;
        }
        return VerificationStatus.REJECTED;
    }

    private String matchedLabels(String text) {
        return PRODUCT_LABELS.stream()
                .filter(text::contains)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
