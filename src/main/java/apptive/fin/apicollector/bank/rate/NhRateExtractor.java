package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class NhRateExtractor extends RateExtractionSupport implements BankRateExtractor {

    @Override
    public BankCode bankCode() {
        return BankCode.NH;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        ExtractedRates smartMarketRates = ratesFromSmartMarketSummary(page.html());
        if (smartMarketRates.hasAnyRate()) {
            return smartMarketRates;
        }

        List<BigDecimal> rates = new ArrayList<>();
        for (String text : selectTexts(page.html(), ".product_new tr")) {
            rates.addAll(ratesFromNhProductArea(text));
        }
        if (!rates.isEmpty()) {
            return minMax(rates);
        }

        for (String row : tableRows(page.html())) {
            rates.addAll(ratesFromNhProductArea(row));
        }

        return minMax(rates);
    }

    private ExtractedRates ratesFromSmartMarketSummary(String html) {
        List<BigDecimal> rates = new ArrayList<>();
        for (String text : selectTexts(html, ".interestBanner")) {
            if (text.contains("최저") || text.contains("최고") || text.contains("금리")) {
                rates.addAll(ratesWithPercent(text));
            }
        }
        return minMax(rates);
    }

    private List<BigDecimal> ratesFromNhProductArea(String text) {
        if (text.contains("중도해지")
                || text.contains("기여금")
                || text.contains("매칭")
                || text.contains("월 최대")
                || text.contains("경과기간")) {
            return List.of();
        }
        if (!(text.contains("기본이자율")
                || text.contains("기본금리")
                || text.contains("최고금리")
                || text.contains("우대금리"))) {
            return List.of();
        }
        return ratesWithPercent(text);
    }
}
