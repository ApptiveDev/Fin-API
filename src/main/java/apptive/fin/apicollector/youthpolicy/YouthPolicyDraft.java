package apptive.fin.apicollector.youthpolicy;

import java.time.LocalDate;
import java.util.List;

/** 온통청년 정책 하나를 저장할 값으로 바꾼 것. */
public record YouthPolicyDraft(
        String policyNo,
        String name,
        String institution,
        String summary,
        Integer minAge,
        Integer maxAge,
        String regionCodes,
        ApplyPeriodType applyPeriodType,
        List<RoundDates> rounds
) {
    public record RoundDates(LocalDate start, LocalDate end) {
    }
}
