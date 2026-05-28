package apptive.fin.apicollector.product.service;

import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.repository.ProductPropertyRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HighInterestKeywordService {

    private static final KeywordValueEnum HIGH_INTEREST_KEYWORD = KeywordValueEnum.BENEFIT_MAX_INTEREST;

    private final ProductPropertyRepository productPropertyRepository;

    @Transactional
    public HighInterestKeywordUpdateResult refreshHighInterestKeywords() {
        List<BigDecimal> rates = productPropertyRepository.findJoinableMaxRatesOrderByMaxRate();
        BigDecimal criteria = calculatePercentile(rates, 0.3);
        String keywordCode = HIGH_INTEREST_KEYWORD.name();

        if (criteria == null) {
            int removed = productPropertyRepository.deleteHighInterestKeywords(keywordCode);
            return new HighInterestKeywordUpdateResult(criteria, rates.size(), 0, removed);
        }

        int removed = productPropertyRepository.deleteHighInterestKeywordsNotExceedingMedian(keywordCode, criteria);
        int added = productPropertyRepository.insertMissingHighInterestKeywords(keywordCode, criteria);

        return new HighInterestKeywordUpdateResult(criteria, rates.size(), added, removed);
    }

    private @Nullable BigDecimal calculateMedian(List<BigDecimal> sortedRates) {
        if (sortedRates.isEmpty()) {
            return null;
        }

        int size = sortedRates.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedRates.get(middle);
        }

        return sortedRates.get(middle - 1)
                .add(sortedRates.get(middle))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private @Nullable BigDecimal calculatePercentile(List<BigDecimal> sortedRates, double percentile) {
        if (sortedRates.isEmpty()) {
            return null;
        }

        int size = sortedRates.size();
        int pos = (int) Math.ceil(size * percentile);
        return sortedRates.get(pos - 1);
    }

    public record HighInterestKeywordUpdateResult(
            @Nullable BigDecimal median,
            int rateCount,
            int addedCount,
            int removedCount
    ) {
    }
}
