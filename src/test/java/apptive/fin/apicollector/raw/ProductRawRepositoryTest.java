package apptive.fin.apicollector.raw;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.llm.cache.LlmEnrichmentCache;
import apptive.fin.apicollector.llm.cache.LlmEnrichmentCacheRepository;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ProductRawRepositoryTest extends IntegrationTestSupport {

    private static final int NORMALIZER_VERSION = 5;
    private static final Set<Source> LLM_SOURCES = Set.of(Source.FSS, Source.KFB);

    @Autowired
    private ProductRawRepository repository;

    @Autowired
    private LlmEnrichmentCacheRepository cacheRepository;

    @Test
    void reselectsNormalizedLlmSourceRawWithoutSuccessCache() {
        ProductRaw kfbWithoutCache = normalizedRaw(Source.KFB, "KFB:PARKING:TEST:캐시없음", ProductType.PARKING);
        ProductRaw kfbWithCache = normalizedRaw(Source.KFB, "KFB:PARKING:TEST:캐시있음", ProductType.PARKING);
        ProductRaw ontong = normalizedRaw(Source.ONTONG, "ONTONG:TEST", ProductType.SAVING);
        saveSuccessCache(kfbWithCache);

        List<Long> selected = findNextNeedNormalize(true);

        assertThat(selected)
                .contains(kfbWithoutCache.getId())
                .doesNotContain(kfbWithCache.getId(), ontong.getId());
    }

    @Test
    void doesNotReselectNormalizedRawWhenLlmIsDisabled() {
        ProductRaw kfbWithoutCache = normalizedRaw(Source.KFB, "KFB:PARKING:TEST:캐시없음", ProductType.PARKING);

        assertThat(findNextNeedNormalize(false)).doesNotContain(kfbWithoutCache.getId());
    }

    private List<Long> findNextNeedNormalize(boolean llmEnabled) {
        return repository.findNextNeedNormalize(
                List.of(Source.FSS, Source.ONTONG, Source.KFB),
                0L,
                NORMALIZER_VERSION,
                llmEnabled,
                LLM_SOURCES,
                "GEMINI",
                "gemini-test",
                1,
                1,
                PageRequest.of(0, 100)
        ).stream().map(ProductRaw::getId).toList();
    }

    private ProductRaw normalizedRaw(Source source, String externalId, ProductType type) {
        ProductRaw raw = new ProductRaw(source, externalId, "hash", "{}", type);
        raw.markNormalized(NORMALIZER_VERSION);
        return repository.saveAndFlush(raw);
    }

    private void saveSuccessCache(ProductRaw raw) {
        LlmEnrichmentCache cache = LlmEnrichmentCache.create(
                raw.getSource(), raw.getExternalId(), raw.getContentHash(), "GEMINI", "gemini-test", 1, 1, "request-hash"
        );
        cache.markSuccess("request-hash", "{}");
        cacheRepository.saveAndFlush(cache);
    }
}
