package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.global.util.JsonNodes;
import apptive.fin.apicollector.global.util.TextMatch;
import apptive.fin.apicollector.llm.LlmProductEnrichment;
import apptive.fin.apicollector.normalize.dto.PreferentialRateDraft;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.normalize.dto.RequiredKeywordDraft;
import apptive.fin.apicollector.normalize.extractor.PreferentialRateReducer;
import apptive.fin.apicollector.normalize.extractor.keywords.TermKeywords;
import apptive.fin.apicollector.product.ExtractionConfidence;
import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.product.RequiredKeywordEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM enrichment 결과를 정규화 draft에 병합한다(기존 값 우선, 소득/우대금리/필수키워드 규칙 적용).
 */
@Slf4j
@Component
public class LlmEnrichmentMerger {

    private static final String[] INCOME_IRRELEVANT_PHRASES = {
            "소득공제", "소득세", "금융소득종합과세", "소득이체"
    };
    private static final String[] INCOME_TOKENS = {"소득", "총급여", "연봉"};
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    // "50만원", "5천만원", "1억원", "1,000,000원" 같은 금액 표기. 단위가 있으면 "원"은 생략될 수 있다.
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(\\d[\\d,]*)\\s*(?:(억|천만|백만|십만|만|천)\\s*원?|원)");
    private static final Map<String, Long> AMOUNT_UNITS = Map.of(
            "억", 100_000_000L, "천만", 10_000_000L, "백만", 1_000_000L, "십만", 100_000L, "만", 10_000L, "천", 1_000L
    );
    private static final Pattern MIN_DEPOSIT_KEYWORD = Pattern.compile("가입|예치|최소|최저");
    private static final Pattern MIN_DEPOSIT_SUFFIX = Pattern.compile("\\s*이상");
    private static final Pattern MIN_DEPOSIT_PREFIX = Pattern.compile("(최소|최저)\\s*(가입)?\\s*(금액|한도)?\\s*[:：]?\\s*$");

    public ProductDraft merge(ProductDraft draft, LlmProductEnrichment enrichment) {
        List<ProductPropertyDraft> properties = new ArrayList<>();
        String eligibilityText = eligibilityText(draft);
        boolean incomeMentioned = mentionsIncome(draft.content(), eligibilityText);
        List<PreferentialRateDraft> enrichmentRates = draft.rawSource() == Source.KFB
                ? percentGroundedRates(enrichment.preferentialRates(), draft.content())
                : enrichment.preferentialRates();
        Set<Long> writtenAmounts = writtenAmounts(draft.content());
        for (ProductPropertyDraft property : draft.properties()) {
            properties.add(merge(
                    property, enrichment, enrichmentRates, eligibilityText, draft.type(), incomeMentioned, writtenAmounts, draft.content()
            ));
        }

        return draft.toBuilder()
                .contentSummary(JsonNodes.blankToNull(enrichment.summaryContent()) == null
                        ? draft.contentSummary()
                        : enrichment.summaryContent().trim())
                .properties(properties)
                .build();
    }

    // earnMaxAmt(연소득 상한)·earnPercent(소득기준 %)는 원문에 소득 요건 언급이 있을 때만 LLM 값을 신뢰한다.
    // LLM이 가입금액/예치한도 등을 소득 상한으로 착각해 채우는 오류를 원천 차단하기 위한 보수적 가드.
    // 1단계(공백 정규화): 원문이 "총 급여액", "소득 공제"처럼 띄어써도 토큰("총급여")·무관문구("소득공제")와
    //   일관되게 매칭되도록 공백을 모두 제거한다(부분문자열 매칭의 띄어쓰기 취약점 제거).
    // 2단계(무관문구 제거): "소득공제", "소득세", "금융소득종합과세", "소득이체" 등 소득요건과 무관한 문구를
    //   제거해 오수용(false positive)을 차단한다 - 이 문구만 있고 실제 소득요건이 없는데 통과하는 것을 방지.
    // 3단계: 남은 텍스트에 "소득", "총급여", "연봉" 중 하나라도 남아 있으면 소득요건 언급으로 인정한다
    //   (오거부 방지 - 총급여/연봉 표현도 소득요건으로 수용).
    private boolean mentionsIncome(String content, String eligibilityText) {
        return mentionsIncomeToken(content) || mentionsIncomeToken(eligibilityText);
    }

