package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class GenericHtmlRateExtractorSupport extends RateExtractionSupport {
    private static final Pattern BASE_RATE_PATTERN = Pattern.compile(
            "(?:기본\\s*(?:금리|이율)|최저\\s*연?|최저)\\D{0,30}([0-9]+(?:\\.[0-9]+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MAX_RATE_PATTERN = Pattern.compile(
            "(?:최고\\s*(?:금리|이율|연)?|최대\\s*(?:금리|이율|연)?)\\D{0,30}([0-9]+(?:\\.[0-9]+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE
    );

    protected ExtractedRates genericExtract(StaticHtmlClient.FetchedPage page) {
        String text = page.text().replaceAll("\\s+", " ").trim();
        Optional<BigDecimal> base = firstRate(BASE_RATE_PATTERN, text);
        Optional<BigDecimal> max = firstRate(MAX_RATE_PATTERN, text);
        if (base.isPresent() || max.isPresent()) {
            return new ExtractedRates(base.orElse(max.orElse(null)), max.orElse(base.orElse(null)));
        }

        List<BigDecimal> sectionRates = new ArrayList<>();
        sectionText(text, "금리", List.of("중도해지", "만기후", "유의사항", "예금자보호"))
                .ifPresent(section -> sectionRates.addAll(ratesWithPercent(section)));
        if (!sectionRates.isEmpty()) {
            return minMax(sectionRates);
        }
        return empty();
    }

    private Optional<BigDecimal> firstRate(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            BigDecimal rate = new BigDecimal(matcher.group(1));
            if (isValidRate(rate)) {
                return Optional.of(rate);
            }
        }
        return Optional.empty();
    }
}
