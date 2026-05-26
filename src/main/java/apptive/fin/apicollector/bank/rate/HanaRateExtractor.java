package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class HanaRateExtractor extends RateExtractionSupport implements BankRateExtractor {
    private static final Pattern RATE_API_PATTERN = Pattern.compile("wpcus401_99i_01\\.do\\?prdCd=([0-9]+)");
    private final StaticHtmlClient htmlClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BankCode bankCode() {
        return BankCode.HANA;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        return productCode(page.html())
                .map(this::fetchRates)
                .filter(ExtractedRates::hasAnyRate)
                .orElseGet(() -> ratesFromVisibleText(page.html()));
    }

    private Optional<String> productCode(String html) {
        Matcher matcher = RATE_API_PATTERN.matcher(html == null ? "" : html);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private ExtractedRates fetchRates(String productCode) {
        try {
            String apiUrl = "https://www.kebhana.com/myhana/personal/wpcus401_99i_01.do?prdCd=" + productCode;
            StaticHtmlClient.FetchedPage apiPage = htmlClient.fetch(apiUrl);
            JsonNode first = objectMapper.readTree(apiPage.text()).path("irtList").path(0);
            return new ExtractedRates(
                    decimal(first.path("baseIrt").asText("")),
                    decimal(first.path("maxIrt").asText(""))
            );
        }
        catch (Exception e) {
            return empty();
        }
    }

    private ExtractedRates ratesFromVisibleText(String html) {
        BigDecimal baseRate = null;
        BigDecimal maxRate = null;
        for (String text : selectTexts(html, ".depositSummary, .product-detail, .contArea, dl")) {
            Matcher matcher = Pattern.compile("기본\\s*연\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%.*최고\\s*연\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%").matcher(text);
            if (matcher.find()) {
                baseRate = decimal(matcher.group(1));
                maxRate = decimal(matcher.group(2));
                break;
            }
        }
        return new ExtractedRates(baseRate, maxRate);
    }

    private BigDecimal decimal(String text) {
        return decimalRates(text).stream().findFirst().orElse(null);
    }
}
