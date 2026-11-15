package apptive.fin.apicollector.youthpolicy;

import apptive.fin.apicollector.support.IntegrationTestSupport;
import apptive.fin.apicollector.youthpolicy.YouthPolicyDraft.RoundDates;
import apptive.fin.apicollector.youthpolicy.YouthPolicySyncService.SyncResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Fin-BE V10 스키마(서브모듈)에 실제로 쓰고 읽어 확인한다. */
class YouthPolicySyncServiceTest extends IntegrationTestSupport {

    private static final Instant FIRST_SYNC = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant SECOND_SYNC = Instant.parse("2026-11-02T00:00:00Z");
    private static final RoundDates MAY = new RoundDates(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21));
    private static final RoundDates JUNE = new RoundDates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));

    @Autowired private YouthPolicySyncService syncService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM youth_policy");
    }

    @Test
    void 처음_받은_정책과_회차를_저장한다() {
        SyncResult result = syncService.sync(List.of(draft("A", MAY, JUNE)), FIRST_SYNC);

        assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
        Map<String, Object> policy = jdbcTemplate.queryForMap(
                "SELECT name, min_age, max_age, region_codes, apply_period_type, is_active FROM youth_policy WHERE policy_no = 'A'");
        assertThat(policy)
                .containsEntry("name", "정책 A")
                .containsEntry("min_age", 19)
                .containsEntry("max_age", 34)
                .containsEntry("region_codes", "26110")
                .containsEntry("apply_period_type", "PERIOD")
                .containsEntry("is_active", true);
        assertThat(rounds("A")).containsExactly("1:2026-05-02~2026-05-21:null", "2:2026-06-01~2026-06-15:null");
    }

    @Test
    void 날짜가_바뀐_회차만_변경_시각을_남긴다() {
        syncService.sync(List.of(draft("A", MAY, JUNE)), FIRST_SYNC);
        RoundDates juneExtended = new RoundDates(JUNE.start(), LocalDate.of(2026, 6, 30));

        SyncResult result = syncService.sync(List.of(draft("A", MAY, juneExtended)), SECOND_SYNC);

        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(rounds("A")).containsExactly(
                "1:2026-05-02~2026-05-21:null",
                "2:2026-06-01~2026-06-30:changed");
    }

    @Test
    void 사라진_회차는_지우고_사라진_정책은_비활성화한다() {
        syncService.sync(List.of(draft("A", MAY, JUNE), draft("B", MAY)), FIRST_SYNC);

        SyncResult result = syncService.sync(List.of(draft("A", MAY)), SECOND_SYNC);

        assertThat(result).isEqualTo(new SyncResult(0, 1, 1));
        assertThat(rounds("A")).containsExactly("1:2026-05-02~2026-05-21:null");
        assertThat(isActive("B")).isFalse();
    }

    @Test
    void 다시_나타난_정책은_활성화한다() {
        syncService.sync(List.of(draft("A", MAY)), FIRST_SYNC);
        syncService.sync(List.of(draft("B", MAY)), SECOND_SYNC);
        assertThat(isActive("A")).isFalse();

        syncService.sync(List.of(draft("A", MAY), draft("B", MAY)), SECOND_SYNC);

        assertThat(isActive("A")).isTrue();
    }

    private Boolean isActive(String policyNo) {
        return jdbcTemplate.queryForObject(
                "SELECT is_active FROM youth_policy WHERE policy_no = ?", Boolean.class, policyNo);
    }

    private List<String> rounds(String policyNo) {
        return jdbcTemplate.query("""
                        SELECT r.round_no, r.apply_start_date, r.apply_end_date, r.schedule_changed_at
                        FROM youth_policy_round r JOIN youth_policy p ON p.id = r.policy_id
                        WHERE p.policy_no = ? ORDER BY r.round_no
                        """,
                (rs, i) -> rs.getInt(1) + ":" + rs.getDate(2) + "~" + rs.getDate(3) + ":"
                        + (rs.getTimestamp(4) == null ? "null" : "changed"),
                policyNo);
    }

    private static YouthPolicyDraft draft(String policyNo, RoundDates... rounds) {
        return new YouthPolicyDraft(policyNo, "정책 " + policyNo, "기관", "설명", 19, 34, "26110",
                ApplyPeriodType.PERIOD, List.of(rounds));
    }
}
