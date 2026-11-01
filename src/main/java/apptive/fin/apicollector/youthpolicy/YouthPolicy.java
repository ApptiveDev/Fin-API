package apptive.fin.apicollector.youthpolicy;

import apptive.fin.apicollector.global.entity.BaseTimeEntity;
import apptive.fin.apicollector.youthpolicy.YouthPolicyDraft.RoundDates;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 캘린더용 온통청년 정책. 스키마는 Fin-BE V10__create_youth_policy_schema.sql */
@Entity
@Table(name = "youth_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YouthPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_no", nullable = false, length = 50)
    private String policyNo;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "institution", columnDefinition = "TEXT")
    private String institution;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "region_codes", columnDefinition = "TEXT")
    private String regionCodes;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_period_type", nullable = false, length = 20)
    private ApplyPeriodType applyPeriodType;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("roundNo")
    private List<YouthPolicyRound> rounds = new ArrayList<>();

    public YouthPolicy(String policyNo) {
        this.policyNo = policyNo;
    }

    /** 이번 수집 값으로 덮어쓴다. 회차는 순서(round_no)로 맞춰, 날짜가 바뀐 회차만 변경 시각을 남긴다. */
    public void apply(YouthPolicyDraft draft, Instant syncedAt) {
        this.name = draft.name();
        this.institution = draft.institution();
        this.summary = draft.summary();
        this.minAge = draft.minAge();
        this.maxAge = draft.maxAge();
        this.regionCodes = draft.regionCodes();
        this.applyPeriodType = draft.applyPeriodType();
        this.active = true;
        this.syncedAt = syncedAt;
        syncRounds(draft.rounds(), syncedAt);
    }

    public void deactivate() {
        this.active = false;
    }

    private void syncRounds(List<RoundDates> next, Instant syncedAt) {
        Map<Integer, YouthPolicyRound> existing = rounds.stream()
                .collect(Collectors.toMap(YouthPolicyRound::getRoundNo, Function.identity()));
        for (int i = 0; i < next.size(); i++) {
            int roundNo = i + 1;
            RoundDates dates = next.get(i);
            YouthPolicyRound round = existing.remove(roundNo);
            if (round == null) {
                rounds.add(new YouthPolicyRound(this, roundNo, dates.start(), dates.end()));
            }
            else {
                round.reschedule(dates.start(), dates.end(), syncedAt);
            }
        }
        rounds.removeAll(existing.values());
    }
}
