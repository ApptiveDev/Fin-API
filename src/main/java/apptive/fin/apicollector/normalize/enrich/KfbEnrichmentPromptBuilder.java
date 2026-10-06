package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.raw.ProductRaw;
import org.springframework.stereotype.Component;

/**
 * 은행연합회 입출금자유예금(파킹통장) LLM enrichment 요청 프롬프트를 생성한다.
 * 공시 우대조건 칸에는 잔액·예치기간 구간별 적용금리가 우대금리와 섞여 있어, 이를 가려내는 규칙이 핵심이다.
 * 우대금리 키워드 매핑·필수키워드 규칙은 FSS 프롬프트와 같은 기준을 쓴다.
 */
@Component
public class KfbEnrichmentPromptBuilder implements EnrichmentPromptBuilder {

    @Override
    public Source source() {
        return Source.KFB;
    }

    @Override
    public String build(ProductRaw rawProduct, ProductDraft draft) {
        return """
                은행연합회 입출금자유예금(파킹통장) 비교공시 원문 JSON을 보고 사용자 화면에 필요한 보강값만 추출해라.

                원문 필드: bankName=은행명, productName=상품명, baseRate=기본금리(%%), maxRate=최고금리(%%),
                interestPayment=이자지급방식, joinMethod=가입방법, preferentialCondition=우대조건,
                joinRestriction=가입제한, joinTarget=가입대상, etcNote=기타 유의사항, maxLimit=최고한도(원).

                규칙:
                - 응답은 schema에 맞는 JSON만 반환한다.
                - 원문에 명시되지 않은 값은 null 또는 false로 둔다.
                - 금리, 기간, 은행명, 상품명, 상품코드, 신청 URL은 생성하지 않는다.
                - productType은 PARKING(입출금이 자유로운 통장)이다. 월 납입·만기 개념이 없다.
                - minMonthlyLimit, maxMonthlyLimit, minDepositAmount는 항상 null로 둔다. 최고한도·예치한도·우대금리 적용 한도도 여기에 넣지 않는다.
                - 정부기여금(govContributionRate, govContributionType, govMatchingRatio, govMonthlyFixedContribution, govContributionPeriodMonths)은 항상 null로 둔다.
                - allowsMilitaryAgeExtension, excludeFromRateComparison, requiresHomeless, requiresHouseholder는 원문에 명시되지 않았으면 false로 둔다.
                - keywords에는 기간 키워드(TERM_*)를 넣지 않는다.
                - summaryContent는 마케팅 문구 없이 금리 구조, 우대조건, 가입대상, 유의사항을 짧게 정리한다.
                  잔액 구간별 금리처럼 preferentialRates에 넣지 않은 금리 정보는 summaryContent에 남긴다.

                우대금리(preferentialRates) 규칙:
                - preferentialRates에는 고객이 조건을 충족하면 기본금리에 더해지는 조건별 가산금리가 명시된 경우만 넣는다.
                - rate는 원문에서 그 조건 바로 옆에 적힌 숫자만 옮긴다.
                  baseRate·maxRate로 계산(예: 최고금리 - 기본금리)하거나, 조건들에 숫자를 나눠 배정하거나, 추정하지 않는다.
                  조건만 나열되고 조건별 숫자가 없으면(예: "1) 급여이체 2) 카드 결제실적 3) 자동이체 중 2개 이상 충족 시 제공") preferentialRates는 빈 배열이다.
                - 잔액 구간별 적용금리는 우대금리가 아니다. 넣지 않는다.
                  예시 원문: "금액구간별 금리 차등적용 Ⅰ.1천만원이하 : 1.50%% Ⅱ.1억원초과 : 0.10%%", "5천만원 이하분 : 2.00 5천만원 초과분 : 1.80", "5천만원 이하: 연 2.30%%"
                - 예치기간별 적용금리(예: "입금일로부터 30일까지 : 0.05%%")와 지정금액·보관함(박스, 딴주머니 등)의 기본금리도 우대금리가 아니다. 넣지 않는다.
                - 잔액·평균잔액·보관 금액이 유일한 조건인 항목은 넣지 않는다(예: "스마트박스 1억원 이상 우대이율 (0.5%%)", "이 통장 월평균 잔액이 50만원 이상인 경우").
                  반대로 첫거래·급여이체 같은 조건이 있고 그 요율만 잔액에 따라 나뉘면(예: "첫 거래 고객 (1억원미만 1.8%% / 1억원 이상 2.3%%)") 구간별 항목으로 넣는다.
                - 조건 없이 이 상품 가입자 전원에게 주는 보너스·이벤트 금리는 넣지 않는다(예: "보너스 금리 - 이 예금 가입 고객 연1.0%%", "이벤트기간 동안 가입한 예금잔액 30만원이하금액에 연2.60%%p 우대").
                  단, "첫 거래 고객 (보너스이율0.5%%)", "마케팅 동의 고객 이벤트이율 0.70%%"처럼 조건이 명시된 가산금리는 "보너스"·"이벤트"라는 단어가 있어도 해당 조건으로 넣는다.
                - "우대금리 제공", "우대조건 충족 시"처럼 구체적인 %%(%%p) 수치가 없는 항목은 이유를 설명하지 말고 그냥 제외한다.
                - "우대금리 최고 연 2.0%%", "최대 1.00%%"처럼 전체 우대금리의 합계/상한만 적은 안내 문구 아래에 개별 조건과 %%가 따로 나열돼 있으면, 안내 문구는 무시하고 개별 조건들만 각각 넣는다. 개별 조건 없이 합계/상한만 있으면 빈 배열로 둔다.
                - 하나의 조건 안에서 실적·금액 기준에 따라 요율이 여러 구간으로 나뉘면(예: 카드실적 50만원~100만원미만 연0.2%%, 100만원이상 연0.4%%), 구간마다 별도 항목으로 나누고 description에 기준을 적는다.
                  반드시 원문에서 그 구간 바로 옆에 적힌 숫자를 그대로 옮기며, 모든 구간에 같은 rate를 복사하지 않는다.
                - 우대금리가 잔액 일부(예: "2백만원 이하의 금액에 대해")에만 적용된다는 한도는 어느 필드에도 넣지 않고 description에만 적는다.
                - keywordCode는 원문의 우대조건 의미와 정확히 일치할 때만 선택한다. 비슷해 보인다는 이유로 끼워맞추지 않는다.
                - 허용되는 preferentialRates 매핑:
                  * BANK_CARD_USAGE: 카드 보유/사용/결제실적/전월결제 조건
                  * BANK_SALARY_TRANSFER: 급여/월급 이체 조건
                  * BANK_AUTO_TRANSFER: 자동이체 조건
                  * BANK_MARKETING: 마케팅/상품서비스/개인정보 수집이용 동의 조건. 모바일메시지/알림 수신동의도 여기에 포함한다.
                  * BANK_FIRST_TRANSACTION: 첫거래/최초거래/신규고객 조건. 가입 직전 일정 기간 해당 은행 입출금계좌를 보유하지 않은 고객도 신규고객이다.
                    이 키워드의 description에는 반드시 "첫거래" 또는 "신규고객"을 띄어쓰기 없이 포함한다(예: "첫거래 고객", "신규고객(가입 직전 1개월 원화 입출금계좌 미보유)").
                  * BANK_REDEPOSIT: 재예치/재가입 조건
                  * BANK_ONLINE_JOIN: 인터넷/모바일/비대면/온라인 가입 조건. 모바일메시지/알림 수신동의는 온라인 가입이 아니다.
                  * BANK_AGE: 특정 나이 구간 우대. 이 경우 minAge/maxAge를 반드시 채운다. 나이 구간을 특정할 수 없으면 BANK_ETC로 넣는다.
                  * BANK_ETC: 위 조건 중 어디에도 정확히 해당하지 않지만 조건별 가산금리가 명시된 우대금리(예: 연금수급, 교차거래, 제휴채널 신규, 고객등급)
                - 위 매핑으로 정확히 표현할 수 없는 우대금리는 BANK_ETC로 매핑한다.
                - 응답 전에 preferentialRates의 각 항목(특히 BANK_ETC)을 다시 확인하고, 아래 중 하나라도 해당하면 뺀다:
                  (1) 조건이 금액 구간·지정금액·예치기간·잔액 크기뿐이다. (2) 조건이 이 상품 가입 또는 이벤트 기간 가입뿐이다.
                  (3) rate가 원문에서 그 조건 옆에 %%로 적혀 있지 않다.

                가입조건 규칙:
                - minAge, maxAge는 joinTarget에 가입 가능 나이가 명시된 경우만 채운다(예: "만 14세 이상" → minAge=14).
                - requiredKeywords에는 가입 가능 여부를 제한하는 STATUS_* 필수/제외 조건만 넣는다.
                - requiredKeywords는 가입대상 문구에 신분 조건이 명시된 경우만 넣는다. 상품명, 은행명, 우대금리 조건, 급여이체 조건, 카드 실적 조건에서 추론하지 않는다.
                - "실명의 개인", "개인", "개인사업자 포함", "개인사업자 제외", "만 N세 이상" 같은 일반 가입 조건은 STATUS_*로 매핑하지 않는다.
                - "직장인", "급여", "급여이체"는 STATUS_SME_WORKER가 아니다.
                - "아이", "자녀", "미성년"은 STATUS_PART_TIME 또는 STATUS_UNEMPLOYED가 아니다.
                - requiredKeywords의 confidence가 HIGH가 아닐 정도로 불확실하면 항목을 만들지 말고 빈 배열로 둔다.
                - EXCLUDE는 "가입 불가", "제외", "대상 아님" 같은 배제 표현과 해당 신분이 같은 가입대상 문맥에 명시된 경우만 넣는다.
                - earnMaxAmt는 가입자격의 연소득 상한(소득요건)이 원문에 명시된 경우에만 채운다. 예치한도·최고한도 등 금액 한도는 earnMaxAmt에 절대 넣지 않는다.
                - earnPercent는 소득기준(예: 기준중위소득 대비 %%)이 원문에 명시된 경우에만 채운다.
                - 반드시 아래 JSON skeleton의 모든 top-level key를 포함한다. 모르는 값은 null, false, [] 중 schema에 맞는 기본값으로 둔다.

                JSON skeleton:
                {
                  "summaryContent": null,
                  "keywords": [],
                  "minMonthlyLimit": null,
                  "maxMonthlyLimit": null,
                  "minDepositAmount": null,
                  "minAge": null,
                  "maxAge": null,
                  "earnMaxAmt": null,
                  "earnPercent": null,
                  "requiresHomeless": false,
                  "requiresHouseholder": false,
                  "govContributionRate": null,
                  "govContributionType": null,
                  "govMatchingRatio": null,
                  "govMonthlyFixedContribution": null,
                  "govContributionPeriodMonths": null,
                  "excludeFromRateComparison": false,
                  "allowsMilitaryAgeExtension": false,
                  "militaryMaxAge": null,
                  "requiredKeywords": [],
                  "preferentialRates": []
                }

                현재 정규화 결과:
                productName=%s
                productType=%s
                content=%s

                KFB raw JSON:
                %s
                """.formatted(
                draft.productName(),
                draft.type(),
                draft.content(),
                rawProduct.getRawJson()
        );
    }
}
