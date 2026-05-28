package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WooriRateExtractor extends RateExtractionSupport implements BankRateExtractor {
    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile("^P\\d+$");
    private static final String RATE_TAB_PATH = "/pot/jcc?withyou=PODEP0019&__ID=c011852";

    private final StaticHtmlClient htmlClient;

    WooriRateExtractor() {
        this(null);
    }

    @Autowired
    public WooriRateExtractor(StaticHtmlClient htmlClient) {
        this.htmlClient = htmlClient;
    }

    @Override
    public BankCode bankCode() {
        return BankCode.WOORI;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        List<BigDecimal> summaryRates = ratesFromSummaryDom(page.html());
        if (summaryRates.size() >= 2) {
            return minMax(summaryRates);
        }

        ExtractedRates tabRates = rateTabRates(page);
        if (tabRates.baseRate() != null || tabRates.maxRate() != null) {
            return tabRates;
        }
        if (!summaryRates.isEmpty()) {
            return minMax(summaryRates);
        }

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

    private List<BigDecimal> ratesFromSummaryDom(String html) {
        Document document = Jsoup.parse(html);
        List<BigDecimal> rates = new ArrayList<>();
        for (Element input : document.select("input[name=CHR_TXT], input[name=SPCHR_TXT]")) {
            rates.addAll(ratesWithPercent(Jsoup.parse(input.attr("value")).text()));
        }
        for (Element element : document.select(".product-box .prd-info dd.tit em, .product-list .prd-info .tit em")) {
            rates.addAll(ratesWithPercent(element.text()));
        }
        return rates;
    }

    private ExtractedRates rateTabRates(StaticHtmlClient.FetchedPage page) {
        if (htmlClient == null) {
            return empty();
        }
        Optional<String> productCode = productCode(page.html());
        if (productCode.isEmpty()) {
            return empty();
        }

        try {
            StaticHtmlClient.FetchedPage ratePage = htmlClient.post(
                    URI.create(page.url()).resolve(RATE_TAB_PATH).toString(),
                    Map.of("PRD_CD", productCode.get(), "pPRD_CD", "01")
            );
            return ratesFromRateTabDom(ratePage.html());
        }
        catch (Exception e) {
            return empty();
        }
    }

    private Optional<String> productCode(String html) {
        Document document = Jsoup.parse(html);
        for (Element input : document.select("input[name=PRD_CD], input[name=PRD_CODE], input[name=PLM_PDCD]")) {
            String value = input.attr("value").trim();
            Matcher matcher = PRODUCT_CODE_PATTERN.matcher(value);
            if (matcher.matches()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private ExtractedRates ratesFromRateTabDom(String html) {
        Document document = Jsoup.parse(html);
        List<BigDecimal> preferredRates = new ArrayList<>();
        List<BigDecimal> allRates = new ArrayList<>();

        for (Element row : document.select("table.tbl-type-1 tbody tr")) {
            Element rateCell = row.selectFirst("td.dtd-r");
            if (rateCell == null) {
                continue;
            }
            List<BigDecimal> rowRates = decimalRates(rateCell.text()).stream()
                    .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            allRates.addAll(rowRates);

            String rowText = row.text().replaceAll("\\s+", "");
            if (rowText.contains("2년이상")
                    || rowText.contains("10년")
                    || rowText.contains("15개월")) {
                preferredRates.addAll(rowRates);
            }
        }

        if (!preferredRates.isEmpty()) {
            return minMax(preferredRates);
        }
        return minMax(allRates);
    }

    private List<BigDecimal> ratesFromWooriProductArea(String text) {
        if (!(text.contains("기본금리")
                || text.contains("약정이율")
                || text.contains("최고금리")
                || text.contains("우대금리"))) {
            return List.of();
        }
        List<BigDecimal> result = new ArrayList<>();
        result.addAll(ratesWithPercent(text));
        result.addAll(decimalRates(text));
        return result;
    }
}
