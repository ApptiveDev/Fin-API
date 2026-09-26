package apptive.fin.apicollector.client.kfb;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 입출금자유예금 중 파킹통장으로 볼 상품을 고른다.
 *
 * <p>조건을 채워야 금리가 나오는 급여·주거래 통장은 기본금리가 0.1% 안팎이라, 기본금리만으로 대부분 걸러진다.
 * 기본금리가 낮아도 은행이 파킹통장으로 내놓은 상품은 이름으로 살린다.
 * 추천 명세(A2)는 입출금자유예금 전체(44건)를 파킹으로 보고 있어 이 규칙은 기획 확인 전 임시 기준이다.
 */
@Component
public class KfbParkingFilter {

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
}
