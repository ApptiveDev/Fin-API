package apptive.fin.apicollector.policyround;

import apptive.fin.apicollector.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** 1계층(Y-Fin 정책상품) 신청 회차. 스키마는 Fin-BE V10__create_youth_policy_schema.sql */
@Entity
@Table(name = "policy_product_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyProductRound extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "apply_start_date", nullable = false)
    private LocalDate applyStartDate;

    @Column(name = "apply_end_date", nullable = false)
    private LocalDate applyEndDate;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    // 날짜가 바뀐 시각. Fin-BE 캘린더가 7일간 '일정 변경'으로 표시한다.
    @Column(name = "schedule_changed_at")
    private Instant scheduleChangedAt;

    PolicyProductRound(Long productId, int roundNo, ProductRounds.Round round) {
        this.productId = productId;
        this.roundNo = roundNo;
        this.applyStartDate = round.start();
        this.applyEndDate = round.end();
        this.sourceUrl = round.source();
    }

    /** @return 바뀐 값이 있으면 true */
    boolean apply(ProductRounds.Round round, Instant changedAt) {
        boolean datesChanged = !round.start().equals(applyStartDate) || !round.end().equals(applyEndDate);
        boolean sourceChanged = !Objects.equals(round.source(), sourceUrl);
        if (datesChanged) {
            this.applyStartDate = round.start();
            this.applyEndDate = round.end();
            this.scheduleChangedAt = changedAt;
        }
        this.sourceUrl = round.source();
        return datesChanged || sourceChanged;
    }
}
