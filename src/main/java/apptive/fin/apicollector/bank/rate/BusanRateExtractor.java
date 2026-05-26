package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class BusanRateExtractor extends RateExtractionSupport implements BankRateExtractor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BankCode bankCode() {
        return BankCode.BUSAN;
    }

    @Override
    public ExtractedRates extract(ProductCandidate candidate, StaticHtmlClient.FetchedPage page) {
        return templateJson(page.html())
                .map(this::ratesFromTemplate)
                .filter(ExtractedRates::hasAnyRate)
                .orElseGet(() -> ratesFromRenderedFallback(page.html()));
    }

    private Optional<JsonNode> templateJson(String html) {
        Element input = Jsoup.parse(html).selectFirst("input.SSP_TMPLT_JSON_TEXT[value]");
        if (input == null) {
            return Optional.empty();
        }
        try {
            String decoded = URLDecoder.decode(input.attr("value"), StandardCharsets.UTF_8);
            return Optional.of(objectMapper.readTree(decoded));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    private ExtractedRates ratesFromTemplate(JsonNode root) {
        BigDecimal baseRate = null;
        BigDecimal maxRate = null;

        for (JsonNode item : root.path("dtb_Ltiv")) {
            String name = item.path("MKPD_LTIV_ITEM_NM").asText("");
            BigDecimal rate = decimal(item.path("MKPD_LTIV_ITEM_CNTN").asText(""));
            if (rate == null) {
                continue;
            }
            if (name.contains("기본금리")) {
                baseRate = rate;
            }
            if (name.contains("최고금리")) {
                maxRate = rate;
            }
        }

        if (baseRate == null || maxRate == null) {
            for (JsonNode item : root.path("dtb_RtmNtrt")) {
                if (baseRate == null) {
                    baseRate = decimal(item.path("BAS_INRST").asText(""));
                }
                if (maxRate == null) {
                    maxRate = decimal(item.path("TRG_BTE_DPO_EGM_NTRT").asText(""));
                }
            }
        }

        return new ExtractedRates(baseRate, maxRate);
    }

    private ExtractedRates ratesFromRenderedFallback(String html) {
        BigDecimal baseRate = null;
        BigDecimal maxRate = null;
        for (String text : selectTexts(html, ".digital-guide-container .years-info-box span")) {
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

    private BigDecimal decimal(String text) {
        return decimalRates(text).stream().findFirst().orElse(null);
    }
}
