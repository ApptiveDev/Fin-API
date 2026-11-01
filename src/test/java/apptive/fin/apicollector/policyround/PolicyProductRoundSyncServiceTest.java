package apptive.fin.apicollector.policyround;

import apptive.fin.apicollector.policyround.PolicyProductRoundSyncService.SyncResult;
import apptive.fin.apicollector.policyround.ProductRounds.Round;
import apptive.fin.apicollector.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fin-BE V10 스키마(서브모듈)에 실제로 쓰고 읽어 확인한다. */
class PolicyProductRoundSyncServiceTest extends IntegrationTestSupport {

    private static final Instant FIRST_SYNC = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant SECOND_SYNC = Instant.parse("2026-11-02T00:00:00Z");
    private static final Round MARCH = new Round(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 16), "https://a", null);
    private static final Round JUNE = new Round(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 3), "https://b", null);

    @Autowired private PolicyProductRoundSyncService syncService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        deleteProducts();
        jdbcTemplate.update("INSERT INTO product_source (code, name) VALUES ('ONTONG', 'ONTONG') ON CONFLICT (code) DO NOTHING");
        insertProduct("RTEST_1");
        insertProduct("RTEST_2");
    }

    @AfterEach
    void tearDown() {
        deleteProducts();
    }

    @Test
    void 파일의_회차를_순서대로_저장한다() {
        SyncResult result = syncService.sync(List.of(entry("RTEST_1", MARCH, JUNE)), FIRST_SYNC);

        assertThat(result).isEqualTo(new SyncResult(2, 0, 0, List.of()));
        assertThat(rounds("RTEST_1")).containsExactly(
                "1:2026-03-03~2026-03-16:https://a:null",
                "2:2026-06-15~2026-07-03:https://b:null");
    }

    @Test
    void 날짜가_바뀐_회차만_변경_시각을_남긴다() {
        syncService.sync(List.of(entry("RTEST_1", MARCH, JUNE)), FIRST_SYNC);
        Round juneExtended = new Round(JUNE.start(), LocalDate.of(2026, 7, 10), JUNE.source(), null);
        Round marchNewSource = new Round(MARCH.start(), MARCH.end(), "https://a2", null);

        SyncResult result = syncService.sync(List.of(entry("RTEST_1", marchNewSource, juneExtended)), SECOND_SYNC);

        assertThat(result).isEqualTo(new SyncResult(0, 2, 0, List.of()));
        assertThat(rounds("RTEST_1")).containsExactly(
                "1:2026-03-03~2026-03-16:https://a2:null",
                "2:2026-06-15~2026-07-10:https://b:changed");
    }

    @Test
    void 파일에서_빠진_회차와_상품의_회차는_지운다() {
        syncService.sync(List.of(entry("RTEST_1", MARCH, JUNE), entry("RTEST_2", MARCH)), FIRST_SYNC);

        SyncResult result = syncService.sync(List.of(entry("RTEST_1", MARCH)), SECOND_SYNC);

        assertThat(result).isEqualTo(new SyncResult(0, 0, 2, List.of()));
        assertThat(rounds("RTEST_1")).containsExactly("1:2026-03-03~2026-03-16:https://a:null");
        assertThat(rounds("RTEST_2")).isEmpty();
    }

    // 정부상품 수집 전이면 상품이 없다. 그 상품만 건너뛰고 나머지는 저장한다.
    @Test
    void 아직_수집되지_않은_상품은_건너뛴다() {
        SyncResult result = syncService.sync(List.of(entry("RTEST_NONE", MARCH), entry("RTEST_1", MARCH)), FIRST_SYNC);

        assertThat(result).isEqualTo(new SyncResult(1, 0, 0, List.of("RTEST_NONE")));
    }

    private static ProductRounds entry(String productCode, Round... rounds) {
        return new ProductRounds(productCode, List.of(rounds), null);
    }

    private void insertProduct(String code) {
        jdbcTemplate.update("""
                INSERT INTO product (source_id, type, product_code, product_name, content)
                VALUES ((SELECT id FROM product_source WHERE code = 'ONTONG'), 'POLICY', ?, ?, '')
                """, code, code);
    }

    private List<String> rounds(String productCode) {
        return jdbcTemplate.query("""
                        SELECT r.round_no, r.apply_start_date, r.apply_end_date, r.source_url, r.schedule_changed_at
                        FROM policy_product_round r JOIN product p ON p.id = r.product_id
                        WHERE p.product_code = ? ORDER BY r.round_no
                        """,
                (rs, i) -> rs.getInt(1) + ":" + rs.getDate(2) + "~" + rs.getDate(3) + ":" + rs.getString(4) + ":"
                        + (rs.getTimestamp(5) == null ? "null" : "changed"),
                productCode);
    }

    private void deleteProducts() {
        jdbcTemplate.update("DELETE FROM product WHERE product_code LIKE 'RTEST_%'");
    }
}
