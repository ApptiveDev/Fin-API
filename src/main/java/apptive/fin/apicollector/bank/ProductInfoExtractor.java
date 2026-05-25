package apptive.fin.apicollector.bank;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductInfoExtractor {
    private static final List<String> KNOWN_LABELS = List.of(
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
            "예금자보호",
            "보호한도",
            "기본금리",
            "최고금리"
    );

    private static final Pattern RATE_PATTERN = Pattern.compile("(최고\\s*)?연\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*p?");

    public BankProductInfo extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        String text = page.text().replaceAll("\\s+", " ").trim();
        Map<String, String> tableValues = tableValues(page.html());
        List<RateMatch> rates = rates(text);
        RateMatch maxRate = rates.stream()
                .max(Comparator.comparing(RateMatch::rate))
                .orElse(null);

        return new BankProductInfo(
                candidate.bankCode(),
                firstNonBlank(candidate.title(), page.title(), candidate.keyword()),
                productType(text),
                candidate.url(),
                value(tableValues, text, List.of("가입대상")),
                value(tableValues, text, List.of("가입기간", "계약기간")),
                value(tableValues, text, List.of("가입금액", "저축금액", "적립금액", "월저축금")),
                value(tableValues, text, List.of("가입방법", "거래방법", "가입채널")),
                rates.isEmpty() ? null : rates.getFirst().rate(),
                maxRate == null ? null : maxRate.rate(),
                rates.isEmpty() ? null : rates.getFirst().text(),
                maxRate == null ? null : maxRate.text(),
                value(tableValues, text, List.of("우대금리", "우대이율", "우대조건")),
                value(tableValues, text, List.of("예금자보호", "예금자보호여부", "보호한도", "보호금융상품")),
                page.sourceHash(),
                Instant.now()
        );
    }

    private String productType(String text) {
        if (text.contains("대출")) {
            return "LOAN";
        }
        if (text.contains("청약")) {
            return "SUBSCRIPTION";
        }
        if (text.contains("예금")) {
            return "DEPOSIT";
        }
        if (text.contains("적금") || text.contains("저축")) {
            return "SAVING";
        }
        return "POLICY_FINANCE";
    }

    private String value(Map<String, String> tableValues, String text, List<String> labels) {
        for (String label : labels) {
            for (Map.Entry<String, String> entry : tableValues.entrySet()) {
                if (entry.getKey().contains(label) && !entry.getValue().isBlank()) {
                    return entry.getValue();
                }
            }
        }
        return extractByLabels(text, labels);
    }

    private Map<String, String> tableValues(String html) {
        Document document = Jsoup.parse(html);
        Map<String, String> values = new LinkedHashMap<>();

        for (Element row : document.select("tr")) {
            Element header = row.selectFirst("th");
            Element value = row.selectFirst("td");
            if (header != null && value != null) {
                put(values, header.text(), value.text());
            }
        }

        for (Element section : document.select("dl")) {
            List<Element> terms = section.select("dt");
            List<Element> descriptions = section.select("dd");
            int count = Math.min(terms.size(), descriptions.size());
            for (int index = 0; index < count; index++) {
                put(values, terms.get(index).text(), descriptions.get(index).text());
            }
        }

        for (Element item : document.select("li:has(> strong):has(> div.infoCont)")) {
            Element label = item.selectFirst("> strong");
            Element value = item.selectFirst("> div.infoCont");
            if (label != null && value != null) {
                put(values, label.text(), value.text());
            }
        }

        return values;
    }

    private void put(Map<String, String> values, String label, String value) {
        String normalizedLabel = normalize(label);
        String normalizedValue = normalize(value);
        if (!normalizedLabel.isBlank() && !normalizedValue.isBlank()) {
            values.putIfAbsent(normalizedLabel, normalizedValue);
        }
    }

    private String extractByLabels(String text, List<String> labels) {
        for (String label : labels) {
            int labelIndex = text.indexOf(label);
            if (labelIndex < 0) {
                continue;
            }
            int start = labelIndex + label.length();
            int end = nextLabelIndex(text, start);
            if (end < 0 || end - start > 800) {
                end = Math.min(text.length(), start + 800);
            }
            String value = text.substring(start, end)
                    .replaceAll("^[\\s:：\\-]+", "")
                    .trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private int nextLabelIndex(String text, int start) {
        return KNOWN_LABELS.stream()
                .map(label -> text.indexOf(label, start + 1))
                .filter(index -> index > start)
                .min(Integer::compareTo)
                .orElse(-1);
    }

    private List<RateMatch> rates(String text) {
        Matcher matcher = RATE_PATTERN.matcher(text);
        return matcher.results()
                .map(result -> new RateMatch(result.group(), new BigDecimal(result.group(2))))
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private record RateMatch(String text, BigDecimal rate) {
    }
}
