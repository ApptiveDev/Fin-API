package apptive.fin.apicollector.client.kfb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KfbParkingFilterTest {

    private final KfbParkingFilter filter = new KfbParkingFilter();

    // 넓은 정의(입출금자유예금 전체 = 파킹통장)를 쓰기로 해서, 실제 공시 45개가 모두 통과해야 한다.
    @Test
    void acceptsEveryProductFromRealDisclosureUnderBroadDefinition() {
        List<KfbRawProduct> all = new KfbFreeDepositParser()
                .parseProducts("any", KfbFreeDepositParserTest.fixture("free_deposit_search_result_all_banks.html"));

        assertThat(all).hasSize(45);
        assertThat(all).allMatch(filter::isParking);
    }

    /*
     * 좁은 정의 규칙(기본금리 ≥ 1.0% 또는 이름에 "파킹")을 쓸 때의 테스트. 규칙을 되살리면 함께 되살린다.
     * 이 규칙은 2026-09-27 공시 45개 중 17개를 남겼고, 파킹이 아닌 3개(카카오뱅크 저금통, 토스뱅크 이자 받는 저금통,
     * 챌린지박스)를 통과시키고 파킹인 잇(it)딴주머니통장을 떨어뜨렸다.
     *
    @ParameterizedTest(name = "[{index}] {0} / 기본금리 {1} → {2}")
    @CsvSource(nullValues = "null", value = {
            "일반통장, 1.00, true",
            "일반통장, 0.99, false",
            "일반통장, null, false",
            "BNK파킹통장, 0.01, true",
    })
    void keepsProductWhenBaseRateIsAtLeastOnePercentOrNameSaysParking(String name, BigDecimal baseRate, boolean expected) {
        assertThat(filter.isParking(product(name, baseRate))).isEqualTo(expected);
    }

    @Test
    void keepsExpectedProductsFromRealDisclosure() {
        List<KfbRawProduct> all = new KfbFreeDepositParser()
                .parseProducts("any", KfbFreeDepositParserTest.fixture("free_deposit_search_result_all_banks.html"));

        List<String> kept = all.stream().filter(filter::isParking).map(KfbRawProduct::productName).toList();

        assertThat(kept).containsExactlyInAnyOrder(
                "KDB Hi 입출금통장",
                "제일EZ통장",
                "IBK중기근로자급여파킹통장 (보통예금)",
                "Sh매일받는통장",
                "비상금박스",
                "매일이자Wa파킹통장",
                "365파킹통장",
                "씨드모아(카드우대) 통장",
                "씨드모아(고액우대) 통장",
                "씨드모아(소액우대) 통장",
                "BNK파킹통장",
                "플러스박스",
                "챌린지박스",
                "세이프박스",
                "카카오뱅크 저금통",
                "토스뱅크 통장",
                "토스뱅크 이자 받는 저금통"
        );
    }

    private static KfbRawProduct product(String name, BigDecimal baseRate) {
        return new KfbRawProduct("0010001", "은행", name, null, baseRate, null,
                null, null, null, null, null, null, null);
    }
    */
}
