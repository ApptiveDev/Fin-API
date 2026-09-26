package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.global.util.Sha256;
import apptive.fin.apicollector.llm.*;
import apptive.fin.apicollector.llm.cache.*;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.raw.ProductRaw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 상품 draft를 LLM으로 보강하는 오케스트레이터. 프롬프트 빌더가 등록된 소스(FSS, KFB)만 보강한다.
 * 프롬프트 생성/검증/병합/캐시는 각 협력자에 위임하고, 여기서는 흐름 제어와 배치 통계만 담당한다.
 */
@Slf4j
@Component
public class LlmProductDraftEnricher implements ProductDraftEnricher, StepExecutionListener {

    private static final Duration FAILED_RETRY_COOLDOWN = Duration.ofHours(6);

    private final CollectorProperties properties;
    private final List<LlmProviderClient> providerClients;
    private final Map<Source, EnrichmentPromptBuilder> promptBuilders;
    private final LlmEnrichmentValidator validator;
    private final LlmEnrichmentMerger merger;
    private final LlmEnrichmentCacheStore cacheStore;

    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private final AtomicInteger llmFailures = new AtomicInteger();
    private final AtomicInteger cooldownSkips = new AtomicInteger();
    private final AtomicInteger invalidCacheEntries = new AtomicInteger();

    public LlmProductDraftEnricher(
            CollectorProperties properties,
            List<LlmProviderClient> providerClients,
            List<EnrichmentPromptBuilder> promptBuilders,
            LlmEnrichmentValidator validator,
            LlmEnrichmentMerger merger,
            LlmEnrichmentCacheStore cacheStore
    ) {
        this.properties = properties;
        this.providerClients = providerClients;
        this.promptBuilders = bySource(promptBuilders);
        this.validator = validator;
        this.merger = merger;
        this.cacheStore = cacheStore;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        cacheHits.set(0);
        llmCalls.set(0);
        llmFailures.set(0);
        cooldownSkips.set(0);
        invalidCacheEntries.set(0);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info(
                "LLM enrichment summary. step={}, cacheHits={}, llmCalls={}, llmFailures={}, cooldownSkips={}, invalidCache={}",
                stepExecution.getStepName(),
                cacheHits.get(),
                llmCalls.get(),
                llmFailures.get(),
                cooldownSkips.get(),
                invalidCacheEntries.get()
        );
        return null;
    }

    @Override
    public boolean supports(Source source) {
        return promptBuilders.containsKey(source);
    }

    /** LLM 보강 대상 소스. reader가 캐시 없는 raw를 다시 고를 때 같은 기준을 쓴다. */
    public Set<Source> supportedSources() {
        return promptBuilders.keySet();
    }

    @Override
    public ProductDraft enrich(ProductRaw rawProduct, ProductDraft draft) {
        if (!enabled() || !draft.shouldSaveProduct()) {
            return draft;
        }

        LlmProviderClient providerClient = providerClient();
        String prompt = promptBuilders.get(rawProduct.getSource()).build(rawProduct, draft);
        String requestHash = Sha256.hex(prompt);
        LlmEnrichmentCache cache = cacheStore.findOrCreate(rawProduct, requestHash);

        if (cache.getStatus() == LlmEnrichmentCacheStatus.SUCCESS
                && cache.getResponseJson() != null
                && requestHash.equals(cache.getRequestHash())) {
            cacheHits.incrementAndGet();
            return fromCache(cache, draft);
        }
        if (cache.isFailedRetryBlocked(Instant.now(), FAILED_RETRY_COOLDOWN)) {
            cooldownSkips.incrementAndGet();
            log.debug(
                    "Skipping LLM enrichment during failed retry cooldown. rawId={}, externalId={}, failureCount={}",
                    rawProduct.getId(),
                    rawProduct.getExternalId(),
                    cache.getFailureCount()
            );
            return draft;
        }

        Instant callStart = Instant.now();
        try {
            llmCalls.incrementAndGet();
            LlmProductEnrichment enrichment = providerClient.enrich(new LlmProductEnrichmentRequest(
                    properties.llm().model(),
                    prompt,
                    properties.llm().schemaVersion()
            ));
            validator.validate(enrichment);

            cacheStore.saveSuccess(cache, requestHash, enrichment);
            log.info(
                    "LLM enrichment call. externalId={}, durationMs={}, outcome=SUCCESS",
                    rawProduct.getExternalId(),
                    Duration.between(callStart, Instant.now()).toMillis()
            );
            return merger.merge(draft, enrichment);
        }
        catch (Exception e) {
            llmFailures.incrementAndGet();
            log.info(
                    "LLM enrichment call. externalId={}, durationMs={}, outcome=FAILED, exception={}",
                    rawProduct.getExternalId(),
                    Duration.between(callStart, Instant.now()).toMillis(),
                    e.getClass().getSimpleName()
            );
            log.warn("LLM enrichment failed. rawId={}, externalId={}", rawProduct.getId(), rawProduct.getExternalId(), e);
            cacheStore.saveFailed(cache, requestHash, truncate(e.getMessage()));
            return draft;
        }
    }

    private ProductDraft fromCache(LlmEnrichmentCache cache, ProductDraft draft) {
        try {
            LlmProductEnrichment enrichment = cacheStore.readEnrichment(cache);
            validator.validate(enrichment);
            return merger.merge(draft, enrichment);
        }
        catch (Exception e) {
            invalidCacheEntries.incrementAndGet();
            log.warn("LLM enrichment cache is invalid. cacheId={}", cache.getId(), e);
            return draft;
        }
    }

    private static Map<Source, EnrichmentPromptBuilder> bySource(List<EnrichmentPromptBuilder> builders) {
        Map<Source, EnrichmentPromptBuilder> result = new EnumMap<>(Source.class);
        for (EnrichmentPromptBuilder builder : builders) {
            result.put(builder.source(), builder);
        }
        return Map.copyOf(result);
    }

    private boolean enabled() {
        return properties.llm() != null
                && properties.llm().enabled()
                && properties.llm().apiKey() != null
                && !properties.llm().apiKey().isBlank();
    }

    private LlmProviderClient providerClient() {
        return providerClients.stream()
                .filter(client -> client.supports(properties.llm().provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported LLM provider: " + properties.llm().provider()));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
