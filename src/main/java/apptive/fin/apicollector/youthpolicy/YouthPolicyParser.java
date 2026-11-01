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
 * 캘린더 범위는 B1 PRD의 2계층(청년 금융 지원 정책)으로, 온통청년 중분류 '취약계층 및 금융지원'만 받는다.
 */
@Slf4j
@Component
public class YouthPolicyParser {

    static final String FINANCE_CATEGORY = "취약계층 및 금융지원";

    // 여러 회차는 문자 그대로의 "\N"(역슬래시 + N)으로 이어져 온다. 예) "20260101 ~ 20261231\N20270101 ~ 20271231"
    private static final String ROUND_SEPARATOR = "\\N";
    private static final Pattern ROUND = Pattern.compile("^(\\d{8})\\s*~\\s*(\\d{8})$");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 캘린더 범위 밖이거나 필수 값이 없으면 empty. */
    public Optional<YouthPolicyDraft> parse(JsonNode raw) {
        String categories = JsonNodes.text(raw, "mclsfNm");
        if (categories == null || !categories.contains(FINANCE_CATEGORY)) {
            return Optional.empty();
        }

        String policyNo = JsonNodes.text(raw, "plcyNo");
        String name = JsonNodes.text(raw, "plcyNm");
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
