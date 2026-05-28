package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class KjbRateExtractor extends GenericHtmlRateExtractorSupport implements BankRateExtractor {
    @Override
    public BankCode bankCode() {
        return BankCode.KJB;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        ExtractedRates summaryRates = topSummaryRates(page.html());
        if (summaryRates.baseRate() != null || summaryRates.maxRate() != null) {
            return summaryRates;
        }
        return genericExtract(page);
    }

    private ExtractedRates topSummaryRates(String html) {
        Document document = Jsoup.parse(html);
        Optional<BigDecimal> baseRate = Optional.empty();
        Optional<BigDecimal> maxRate = Optional.empty();

        for (Element item : document.select(".explain-box .item")) {
            String label = item.select(".name").text().replaceAll("\\s+", "");
            Element number = item.selectFirst(".value .number");
            if (number == null) {
                continue;
            }
            BigDecimal rate = parseRate(number.text());
            if (label.contains("기본")) {
                baseRate = Optional.of(rate);
            }
            if (label.contains("최고") || label.contains("최대")) {
                maxRate = Optional.of(rate);
            }
        }

        if (baseRate.isEmpty() && maxRate.isEmpty()) {
            return empty();
        }
        return new ExtractedRates(
                baseRate.orElse(maxRate.orElse(null)),
                maxRate.orElse(baseRate.orElse(null))
        );
    }

    private BigDecimal parseRate(String value) {
        BigDecimal rate = new BigDecimal(value.replaceAll("[^0-9.]", ""));
        if (!isValidRate(rate)) {
            throw new IllegalArgumentException("Invalid KJB rate: " + value);
        }
        return rate;
    }
}
