package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KbRateExtractor extends RateExtractionSupport implements BankRateExtractor {
    private static final Pattern COMPACT_RATE_RANGE_PATTERN = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*~\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%"
    );

    @Override
    public BankCode bankCode() {
        return BankCode.KB;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        List<BigDecimal> summaryRates = new ArrayList<>();
        for (String text : selectTexts(page.html(), ".info-data3 dl:has(dt:contains(최고금리)) dd")) {
            summaryRates.addAll(summaryRates(text));
        }
        if (!summaryRates.isEmpty()) {
            return minMax(summaryRates);
        }

        List<BigDecimal> detailRates = new ArrayList<>();
        for (String text : selectTexts(page.html(), "#uiProTabCon2 li:has(strong:contains(우대이율)) .infoCont")) {
            detailRates.addAll(ratesWithPercent(text));
        }
        return minMax(detailRates);
    }

    private List<BigDecimal> summaryRates(String text) {
        List<BigDecimal> rates = new ArrayList<>(ratesWithPercent(text));
        Matcher matcher = COMPACT_RATE_RANGE_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            rates.add(new BigDecimal(matcher.group(1)));
            rates.add(new BigDecimal(matcher.group(2)));
        }
        return rates;
    }
}
