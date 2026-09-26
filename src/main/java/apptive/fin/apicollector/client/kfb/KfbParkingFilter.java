package apptive.fin.apicollector.client.kfb;

import org.springframework.stereotype.Component;

/**
 * 입출금자유예금 중 파킹통장으로 볼 상품을 고른다.
 *
 * <p>지금은 넓은 정의(입출금자유예금 전체 = 파킹통장, 추천 명세 A2의 "파킹 44건" 전제)를 써서 전부 통과시킨다.
 * "파킹통장"은 공시 분류가 아닌 시장 통칭이라, 좁은 정의(수시입출금 + 지속 실적 없이 받는 금리 + 청년 가입 가능)로
 * 좁히려면 우대조건 텍스트를 읽어야 한다. 공시의 기본금리 칸은 구간형이면 최저 구간, 보관함형이면 본통장 금리라
 * 대리 지표가 되지 못한다. 정의가 바뀌면 이 클래스만 고치면 되도록 필터 자리를 남겨 둔다.
 */
@Component
public class KfbParkingFilter {

    public boolean isParking(KfbRawProduct product) {
        return true;
    }

    /*
     * 좁은 정의 임시 규칙: 기본금리 ≥ 1.0% 또는 상품명에 "파킹".
     * 2026-09-27 공시 기준 45개 중 17개를 남겼으나 저금통·챌린지형을 통과시키고 보관함형(딴주머니)을 떨어뜨려 보류했다.
     *
    private static final BigDecimal MIN_BASE_RATE = new BigDecimal("1.00");

    public boolean isParking(KfbRawProduct product) {
        return hasHighBaseRate(product.baseRate()) || isNamedParking(product.productName());
    }

    private static boolean hasHighBaseRate(BigDecimal baseRate) {
        return baseRate != null && baseRate.compareTo(MIN_BASE_RATE) >= 0;
    }

    private static boolean isNamedParking(String productName) {
        return productName != null && productName.contains("파킹");
    }
    */
}
