package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.bankurl.BankProductUrlPersistenceService;
import apptive.fin.apicollector.bankurl.BankProductUrlProperties;
import apptive.fin.apicollector.bankurl.BankProductUrlRepository;
import apptive.fin.apicollector.bankurl.BankProductUrlTarget;
import apptive.fin.apicollector.bankurl.ScrapeResult;
import apptive.fin.apicollector.bankurl.ScrapeStatus;
import apptive.fin.apicollector.bankurl.runner.BankProductUrlScrapeService;
import apptive.fin.apicollector.config.CollectorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankProductUrlTasklet implements Tasklet {

    private final CollectorProperties collectorProperties;
    private final BankProductUrlProperties properties;
    private final BankProductUrlRepository repository;
    private final BankProductUrlScrapeService scrapeService;
    private final BankProductUrlPersistenceService persistenceService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (collectorProperties.mode().isNormalizeOnly()) {
            log.info("BankProductUrlTasklet skipped. mode={}", collectorProperties.mode());
            return RepeatStatus.FINISHED;
        }
        if (!properties.enabled()) {
            log.info("BankProductUrlTasklet skipped. enabled=false");
            return RepeatStatus.FINISHED;
        }

        long started = System.nanoTime();
        List<BankProductUrlTarget> targets = repository.findActiveTargets(targetSourceCodes());
        List<ScrapeResult> results = scrapeService.scrape(targets);
        int updatedRows = persistenceService.applyPassedResults(results);

        long pass = count(results, ScrapeStatus.PASS);
        long warn = count(results, ScrapeStatus.WARN);
        long fail = count(results, ScrapeStatus.FAIL);
        log.info(
                "BankProductUrlTasklet finished. total={}, pass={}, warn={}, fail={}, updatedRows={}, elapsedMs={}",
                results.size(), pass, warn, fail, updatedRows, (System.nanoTime() - started) / 1_000_000
        );
        results.stream()
                .filter(result -> result.status() != ScrapeStatus.PASS)
                .forEach(result -> log.warn(
                        "Bank product URL {}. provider={}, product={}, candidate={}, url={}, similarity={}, error={}",
                        result.status(),
                        result.target().providerName(),
                        result.target().originalName(),
                        result.candidateName(),
                        result.productUrl(),
                        result.similarity(),
                        result.error()
                ));
        // 어떤 후보명과 비교해서 그 판정이 나왔는지가 없으면 원인 분석이 매번 막힌다.
        // PASS 까지 포함해 전부 남기되, 평소 로그를 덮지 않도록 DEBUG 로 둔다.
        if (log.isDebugEnabled()) {
            results.forEach(result -> log.debug(
                    "Bank product URL detail. status={}, provider={}, product={}, candidate={}, similarity={}",
                    result.status(),
                    result.target().providerName(),
                    result.target().originalName(),
                    result.candidateName(),
                    result.similarity()
            ));
        }
        return RepeatStatus.FINISHED;
    }

    // 은행 상품 링크가 필요한 소스. FSS는 API가 링크를 주지 않고, KFB는 공시 링크가 홈페이지·다른 상품·404인 경우가 있어
    // 두 소스 모두 은행 사이트에서 찾아 상품명까지 검증한 링크(PASS)로 덮는다.
    private List<String> targetSourceCodes() {
        return switch (collectorProperties.source()) {
            case ALL -> List.of(Source.FSS.name(), Source.KFB.name());
            case FSS, KFB -> List.of(collectorProperties.source().name());
            case ONTONG -> List.of();
        };
    }

    private long count(List<ScrapeResult> results, ScrapeStatus status) {
        return results.stream().filter(result -> result.status() == status).count();
    }
}
