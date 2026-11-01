package apptive.fin.apicollector.youthpolicy;

import apptive.fin.apicollector.youthpolicy.YouthPolicyDraft.RoundDates;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 필드 모양은 2026-11 온통청년 getPlcy 응답 기준
class YouthPolicyParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final YouthPolicyParser parser = new YouthPolicyParser();

    @Test
    void 특정기간_정책을_회차와_함께_읽는다() {
        YouthPolicyDraft draft = parser.parse(policy(Map.of(
                "aplyPrdSeCd", "0057001",
                "aplyYmd", "20260502 ~ 20260521",
                "sprtTrgtMinAge", "19",
                "sprtTrgtMaxAge", "34",
                "zipCd", "26110,26140"
        ))).orElseThrow();

        assertThat(draft.policyNo()).isEqualTo("20260101000000000001");
        assertThat(draft.name()).isEqualTo("청년 자산형성 지원");
        assertThat(draft.institution()).isEqualTo("부산광역시");
        assertThat(draft.summary()).isEqualTo("월 10만 원 저축 시 1:1 매칭");
        assertThat(draft.applyPeriodType()).isEqualTo(ApplyPeriodType.PERIOD);
        assertThat(draft.rounds()).containsExactly(
                new RoundDates(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21)));
        assertThat(draft.minAge()).isEqualTo(19);
        assertThat(draft.maxAge()).isEqualTo(34);
        assertThat(draft.regionCodes()).isEqualTo("26110,26140");
    }

    // 온통청년은 여러 회차를 문자 그대로의 "\N"으로 잇는다.
    @Test
    void 여러_회차를_순서대로_나눈다() {
        YouthPolicyDraft draft = parser.parse(policy(Map.of(
                "aplyPrdSeCd", "0057001",
                "aplyYmd", "20260101 ~ 20261231\\N20270101 ~ 20271231"
        ))).orElseThrow();

        assertThat(draft.rounds()).containsExactly(
                new RoundDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                new RoundDates(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)));
    }

    @Test
    void 상시와_마감_정책은_회차가_없다() {
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "aplyYmd", ""))).orElseThrow())
                .satisfies(draft -> {
                    assertThat(draft.applyPeriodType()).isEqualTo(ApplyPeriodType.ALWAYS);
                    assertThat(draft.rounds()).isEmpty();
                });
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057003"))).orElseThrow().applyPeriodType())
                .isEqualTo(ApplyPeriodType.CLOSED);
    }

    // 읽을 수 없는 회차는 버리고 정책은 남긴다. 회차가 없으면 캘린더에 찍히지 않는다.
    @Test
    void 읽을_수_없는_신청기간은_회차로_만들지_않는다() {
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057001", "aplyYmd", "2026년 상반기"))).orElseThrow().rounds())
                .isEmpty();
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057001", "aplyYmd", "20260521 ~ 20260502"))).orElseThrow().rounds())
                .isEmpty();
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057001", "aplyYmd", "20260231 ~ 20260302"))).orElseThrow().rounds())
                .isEmpty();
    }

    @Test
    void 나이_0은_제한_없음이다() {
        YouthPolicyDraft draft = parser.parse(policy(Map.of(
                "aplyPrdSeCd", "0057002", "sprtTrgtMinAge", "0", "sprtTrgtMaxAge", "39"))).orElseThrow();

        assertThat(draft.minAge()).isNull();
        assertThat(draft.maxAge()).isEqualTo(39);
    }

    @Test
    void 금융지원_분류가_아니면_건너뛴다() {
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "mclsfNm", "취업")))).isEmpty();
    }

    // 분류가 여러 개면 쉼표로 이어져 온다.
    @Test
    void 여러_분류_중_금융지원이_있으면_포함한다() {
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "mclsfNm", "건강,취약계층 및 금융지원"))))
                .isPresent();
    }

    @Test
    void 정책번호나_이름이나_신청기간_구분이_없으면_건너뛴다() {
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "plcyNo", "")))).isEmpty();
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "plcyNm", " ")))).isEmpty();
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "9999999")))).isEmpty();
    }

    @Test
    void 주관기관이_없으면_등록기관을_쓴다() {
        Optional<YouthPolicyDraft> draft = parser.parse(policy(Map.of(
                "aplyPrdSeCd", "0057002", "sprvsnInstCdNm", "", "rgtrInstCdNm", "금융위원회")));

        assertThat(draft.orElseThrow().institution()).isEqualTo("금융위원회");
    }

    private static JsonNode policy(Map<String, String> overrides) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("plcyNo", "20260101000000000001");
        node.put("plcyNm", "청년 자산형성 지원");
        node.put("plcyExplnCn", "월 10만 원 저축 시 1:1 매칭");
        node.put("mclsfNm", "취약계층 및 금융지원");
        node.put("sprvsnInstCdNm", "부산광역시");
        node.put("rgtrInstCdNm", "부산광역시 청년정책과");
        node.put("sprtTrgtMinAge", "0");
        node.put("sprtTrgtMaxAge", "0");
        node.put("zipCd", "");
        node.put("aplyYmd", "");
        overrides.forEach(node::put);
        return node;
    }
}
