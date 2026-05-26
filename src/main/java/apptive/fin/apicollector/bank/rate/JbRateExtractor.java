package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class JbRateExtractor extends RateExtractionSupport implements BankRateExtractor {

    @Override
    public BankCode bankCode() {
        return BankCode.JB;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        List<BigDecimal> rates = new ArrayList<>();
        for (String text : selectTexts(page.html(), "#content, .contents, .product_detail, table")) {
            rates.addAll(ratesFromJbProductArea(text));
        }
        if (!rates.isEmpty()) {
            return minMax(rates);
        }

        for (String row : tableRows(page.html())) {
            rates.addAll(ratesFromJbProductArea(row));
        }
        if (!rates.isEmpty()) {
            return minMax(rates);
        }

        return sectionText(
                page.text(),
                "적용이율",
                List.of("우대", "중도해지", "만기", "유의사항", "예금자보호")
        )
                .map(this::decimalRates)
                .map(this::minMax)
                .orElseGet(this::empty);
    }

    private List<BigDecimal> ratesFromJbProductArea(String text) {
        if (!(text.contains("약정이율") || text.contains("적용이율") || text.contains("기본금리") || text.contains("최고금리"))) {
            return List.of();
        }
        List<BigDecimal> result = new ArrayList<>();
        result.addAll(ratesWithPercent(text));
        result.addAll(decimalRates(text));
        return result;
    }
}
