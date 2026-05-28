package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KbankRateExtractor extends GenericHtmlRateExtractorSupport implements BankRateExtractor {
    private static final Pattern CONTRACT_RATE_PATTERN = Pattern.compile(
            "\\{[^{}]*\"pdCndNm\":\"약정이율\"[^{}]*\"bsicIntRt\":\"([0-9.]+)\"[^{}]*\"maxIntRt\":\"([0-9.]+)\"[^{}]*}"
    );

    @Override
    public BankCode bankCode() {
        return BankCode.KBANK;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        List<BigDecimal> rates = new ArrayList<>();
        Matcher matcher = CONTRACT_RATE_PATTERN.matcher(page.html());
        while (matcher.find()) {
            rates.add(new BigDecimal(matcher.group(1)));
            rates.add(new BigDecimal(matcher.group(2)));
        }
        if (!rates.isEmpty()) {
            return minMax(rates);
        }

        return genericExtract(page);
    }
}
