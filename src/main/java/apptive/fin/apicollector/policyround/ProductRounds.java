package apptive.fin.apicollector.policyround;

import java.time.LocalDate;
import java.util.List;

/**
 * manual-product-rounds.json의 한 항목. 상품 코드(manual-products.json의 productCode)별 신청 회차.
 * 회차가 없으면(그해 모집이 없거나 공고 전) rounds를 비우고 note에 사유를 적는다.
 */
public record ProductRounds(String productCode, List<Round> rounds, String note) {

    /** @param source 신청기간 근거(공고문, 보도자료 등) */
    public record Round(LocalDate start, LocalDate end, String source, String note) {
    }

    /** 사람이 고치는 파일이라 저장 전에 형식을 확인한다. 틀리면 파일을 고쳐야 하므로 실패시킨다. */
    public void validate() {
        if (productCode == null || productCode.isBlank() || rounds == null) {
            throw new IllegalStateException("productCode와 rounds는 필수입니다. productCode=" + productCode);
        }
        for (Round round : rounds) {
            if (round.start() == null || round.end() == null || round.start().isAfter(round.end())) {
                throw new IllegalStateException(
                        "회차의 시작일과 종료일이 없거나 순서가 틀렸습니다. productCode=%s, start=%s, end=%s"
                                .formatted(productCode, round.start(), round.end()));
            }
        }
    }
}
