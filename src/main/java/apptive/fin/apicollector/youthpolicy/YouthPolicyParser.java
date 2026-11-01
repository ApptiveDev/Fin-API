package apptive.fin.apicollector.youthpolicy;

import apptive.fin.apicollector.global.util.JsonNodes;
import apptive.fin.apicollector.youthpolicy.YouthPolicyDraft.RoundDates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 온통청년 정책(getPlcy 응답 항목)을 캘린더용 값으로 바꾼다.
 * 캘린더 범위는 B1 PRD의 2계층 '청년 금융 지원 정책(자산형성 저축·공제·신용회복)'이고, 주거 정책은 제외한다.
 * 정책 이름으로 고른다. 온통청년 분류로 먼저 거르면 내일채움공제(재직자)와 학자금 신용회복(교육비지원)이 빠지고,
 * '취약계층 및 금융지원' 분류에는 출산지원금, 돌봄처럼 금융과 먼 정책이 70% 넘게 섞여 있다.
 */
@Slf4j
@Component
public class YouthPolicyParser {

    // 키워드는 2026-11 온통청년 전체(3,843건)를 검토해 정했다. 온통청년 키워드 태그(plcyKywdNm)는
    // '금리혜택'이 교통비 환급에 붙는 식으로 부정확해서 쓰지 않는다.
    static final Pattern SCOPE_NAME = Pattern.compile(
            "저축|적금|통장|자산형성|재형|도약계좌"              // 자산형성 저축
                    + "|공제|사랑채움"                          // 공제 (경북 사랑채움은 이름에 '공제'가 없다)
                    + "|신용회복|신용 회복|채무조정|장기연체|부실채무|신용유의"); // 신용회복
    // 세금 공제와 주거 정책(청약통장, 전세·임차 대출이자 등)은 범위 밖이다.
    static final Pattern EXCLUDED_NAME = Pattern.compile("소득공제|세액공제|전세|월세|주거|주택|임대|임차|보금자리|청약");

    // 여러 회차는 문자 그대로의 "\N"(역슬래시 + N)으로 이어져 온다. 예) "20260101 ~ 20261231\N20270101 ~ 20271231"
    private static final String ROUND_SEPARATOR = "\\N";
    private static final Pattern ROUND = Pattern.compile("^(\\d{8})\\s*~\\s*(\\d{8})$");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 캘린더 범위 밖이거나 필수 값이 없으면 empty. */
    public Optional<YouthPolicyDraft> parse(JsonNode raw) {
        String name = JsonNodes.text(raw, "plcyNm");
        if (name != null && (!SCOPE_NAME.matcher(name).find() || EXCLUDED_NAME.matcher(name).find())) {
            return Optional.empty();
        }

        String policyNo = JsonNodes.text(raw, "plcyNo");
        String periodCode = JsonNodes.text(raw, "aplyPrdSeCd");
        ApplyPeriodType applyPeriodType = ApplyPeriodType.fromOntongCode(periodCode);
        if (policyNo == null || name == null || applyPeriodType == null) {
            log.warn("YouthPolicyParser: skipped. plcyNo={}, plcyNm={}, aplyPrdSeCd={}", policyNo, name, periodCode);
            return Optional.empty();
        }

        List<RoundDates> rounds = applyPeriodType == ApplyPeriodType.PERIOD
                ? parseRounds(policyNo, JsonNodes.text(raw, "aplyYmd"))
                : List.of();

        return Optional.of(new YouthPolicyDraft(
                policyNo,
                name,
                JsonNodes.firstText(raw, "sprvsnInstCdNm", "rgtrInstCdNm", "operInstCdNm"),
                JsonNodes.text(raw, "plcyExplnCn"),
                ageOrNull(JsonNodes.integer(raw, "sprtTrgtMinAge")),
                ageOrNull(JsonNodes.integer(raw, "sprtTrgtMaxAge")),
                JsonNodes.text(raw, "zipCd"),
                applyPeriodType,
                rounds
        ));
    }

    // 읽을 수 없는 회차는 버린다. 회차가 하나도 없는 특정기간 정책은 캘린더에 나오지 않는다.
    private List<RoundDates> parseRounds(String policyNo, String aplyYmd) {
        if (aplyYmd == null) {
            log.warn("YouthPolicyParser: no apply period. plcyNo={}", policyNo);
            return List.of();
        }
        List<RoundDates> rounds = new ArrayList<>();
        for (String part : aplyYmd.split(Pattern.quote(ROUND_SEPARATOR))) {
            Optional<RoundDates> round = parseRound(part.trim());
            if (round.isPresent()) {
                rounds.add(round.get());
            }
            else {
                log.warn("YouthPolicyParser: unreadable apply period. plcyNo={}, aplyYmd={}", policyNo, part);
            }
        }
        return rounds;
    }

    private static Optional<RoundDates> parseRound(String value) {
        Matcher matcher = ROUND.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            LocalDate start = LocalDate.parse(matcher.group(1), YMD);
            LocalDate end = LocalDate.parse(matcher.group(2), YMD);
            return start.isAfter(end) ? Optional.empty() : Optional.of(new RoundDates(start, end));
        }
        catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    // 온통청년은 나이 제한 없음을 0으로 준다.
    private static Integer ageOrNull(Integer age) {
        return age == null || age == 0 ? null : age;
    }
}
