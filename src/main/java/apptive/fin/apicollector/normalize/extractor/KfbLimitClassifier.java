package apptive.fin.apicollector.normalize.extractor;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 은행연합회 공시의 "최고한도"가 무엇의 한도인지 판정한다.
 *
 * <p>같은 칸에 예치 한도(세이프박스 1억, "가입금액: … 500만원 이하")와 우대금리 적용 한도
 * ("매일의 최종 잔액 중 5백만원 이하의 금액에 대해 우대금리 제공")가 섞여 들어온다.
 * 금액 바로 뒤에 우대금리가 오는 표현이 있으면 우대금리 적용 한도로 본다.
 */
@Component
public class KfbLimitClassifier {

    // 줄을 넘지 않게 제한해서, 구간별 금리 목록의 다른 줄에 있는 "우대금리"와 엮이지 않게 한다.
    private static final Pattern RATE_APPLIES_UP_TO_AMOUNT =
            Pattern.compile("원\\s*(이하|까지)[^\\n]{0,20}우대\\s*금리");

    public boolean isPreferentialRateLimit(String preferentialCondition, String etcNote) {
        return matches(preferentialCondition) || matches(etcNote);
    }

    private static boolean matches(String text) {
        return text != null && RATE_APPLIES_UP_TO_AMOUNT.matcher(text).find();
    }
}
