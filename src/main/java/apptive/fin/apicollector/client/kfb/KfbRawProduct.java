package apptive.fin.apicollector.client.kfb;

import java.math.BigDecimal;

/**
 * 은행연합회 입출금자유예금 비교공시 한 행(목록 + 상세 펼침)을 파싱한 결과.
 * 매주 바뀌는 "은행 최종제공일"은 raw 해시를 흔들기만 하므로 담지 않는다.
 */
public record KfbRawProduct(
        String bankCode,
        String bankName,
        String productName,
        String productUrl,
        BigDecimal baseRate,
        BigDecimal maxRate,
        String interestPayment,
        String joinMethod,
        String preferentialCondition,
        String joinRestriction,
        String joinTarget,
        String etcNote,
        Long maxLimit
) {
    public String externalId() {
        return "KFB:PARKING:" + bankCode + ":" + productName;
    }
}
