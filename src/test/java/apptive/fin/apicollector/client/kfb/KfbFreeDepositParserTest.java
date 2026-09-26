package apptive.fin.apicollector.client.kfb;

import apptive.fin.apicollector.normalize.normalizer.KfbProductUrlNormalizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        List<KfbRawProduct> products = parser.parseProducts("0015130", fixture(ALL_BANKS_RESULT));

        assertThat(products).hasSize(45);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.bankCode()).isEqualTo("0015130");
            assertThat(product.baseRate()).isNotNull();
            assertThat(product.maxRate()).isNotNull();
        });
    }

    // 파서는 원본 링크를 그대로 담는다. 아웃링크 정규화·검증은 정규화 단계(KfbProductUrlNormalizer)의 몫이라 raw에 원본이 남아야 한다.
    @Test
    void keepsProductHrefAsIs() {
        KfbRawProduct woori = product(parser.parseProducts("0010001", fixture(ALL_BANKS_RESULT)), "우월한 월급 통장");

        assertThat(woori.productUrl()).isEqualTo("https://자유입출금상품>예금상품상세 - 우리은행");
    }

    // 실제 공시 링크를 아웃링크용으로 정규화하면, 깨진 우리은행 링크 하나만 떨어지고 나머지 44개는 살아야 한다.
    @Test
    void realDisclosureLinksSurviveUrlNormalizationExceptBrokenOne() {
        KfbProductUrlNormalizer urlNormalizer = new KfbProductUrlNormalizer();
        List<KfbRawProduct> products = parser.parseProducts("any", fixture(ALL_BANKS_RESULT));

        List<String> rejected = products.stream()
                .filter(product -> urlNormalizer.normalize(product.productUrl()).isEmpty())
                .map(KfbRawProduct::productName)
                .toList();

        assertThat(rejected).containsExactly("우월한 월급 통장");
    }

    @Test
    void mapsListColumnsAndDetailLabelsToFields() {
        KfbRawProduct safeBox = product(parser.parseProducts("0015130", fixture(ALL_BANKS_RESULT)), "세이프박스");

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
        KfbRawProduct safeBox = product(parser.parseProducts("0015130", fixture(ALL_BANKS_RESULT)), "세이프박스");

        assertThat(safeBox.etcNote().lines().toList()).containsExactly(
                "1. 상품설명 : 예비자금을 언제든지 입금하고 출금할 수 있는 계좌 속 금고",
                "2. 가입방법 : 스마트폰",
                "3. 거래방법 : 연결된 카카오뱅크 입출금통장 또는 개인사업자통장을 통한 입출금만 가능",
                "4. 이자지급 : 매월 네번째 금요일의 다음날 또는 고객이 이자지급을 요청한 날에 바로 지급"
        );
    }

    @Test
    void collapsesLineBreakInProductName() {
        List<KfbRawProduct> products = parser.parseProducts("0014807", fixture(ALL_BANKS_RESULT));

        assertThat(products).extracting(KfbRawProduct::productName)
                .contains("Sh평생주거래우대통장 (잔액구간별)", "Sh평생주거래우대통장 (예치기간별)");
    }

    @Test
    void leavesMaxLimitNullWhenBlank() {
        KfbRawProduct kdb = product(parser.parseProducts("0010030", fixture(ALL_BANKS_RESULT)), "KDB Hi 입출금통장");

        assertThat(kdb.maxLimit()).isNull();
    }

    private static final String ALL_BANKS_RESULT = "free_deposit_search_result_all_banks.html";

    private static KfbRawProduct product(List<KfbRawProduct> products, String name) {
        return products.stream()
                .filter(product -> name.equals(product.productName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no product named " + name));
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
