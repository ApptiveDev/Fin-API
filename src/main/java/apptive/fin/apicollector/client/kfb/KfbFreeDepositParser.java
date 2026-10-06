package apptive.fin.apicollector.client.kfb;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 은행연합회 입출금자유예금 비교공시 HTML 파서.
 *
 * <p>목록 행(td 6개: 은행/상품명/기본금리/최고금리/이자지급방식/보기) 바로 다음 {@code tr#Goods_Text_TR}에
 * 상세 정보가 {@code ul > li(라벨), li(값)} 쌍으로 들어 있다. 상세는 순서 대신 라벨로 찾는다.
 */
@Slf4j
@Component
public class KfbFreeDepositParser {

    // 은행/상품명/기본금리/최고금리/이자지급방식까지 있어야 상품 행으로 읽을 수 있다.
    private static final int MIN_PRODUCT_CELLS = 5;

    public List<KfbBank> parseBanks(String html) {
        Document document = Jsoup.parse(html);
        return document.select("input[name=BankSelect]").stream()
                .map(input -> new KfbBank(input.val(), labelOf(document, input)))
                .toList();
    }

    /**
     * 응답 행에는 은행코드가 없어서, 행의 은행명을 검색 페이지의 은행 라벨과 맞춰 코드를 붙인다.
     * 라벨에 없는 은행명이 나오면 코드 없이 저장할 수 없으므로 실패시킨다.
     */
    public List<KfbRawProduct> parseProducts(String html, List<KfbBank> banks) {
        Map<String, String> codeByName = new HashMap<>();
        for (KfbBank bank : banks) {
            codeByName.put(bank.name(), bank.code());
        }
        // 이상한 행 하나 때문에 공시 전체 수집이 실패하지 않도록, 상품 행으로 읽을 수 없는 행은 건너뛴다.
        return Jsoup.parse(html).select("table.resultList_ty02 tr:has(td.tl)").stream()
                .filter(KfbFreeDepositParser::isProductRow)
                .map(row -> toProduct(codeByName, row))
                .toList();
    }

    private static boolean isProductRow(Element row) {
        if (row.select("> td").size() >= MIN_PRODUCT_CELLS) {
            return true;
        }
        log.warn("Skipping KFB result row with too few cells. row={}", clean(row.text()));
        return false;
    }

    private static KfbRawProduct toProduct(Map<String, String> codeByName, Element row) {
        Elements cells = row.select("> td");
        String bankName = clean(cells.get(0).ownText());
        String bankCode = codeByName.get(bankName);
        if (bankCode == null) {
            throw new IllegalStateException("KFB result row has a bank name missing from the search page. bankName=" + bankName);
        }
        Element link = cells.get(1).selectFirst("a");
        Map<String, String> details = details(row.nextElementSibling());

        return new KfbRawProduct(
                bankCode,
                bankName,
                clean(cells.get(1).text()),
                // 원본 그대로 둔다. 아웃링크 정규화·검증은 정규화 단계(KfbProductUrlNormalizer)에서 한다.
                link == null ? null : blankToNull(link.attr("href").trim()),
                decimal(cells.get(2).text()),
                decimal(cells.get(3).text()),
                clean(cells.get(4).text()),
                details.get("가입방법"),
                details.get("우대조건"),
                details.get("가입 제한조건"),
                details.get("가입대상"),
                details.get("기타 유의사항"),
                amount(details.get("최고한도"))
        );
    }

    private static Map<String, String> details(Element detailRow) {
        Map<String, String> details = new HashMap<>();
        if (detailRow == null || !"Goods_Text_TR".equals(detailRow.id())) {
            return details;
        }
        for (Element item : detailRow.select("div.openTxt02 > ul")) {
            Elements pair = item.select("> li");
            if (pair.size() < 2) {
                continue;
            }
            String value = multilineText(pair.get(1));
            if (value != null) {
                details.put(clean(pair.get(0).text()), value);
            }
        }
        return details;
    }

    // <br>로 나뉜 줄(구간별 금리, 번호 목록)은 의미가 있으므로 줄바꿈으로 보존하고 줄마다 공백만 정리한다.
    private static String multilineText(Element element) {
        Element copy = element.clone();
        copy.select("br").after("\n");
        String text = Arrays.stream(copy.wholeText().split("\n"))
                .map(KfbFreeDepositParser::clean)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return blankToNull(text);
    }

    private static String labelOf(Document document, Element input) {
        Element label = document.selectFirst("label[for=" + input.id() + "]");
        return label == null ? null : clean(label.text());
    }

    // "1.70%", "1,000.00"도 받는다. "-"처럼 숫자가 아니면 그 칸만 비운다.
    private static BigDecimal decimal(String text) {
        String value = clean(text == null ? null : text.replace("%", "").replace(",", ""));
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        }
        catch (NumberFormatException e) {
            log.warn("KFB rate cell is not a number. value={}", text);
            return null;
        }
    }

    // "100,000,000원" → 100000000
    private static Long amount(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Long.parseLong(digits);
    }

    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        return blankToNull(text.replace(' ', ' ').replaceAll("\\s+", " ").trim());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