    private boolean mentionsIncomeToken(String value) {
        return TextMatch.containsAny(normalizeForIncomeMatch(value), INCOME_TOKENS);
    }

    private String normalizeForIncomeMatch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "");
        for (String phrase : INCOME_IRRELEVANT_PHRASES) {
            normalized = normalized.replace(phrase, "");
        }
        return normalized;
    }

    // KFB 공시 실측에서 LLM이 최고금리-기본금리처럼 원문에 없는 금리를 계산하거나, 숫자 없이 나열된 조건에
    // 금리를 지어내 배정하는 일이 반복됐다. 공시 본문에 "N%"로 적힌 숫자와 같은 금리만 받는다.
    // 숫자만 대조하므로 지어낸 금리가 다른 조건의 숫자와 우연히 같으면 통과한다.
    // FSS는 이 가드로 검증하지 않았으므로 적용하지 않는다.
    private List<PreferentialRateDraft> percentGroundedRates(List<PreferentialRateDraft> rates, String content) {
        Set<BigDecimal> writtenRates = new HashSet<>();
        Matcher matcher = PERCENT_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) {
            writtenRates.add(new BigDecimal(matcher.group(1)).stripTrailingZeros());
        }

        List<PreferentialRateDraft> result = new ArrayList<>();
        for (PreferentialRateDraft rate : rates) {
            if (writtenRates.contains(rate.rate().stripTrailingZeros())) {
                result.add(rate);
            }
            else {
                log.info("Dropping LLM preferential rate not written in disclosure. rate={}", rate);
            }
        }
        return List.copyOf(result);
    }

    // KFB 실측에서 LLM이 공시의 "50만원"을 500만으로 옮기거나(자릿수 오류), 구간이 없는 단일 금리 상품의
    // 예치 한도를 최고금리 적용 범위로 넣는 일이 있었다. 아래 중 하나라도 해당하면 LLM 범위를 버린다.
    // (1) 경계 금액이 공시 본문에 금액으로 적혀 있지 않다. (2) 기본금리와 최고금리가 같다(구간이 없다).
    // (3) 범위가 예치 한도와 똑같다(상한만 있고 그 값이 maxDepositAmount).
    private boolean isPlausibleLlmRange(ProductPropertyDraft property, LlmProductEnrichment enrichment, Set<Long> writtenAmounts) {
        Long min = enrichment.maxRateApplicableMinAmount();
        Long max = enrichment.maxRateApplicableMaxAmount();
        if (min == null && max == null) {
            return true;
        }
        if ((min != null && !writtenAmounts.contains(min)) || (max != null && !writtenAmounts.contains(max))) {
            log.info("Dropping LLM max-rate range not written in disclosure. min={}, max={}", min, max);
            return false;
        }
        if (property.baseRate() != null && property.maxRate() != null && property.baseRate().compareTo(property.maxRate()) == 0) {
            return false;
        }
        return !(min == null && max.equals(property.maxDepositAmount()));
    }

    private static Set<Long> writtenAmounts(String content) {
        Set<Long> amounts = new HashSet<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) {
            amounts.add(amountOf(matcher));
        }
        return amounts;
    }

    // KFB 실측에서 LLM이 잔액 구간의 시작 금액("예금잔액1원~")이나 지정금액("최소지정금액 :1천만원")을 최소 가입금액으로 넣었다.
    // 파킹은 공시에 최소 가입금액 문구로 적힌 금액만 받는다: "가입금액: 1만원 이상"처럼 가입·예치·최소·최저 문맥에서
    // 금액 뒤에 "이상"이 오거나, "최저 10만원", "가입 최저한도 : 100만원"처럼 최소·최저 바로 뒤에 금액이 온다.
    // (FSS 정기예금 LLM 값 36건으로 이 규칙이 맞는 값을 버리지 않는지 확인했다. FSS에는 적용하지 않는다.)
    private static Long writtenMinimumDeposit(LlmProductEnrichment enrichment, String content) {
        Long amount = enrichment.minDepositAmount();
        if (amount == null || content == null) {
            return null;
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(content);
        while (matcher.find()) {
            if (amountOf(matcher) != amount) {
                continue;
            }
            String around = content.substring(Math.max(0, matcher.start() - 20), Math.min(content.length(), matcher.end() + 20));
            boolean writtenAsAtLeast = MIN_DEPOSIT_SUFFIX.matcher(content).region(matcher.end(), content.length()).lookingAt()
                    && MIN_DEPOSIT_KEYWORD.matcher(around).find();
            if (writtenAsAtLeast || MIN_DEPOSIT_PREFIX.matcher(content.substring(0, matcher.start())).find()) {
                return amount;
            }
        }
        log.info("Dropping LLM minDepositAmount not written as minimum deposit. amount={}", amount);
        return null;
    }

    private static long amountOf(Matcher amountMatcher) {
        long number = Long.parseLong(amountMatcher.group(1).replace(",", ""));
        String unit = amountMatcher.group(2);
        return unit == null ? number : number * AMOUNT_UNITS.get(unit);
    }

    private ProductPropertyDraft merge(
            ProductPropertyDraft property,
            LlmProductEnrichment enrichment,
            List<PreferentialRateDraft> enrichmentRates,
            String eligibilityText,
            ProductType type,
            boolean incomeMentioned,
            Set<Long> writtenAmounts,
            String content
    ) {
        // 월 납입 개념은 적금(SAVING)에만 있다. min/maxMonthlyLimit은 월 납입액 전용 필드이므로 정기예금의 일시납
        // 가입금액이나 파킹통장의 예치한도가 잘못 채워지지 않도록 null로 강제한다(LLM 준수 여부와 무관하게 보장).
        boolean isSaving = type == ProductType.SAVING;
        boolean isDeposit = type == ProductType.DEPOSIT;
        boolean isParking = type == ProductType.PARKING;
        // 최고금리 적용 범위는 하한·상한을 한 쌍으로 다룬다. 공시 최고한도에서 정한 값(KfbLimitClassifier)이
        // 있으면 그 쌍을 쓰고, 없을 때만 LLM 쌍을 쓴다. 필드별로 섞으면 하한이 상한보다 큰 범위가 생길 수 있다.
        boolean hasRuleBasedRange = property.maxRateApplicableMinAmount() != null
                || property.maxRateApplicableMaxAmount() != null;
        boolean useLlmRange = isParking && !hasRuleBasedRange && isPlausibleLlmRange(property, enrichment, writtenAmounts);
        return property.toBuilder()
                .minMonthlyLimit(isSaving ? firstNonNull(property.minMonthlyLimit(), enrichment.minMonthlyLimit()) : null)
                .maxMonthlyLimit(isSaving ? firstNonNull(property.maxMonthlyLimit(), enrichment.maxMonthlyLimit()) : null)
                // minDepositAmount는 예금·파킹 컬럼이다. 해당 유형이면 결정적 값(수동입력) 우선, 없으면 LLM 값으로 채우고,
                // 그 밖의 유형이면 LLM이 채웠더라도 null로 강제한다(월 납입 가드와 대칭).
                .minDepositAmount(isDeposit
                        ? firstNonNull(property.minDepositAmount(), enrichment.minDepositAmount())
                        : isParking ? firstNonNull(property.minDepositAmount(), writtenMinimumDeposit(enrichment, content)) : null)
                .maxRateApplicableMinAmount(useLlmRange
                        ? enrichment.maxRateApplicableMinAmount()
                        : property.maxRateApplicableMinAmount())
                .maxRateApplicableMaxAmount(useLlmRange
                        ? enrichment.maxRateApplicableMaxAmount()
                        : property.maxRateApplicableMaxAmount())
                .minAge(firstNonNull(property.minAge(), enrichment.minAge()))
                .maxAge(firstNonNull(property.maxAge(), enrichment.maxAge()))
                .earnMaxAmt(firstNonNull(property.earnMaxAmt(), incomeMentioned ? enrichment.earnMaxAmt() : null))
                .earnPercent(firstNonNull(property.earnPercent(), incomeMentioned ? enrichment.earnPercent() : null))
                .govContributionRate(firstNonNull(property.govContributionRate(), enrichment.govContributionRate()))
                .govContributionType(firstNonNull(property.govContributionType(), enrichment.govContributionType()))
                .govMatchingRatio(firstNonNull(property.govMatchingRatio(), enrichment.govMatchingRatio()))
                .govMonthlyFixedContribution(firstNonNull(
                        property.govMonthlyFixedContribution(),
                        enrichment.govMonthlyFixedContribution()
                ))
                .govContributionPeriodMonths(firstNonNull(
                        property.govContributionPeriodMonths(),
                        enrichment.govContributionPeriodMonths()
                ))
                .excludeFromRateComparison(firstTrue(property.excludeFromRateComparison(), enrichment.excludeFromRateComparison()))
                .allowsMilitaryAgeExtension(firstTrue(
                        property.allowsMilitaryAgeExtension(),
                        enrichment.allowsMilitaryAgeExtension()
                ))
                .militaryMaxAge(firstNonNull(property.militaryMaxAge(), enrichment.militaryMaxAge()))
                .requiresHomeless(firstTrue(property.requiresHomeless(), enrichment.requiresHomeless()))
                .requiresHouseholder(firstTrue(property.requiresHouseholder(), enrichment.requiresHouseholder()))
                .keywords(mergeKeywords(property, enrichment))
                .requiredKeywords(mergeRequiredKeywords(
                        property.requiredKeywords(),
                        filteredRequiredKeywords(enrichment.requiredKeywords(), eligibilityText)
                ))
                .preferentialRates(mergePreferentialRates(property.preferentialRates(), enrichmentRates))
                .build();
    }

    private List<KeywordValueEnum> mergeKeywords(ProductPropertyDraft property, LlmProductEnrichment enrichment) {
        Set<KeywordValueEnum> keywords = EnumSet.noneOf(KeywordValueEnum.class);
        keywords.addAll(property.keywords());
        for (String keyword : enrichment.keywords()) {
            KeywordValueEnum keywordValue = KeywordValueEnum.from(keyword);
            // TERM_*는 saveTerm으로 별도 산출하고, BENEFIT_MAX_INTEREST는 정적 태깅하지 않는다
            // (최고이율은 검색 시점 동적 판정, PRD A-2). LLM이 넣어도 무시.
            if (keywordValue != null
                    && !keywordValue.name().startsWith("TERM_")
                    && keywordValue != KeywordValueEnum.BENEFIT_MAX_INTEREST) {
                keywords.add(keywordValue);
            }
        }

        if (property.saveTerm() != null) {
            keywords.add(TermKeywords.bucket(property.saveTerm()));
        }

        return List.copyOf(keywords);
    }

    private List<RequiredKeywordDraft> mergeRequiredKeywords(
            List<RequiredKeywordDraft> existing,
            List<RequiredKeywordDraft> enrichment
    ) {
        Map<String, RequiredKeywordDraft> merged = new LinkedHashMap<>();
        for (RequiredKeywordDraft draft : existing) {
            merged.put(requiredKeywordKey(draft), draft);
        }
        for (RequiredKeywordDraft draft : enrichment) {
            merged.put(requiredKeywordKey(draft), draft);
        }
        return List.copyOf(merged.values());
    }

    private List<RequiredKeywordDraft> filteredRequiredKeywords(
            List<RequiredKeywordDraft> enrichment,
            String eligibilityText
    ) {
        List<RequiredKeywordDraft> result = new ArrayList<>();
        for (RequiredKeywordDraft draft : enrichment) {
            if (draft.confidence() != ExtractionConfidence.HIGH) {
                log.info("Dropping low-confidence LLM required keyword. keyword={}", draft);
                continue;
            }
            if (!matchesRequiredKeyword(draft, eligibilityText)) {
                log.info("Dropping unsupported LLM required keyword. keyword={}, eligibilityText={}", draft, eligibilityText);
                continue;
            }
            result.add(draft);
        }
        return List.copyOf(result);
    }

    private boolean matchesRequiredKeyword(RequiredKeywordDraft draft, String eligibilityText) {
        if (eligibilityText == null || eligibilityText.isBlank()) {
            return false;
        }
        if (draft.effect() == RequiredKeywordEffect.EXCLUDE && !hasExcludeExpression(eligibilityText)) {
            return false;
        }

        return switch (draft.keywordCode()) {
            case STATUS_SME_WORKER -> TextMatch.containsAny(
                    eligibilityText,
                    "중소기업근로자",
                    "중기근로자",
                    "중소기업 재직",
                    "중소기업 재직자",
                    "중소기업 근로자",
                    "중소기업에 재직",
                    "중소기업 취업",
                    "중소기업 청년"
            );
            case STATUS_MILITARY -> TextMatch.containsAny(
                    eligibilityText,
                    "군인",
                    "장병",
                    "군 복무",
                    "군복무",
                    "병역복무자",
                    "군 복무자",
                    "군복무자"
            ) && !TextMatch.containsAny(eligibilityText, "병역 이행 기간", "병역이행기간", "나이 연장", "연령 연장");
            case STATUS_UNEMPLOYED -> TextMatch.containsAny(eligibilityText, "무직", "미취업", "구직자", "실업");
            case STATUS_PART_TIME -> TextMatch.containsAny(eligibilityText, "파트타임", "시간제", "단시간 근로", "단시간근로");
            default -> false;
        };
    }

    private boolean hasExcludeExpression(String value) {
        return TextMatch.containsAny(value, "제외", "가입 불가", "가입불가", "대상 아님", "대상아님", "불가능");
    }

    // 신분 조건 근거 문구. 정규화기가 원문의 가입대상·유의사항을 draft에 담아 두므로 소스와 무관하게 여기서 읽는다.
    private String eligibilityText(ProductDraft draft) {
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, draft.eligibilityText());
        addIfNotBlank(parts, draft.cautionText());
        return String.join(" ", parts);
    }

    private void addIfNotBlank(List<String> values, String value) {
        String normalized = JsonNodes.blankToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private String requiredKeywordKey(RequiredKeywordDraft draft) {
        return draft.keywordCode() + ":" + draft.effect();
    }

    private List<PreferentialRateDraft> mergePreferentialRates(
            List<PreferentialRateDraft> existing,
            List<PreferentialRateDraft> enrichment
    ) {
        // LLM이 우대금리를 하나라도 제시하면 LLM 결과를 authoritative로 사용한다(규칙추출과 union하지 않음).
        // 규칙·LLM이 같은 조건을 다른 문자열/코드로 내놓아 중복·이중분류(예: 마케팅동의가 BANK_MARKETING과
        // BANK_ONLINE_JOIN 양쪽에)되던 문제를 원천 제거. LLM이 하나도 못 뽑았을 때만 규칙추출(existing)을
        // 폴백으로 유지해 max_rate 정합성 safety net을 남긴다.
        List<PreferentialRateDraft> source = enrichment.isEmpty() ? existing : enrichment;
        return PreferentialRateReducer.reduce(source);
    }

    private <T> T firstNonNull(T existing, T enrichment) {
        return existing != null ? existing : enrichment;
    }

    private boolean firstTrue(Boolean existing, Boolean enrichment) {
        return Boolean.TRUE.equals(existing) || Boolean.TRUE.equals(enrichment);
    }
}
