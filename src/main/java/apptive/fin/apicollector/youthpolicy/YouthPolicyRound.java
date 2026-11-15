package apptive.fin.apicollector.youthpolicy;

import apptive.fin.apicollector.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "youth_policy_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YouthPolicyRound extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private YouthPolicy policy;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "apply_start_date", nullable = false)
    private LocalDate applyStartDate;

    @Column(name = "apply_end_date", nullable = false)
    private LocalDate applyEndDate;

    // 날짜가 바뀐 시각. Fin-BE 캘린더가 7일간 '일정 변경'으로 표시한다.
    @Column(name = "schedule_changed_at")
    private Instant scheduleChangedAt;

    YouthPolicyRound(YouthPolicy policy, int roundNo, LocalDate applyStartDate, LocalDate applyEndDate) {
        this.policy = policy;
        this.roundNo = roundNo;
        this.applyStartDate = applyStartDate;
        this.applyEndDate = applyEndDate;
    }

    void reschedule(LocalDate start, LocalDate end, Instant changedAt) {
        if (start.equals(applyStartDate) && end.equals(applyEndDate)) {
            return;
        }
        this.applyStartDate = start;
        this.applyEndDate = end;
        this.scheduleChangedAt = changedAt;
    }
}
