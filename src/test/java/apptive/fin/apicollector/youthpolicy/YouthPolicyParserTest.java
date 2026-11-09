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
    void 이름에_저축_공제_신용회복_키워드가_있으면_포함한다() {
        assertThat(named("전북청년 함께 두배적금")).isPresent();
        assertThat(named("청년내일저축계좌")).isPresent();
        assertThat(named("광주형 청년일자리 공제")).isPresent();
        assertThat(named("청년근로자 사랑채움사업")).isPresent();
        assertThat(named("청년 특례 채무조정")).isPresent();
        assertThat(named("학자금 대출 신용 회복 지원")).isPresent();
        assertThat(named("2026년 청년 학자금대출 장기연체자 지원사업")).isPresent();
    }

    // 2026-11 실제 데이터에서 걸러야 했던 이름들
    @Test
    void 범위_밖_정책은_건너뛴다() {
        assertThat(named("출산육아수당 지원")).isEmpty();
        assertThat(named("청년·대학생 햇살론(햇살론유스)")).isEmpty();
        assertThat(named("소프트웨어(SW) 미래채움 사업")).isEmpty();
        assertThat(named("취업성공 디딤돌 청년인턴 사업")).isEmpty();
    }

    // PRD는 주거 정책을 뺀다. 저축·이자 지원이라도 주거 목적이면 제외한다.
    @Test
    void 주거_정책은_건너뛴다() {
        assertThat(named("청년주택드림청약통장")).isEmpty();
        assertThat(named("신혼부부 전세자금 대출이자 지원사업")).isEmpty();
        assertThat(named("신혼부부 임차보증금 이자지원")).isEmpty();
    }

    // 온통청년 분류와 관계없이 이름으로 고른다. 내일채움공제는 '재직자', 학자금 신용회복은 '교육비지원' 분류에 있다.
    @Test
    void 온통청년_분류와_관계없이_이름으로_고른다() {
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "plcyNm", "대구형 청년 그린 내일채움공제", "mclsfNm", "재직자"))))
                .isPresent();
        assertThat(parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "plcyNm", "취업청년 정착수당", "mclsfNm", "취약계층 및 금융지원"))))
                .isEmpty();
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

    private Optional<YouthPolicyDraft> named(String name) {
        return parser.parse(policy(Map.of("aplyPrdSeCd", "0057002", "plcyNm", name)));
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
