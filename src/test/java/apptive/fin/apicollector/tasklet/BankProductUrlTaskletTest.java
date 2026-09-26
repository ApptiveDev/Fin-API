package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.bankurl.BankProductUrlPersistenceService;
import apptive.fin.apicollector.bankurl.BankProductUrlProperties;
import apptive.fin.apicollector.bankurl.BankProductUrlRepository;
import apptive.fin.apicollector.bankurl.BankProductUrlTarget;
import apptive.fin.apicollector.bankurl.ScrapeResult;
import apptive.fin.apicollector.bankurl.ScrapeStatus;
import apptive.fin.apicollector.bankurl.runner.BankProductUrlScrapeService;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.product.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BankProductUrlTaskletTest {

    private final BankProductUrlRepository repository = mock(BankProductUrlRepository.class);
    private final BankProductUrlScrapeService scrapeService = mock(BankProductUrlScrapeService.class);
    private final BankProductUrlPersistenceService persistenceService = mock(BankProductUrlPersistenceService.class);

    @Test
    void normalizeOnlyModeSkipsAllUrlWork() {
        BankProductUrlTasklet tasklet = new BankProductUrlTasklet(
                collectorProperties(Mode.NORMALIZE_ONLY),
                new BankProductUrlProperties(true, 4, 90, 1),
                repository,
                scrapeService,
                persistenceService
        );

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verifyNoInteractions(repository, scrapeService, persistenceService);
    }

    @Test
    void disabledCollectorSkipsAllWork() {
        BankProductUrlTasklet tasklet = new BankProductUrlTasklet(
                collectorProperties(Mode.SYNC),
                new BankProductUrlProperties(false, 4, 90, 1),
                repository,
                scrapeService,
                persistenceService
        );

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verifyNoInteractions(repository, scrapeService, persistenceService);
    }

    @Test
    void enabledCollectorScrapesTargetsAndPersistsPassedResults() {
        BankProductUrlTarget target = new BankProductUrlTarget(
                1L, "P1", "테스트정기예금", ProductType.DEPOSIT, "TEST", "테스트은행"
        );
        ScrapeResult result = new ScrapeResult(
                target, "FakeScraper", ScrapeStatus.PASS, target.originalName(),
                "https://example.com/product", 1.0, "", 10, 1
        );
        when(repository.findActiveTargets(List.of("FSS"))).thenReturn(List.of(target));
        when(scrapeService.scrape(List.of(target))).thenReturn(List.of(result));
        when(persistenceService.applyPassedResults(List.of(result))).thenReturn(2);
        BankProductUrlTasklet tasklet = new BankProductUrlTasklet(
                collectorProperties(Mode.SYNC),
                new BankProductUrlProperties(true, 4, 90, 1),
                repository,
                scrapeService,
                persistenceService
        );

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(persistenceService).applyPassedResults(List.of(result));
    }

    // 은행연합회(KFB) 공시 링크는 홈페이지·다른 상품·404인 경우가 있어, FSS처럼 은행 사이트에서 찾아 검증한 링크로 덮는다.
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("targetSourcesByCollectorSource")
    void scrapesProductsOfSourcesCoveredByCurrentRun(Source source, List<String> expectedSourceCodes) {
        BankProductUrlTasklet tasklet = new BankProductUrlTasklet(
                collectorProperties(source, Mode.SYNC),
                new BankProductUrlProperties(true, 4, 90, 1),
                repository,
                scrapeService,
                persistenceService
        );

        tasklet.execute(null, null);

        verify(repository).findActiveTargets(expectedSourceCodes);
    }

    static Stream<Arguments> targetSourcesByCollectorSource() {
        return Stream.of(
                Arguments.of(Source.FSS, List.of("FSS")),
                Arguments.of(Source.KFB, List.of("KFB")),
                Arguments.of(Source.ALL, List.of("FSS", "KFB"))
        );
    }

    private CollectorProperties collectorProperties(Mode mode) {
        return collectorProperties(Source.FSS, mode);
    }

    private CollectorProperties collectorProperties(Source source, Mode mode) {
        return new CollectorProperties(
                true, source, mode, 1, 100, 30,
                null, null, null
        );
    }
}
