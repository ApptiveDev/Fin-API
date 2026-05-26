package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class KyongnamRateExtractor extends RateExtractionSupport implements BankRateExtractor {

    @Override
    public BankCode bankCode() {
        return BankCode.KYONGNAM;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        ExtractedRates topRates = ratesFromTopSummary(page.html());
        if (topRates.hasAnyRate()) {
            return topRates;
        }
        return ratesFromInterestTable(page.html());
    }

    private ExtractedRates ratesFromTopSummary(String html) {
        BigDecimal baseRate = null;
        BigDecimal maxRate = null;

        for (String text : selectTexts(html, ".acc_newtxtinfo .acbox")) {
            BigDecimal rate = decimal(text);
            if (text.contains("기본")) {
                baseRate = rate;
            }
            if (text.contains("최고")) {
                maxRate = rate;
            }
        }

        return new ExtractedRates(baseRate, maxRate);
    }

    private ExtractedRates ratesFromInterestTable(String html) {
        Document document = Jsoup.parse(html);
        BigDecimal baseRate = null;
        BigDecimal maxRate = null;

        for (Element row : document.select("[data-irt-tabPanel=D1] table.data-table tr")) {
            String rowText = row.text().replaceAll("\\s+", " ").trim();
            if (rowText.contains("약정금리")) {
                baseRate = decimal(rowText);
            }
            if (rowText.contains("우대금리") && rowText.contains("최대") && baseRate != null) {
                BigDecimal preferredRate = decimal(rowText);
                if (preferredRate != null) {
                    maxRate = baseRate.add(preferredRate);
                }
            }
        }

        return new ExtractedRates(baseRate, maxRate);
    }

    private BigDecimal decimal(String text) {
        return decimalRates(text).stream().findFirst().orElse(null);
    }
}
