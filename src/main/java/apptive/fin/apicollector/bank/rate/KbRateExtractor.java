package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class KbRateExtractor extends RateExtractionSupport implements BankRateExtractor {

    @Override
    public BankCode bankCode() {
        return BankCode.KB;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        List<BigDecimal> summaryRates = new ArrayList<>();
        for (String text : selectTexts(page.html(), ".info-data3 dl:has(dt:contains(최고금리)) dd")) {
            summaryRates.addAll(ratesWithPercent(text));
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
}
