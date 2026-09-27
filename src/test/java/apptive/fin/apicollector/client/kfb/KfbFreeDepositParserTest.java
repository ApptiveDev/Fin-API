package apptive.fin.apicollector.client.kfb;

import apptive.fin.apicollector.bankurl.scraper.BankProductScrapers;
import apptive.fin.apicollector.normalize.normalizer.KfbProductUrlNormalizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KfbFreeDepositParserTest {

    private final KfbFreeDepositParser parser = new KfbFreeDepositParser();

    @Test
    void parsesBankCheckboxesFromSearchPage() {
        List<KfbBank> banks = parser.parseBanks(fixture("free_deposit.html"));

        assertThat(banks).hasSize(19);
        assertThat(banks.getFirst()).isEqualTo(new KfbBank("0010030", "한국산업은행"));
        assertThat(banks).contains(new KfbBank("0015130", "카카오뱅크"));
    }

    @Test
    void parsesEveryProductRowWithItsDetailRow() {
        List<KfbRawProduct> products = realProducts();

        assertThat(products).hasSize(45);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.baseRate()).isNotNull();
            assertThat(product.maxRate()).isNotNull();
        });
    }

    // 결과 행에는 은행코드가 없어서, 검색 페이지 라벨과 은행명이 같아야 코드가 붙는다. 실제 공시의 모든 행이 맞아야 한다.
    @Test
    void mapsEveryRowToBankCodeByBankLabel() {
        List<KfbBank> banks = parser.parseBanks(fixture("free_deposit.html"));
        List<KfbRawProduct> products = parser.parseProducts(fixture(ALL_BANKS_RESULT), banks);

        assertThat(products).allSatisfy(product ->
                assertThat(banks).contains(new KfbBank(product.bankCode(), product.bankName())));
        assertThat(product(products, "세이프박스").bankCode()).isEqualTo("0015130");
        assertThat(product(products, "IBK간편한통장 (보통예금)").bankCode()).isEqualTo("0010026");
    }

    @Test
    void failsWhenRowBankIsMissingFromSearchPage() {
        List<KfbBank> banks = List.of(new KfbBank("0015130", "카카오뱅크"));

        assertThatThrownBy(() -> parser.parseProducts(fixture(ALL_BANKS_RESULT), banks))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bankName=");
    }

    // 파서는 원본 링크를 그대로 담는다. 아웃링크 정규화·검증은 정규화 단계(KfbProductUrlNormalizer)의 몫이라 raw에 원본이 남아야 한다.
    @Test
    void keepsProductHrefAsIs() {
        KfbRawProduct woori = product(realProducts(), "우월한 월급 통장");

        assertThat(woori.productUrl()).isEqualTo("https://자유입출금상품>예금상품상세 - 우리은행");
    }

    // 실제 공시 링크를 은행 도메인과 대조하면 깨진 우리은행 링크, 홈페이지만 가리키는 전북은행 4건,
    // 은행 도메인 밖(앱 딥링크 onelink.me)인 부산은행 1건이 떨어지고 나머지는 살아야 한다. 떨어진 상품은 스크래퍼가 채운다.
    @Test
    void realDisclosureLinksSurviveUrlNormalizationExceptBrokenHomeAndForeignOnes() {
        KfbProductUrlNormalizer urlNormalizer = new KfbProductUrlNormalizer(BankProductScrapers.all());
        List<KfbRawProduct> products = realProducts();

        List<String> rejected = products.stream()
                .filter(product -> urlNormalizer.normalize(product.bankCode(), product.productUrl()).isEmpty())
                .map(product -> product.bankName() + "/" + product.productName())
                .toList();

        assertThat(rejected).containsExactlyInAnyOrder(
                "우리은행/우월한 월급 통장",
                "BNK부산은행/마!이통장",
                "전북은행/씨드모아(카드우대) 통장",
                "전북은행/씨드모아(고액우대) 통장",
                "전북은행/씨드모아(소액우대) 통장",
                "전북은행/JB 언택트 통장"
        );
    }

    @Test
    void mapsListColumnsAndDetailLabelsToFields() {
        KfbRawProduct safeBox = product(realProducts(), "세이프박스");

        assertThat(safeBox.bankName()).isEqualTo("카카오뱅크");
        assertThat(safeBox.baseRate()).isEqualByComparingTo("1.60");
        assertThat(safeBox.maxRate()).isEqualByComparingTo("1.60");
        assertThat(safeBox.interestPayment()).isEqualTo("수시지급,월지급");
        assertThat(safeBox.joinMethod()).isEqualTo("스마트뱅킹");
        assertThat(safeBox.preferentialCondition()).isEqualTo("※별도 우대조건 없음");
        assertThat(safeBox.joinRestriction()).isEqualTo("제한없음");
        assertThat(safeBox.joinTarget()).isEqualTo("만 14세 이상의 실명의 개인");
        assertThat(safeBox.maxLimit()).isEqualTo(100_000_000L);
    }

    @Test
    void keepsLineBreaksInDetailText() {
        KfbRawProduct safeBox = product(realProducts(), "세이프박스");

        assertThat(safeBox.etcNote().lines().toList()).containsExactly(
                "1. 상품설명 : 예비자금을 언제든지 입금하고 출금할 수 있는 계좌 속 금고",
                "2. 가입방법 : 스마트폰",
                "3. 거래방법 : 연결된 카카오뱅크 입출금통장 또는 개인사업자통장을 통한 입출금만 가능",
                "4. 이자지급 : 매월 네번째 금요일의 다음날 또는 고객이 이자지급을 요청한 날에 바로 지급"
        );
    }

    @Test
    void collapsesLineBreakInProductName() {
        List<KfbRawProduct> products = realProducts();

        assertThat(products).extracting(KfbRawProduct::productName)
                .contains("Sh평생주거래우대통장 (잔액구간별)", "Sh평생주거래우대통장 (예치기간별)");
    }

    @Test
    void leavesMaxLimitNullWhenBlank() {
        KfbRawProduct kdb = product(realProducts(), "KDB Hi 입출금통장");

        assertThat(kdb.maxLimit()).isNull();
    }

    // 금리 칸이 숫자가 아니어도 그 칸만 비우고, 나머지 행은 정상으로 읽는다.
    @Test
    void readsRateCellLeniently() {
        List<KfbRawProduct> products = parser.parseProducts(resultTable(
                row("가통장", "-", "1.70%"),
                row("나통장", "", "1,000.00")
        ), BANKS);

        assertThat(products).extracting(KfbRawProduct::productName).containsExactly("가통장", "나통장");
        assertThat(products.get(0).baseRate()).isNull();
        assertThat(products.get(0).maxRate()).isEqualByComparingTo("1.70");
        assertThat(products.get(1).baseRate()).isNull();
        assertThat(products.get(1).maxRate()).isEqualByComparingTo("1000.00");
    }

    @Test
    void skipsRowWithTooFewCells() {
        List<KfbRawProduct> products = parser.parseProducts(resultTable(
                "<tr><td class=\"tl\">조회 결과가 없습니다</td></tr>",
                row("가통장", "0.10", "2.00")
        ), BANKS);

        assertThat(products).extracting(KfbRawProduct::productName).containsExactly("가통장");
    }

    private static final List<KfbBank> BANKS = List.of(new KfbBank("0015130", "카카오뱅크"));

    private static String resultTable(String... rows) {
        return "<table class=\"resultList_ty02\"><tbody>" + String.join("", rows) + "</tbody></table>";
    }

    private static String row(String productName, String baseRate, String maxRate) {
        return """
                <tr><td>카카오뱅크</td><td class="tl"><a href="https://www.kakaobank.com/p">%s</a></td>
                <td>%s</td><td>%s</td><td>월지급</td><td>보기</td></tr>
                """.formatted(productName, baseRate, maxRate);
    }

    private static final String ALL_BANKS_RESULT = "free_deposit_search_result_all_banks.html";

    private static KfbRawProduct product(List<KfbRawProduct> products, String name) {
        return products.stream()
                .filter(product -> name.equals(product.productName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no product named " + name));
    }

    // 실제 검색 페이지의 은행 목록으로 실제 전체 은행 응답을 파싱한 45개 상품
    static List<KfbRawProduct> realProducts() {
        KfbFreeDepositParser parser = new KfbFreeDepositParser();
        return parser.parseProducts(fixture(ALL_BANKS_RESULT), parser.parseBanks(fixture("free_deposit.html")));
    }

    // 실제 응답(2026-09-27, EUC-KR 원본 바이트)을 그대로 둔 fixture
    static String fixture(String name) {
        try (InputStream in = KfbFreeDepositParserTest.class.getResourceAsStream("/kfb/" + name)) {
            return new String(in.readAllBytes(), Charset.forName("MS949"));
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
