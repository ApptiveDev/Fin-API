package apptive.fin.apicollector.bank.rate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class RateExtractionSupport {
    private static final BigDecimal MIN_VALID_RATE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALID_RATE = new BigDecimal("100.00");
    private static final Pattern PERCENT_RATE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*p?");
    private static final Pattern DECIMAL_RATE_PATTERN = Pattern.compile("([0-9]+\\.[0-9]+)");

    protected ExtractedRates empty() {
        return ExtractedRates.empty();
    }

    protected ExtractedRates minMax(List<BigDecimal> rates) {
        List<BigDecimal> validRates = rates.stream()
                .filter(this::isValidRate)
                .toList();
        if (validRates.isEmpty()) {
            return empty();
        }
        BigDecimal min = validRates.stream().min(Comparator.naturalOrder()).orElse(null);
        BigDecimal max = validRates.stream().max(Comparator.naturalOrder()).orElse(null);
        return new ExtractedRates(min, max);
    }

    protected List<BigDecimal> ratesWithPercent(String text) {
        return rates(text, PERCENT_RATE_PATTERN);
    }

    protected List<BigDecimal> decimalRates(String text) {
        return rates(text, DECIMAL_RATE_PATTERN);
    }

    protected Optional<String> sectionText(String text, String startLabel, List<String> endLabels) {
        int start = text.indexOf(startLabel);
        if (start < 0) {
            return Optional.empty();
        }
        int end = endLabels.stream()
                .mapToInt(label -> text.indexOf(label, start + startLabel.length()))
                .filter(index -> index > start)
                .min()
                .orElse(Math.min(text.length(), start + 1_500));
        return Optional.of(text.substring(start, end));
    }

    protected List<String> tableRows(String html) {
        Document document = Jsoup.parse(html);
        List<String> rows = new ArrayList<>();
        for (Element row : document.select("tr")) {
            String rowText = row.text().replaceAll("\\s+", " ").trim();
            if (!rowText.isBlank()) {
                rows.add(rowText);
            }
        }
        return rows;
    }

    protected List<String> selectTexts(String html, String selector) {
        Document document = Jsoup.parse(html);
        List<String> result = new ArrayList<>();
        for (Element element : document.select(selector)) {
            String text = element.text().replaceAll("\\s+", " ").trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<BigDecimal> rates(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        List<BigDecimal> result = new ArrayList<>();
        while (matcher.find()) {
            BigDecimal rate = new BigDecimal(matcher.group(1));
            if (isValidRate(rate)) {
                result.add(rate);
            }
        }
        return result;
    }

    protected boolean isValidRate(BigDecimal rate) {
        return rate.compareTo(MIN_VALID_RATE) >= 0
                && rate.compareTo(MAX_VALID_RATE) <= 0;
    }
}
