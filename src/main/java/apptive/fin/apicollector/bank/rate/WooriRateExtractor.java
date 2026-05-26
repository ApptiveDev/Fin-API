package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class WooriRateExtractor extends RateExtractionSupport implements BankRateExtractor {

    @Override
    public BankCode bankCode() {
        return BankCode.WOORI;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        List<BigDecimal> rates = new ArrayList<>();
        for (String text : selectTexts(page.html(), "#content .product-detail, #content .product_view, #content .tbl-type, #content .tbl-list")) {
            rates.addAll(ratesFromWooriProductArea(text));
        }
        if (!rates.isEmpty()) {
            return minMax(rates);
        }

        for (String row : tableRows(page.html())) {
            rates.addAll(ratesFromWooriProductArea(row));
        }

        return minMax(rates);
    }

    private List<BigDecimal> ratesFromWooriProductArea(String text) {
        if (!(text.contains("기본금리") || text.contains("약정이율") || text.contains("최고금리") || text.contains("우대금리"))) {
            return List.of();
        }
        List<BigDecimal> result = new ArrayList<>();
        result.addAll(ratesWithPercent(text));
        result.addAll(decimalRates(text));
        return result;
    }
}
