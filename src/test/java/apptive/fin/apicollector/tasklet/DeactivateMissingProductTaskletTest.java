package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.product.service.ProductSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DeactivateMissingProductTaskletTest {

    private final ProductSyncService productSyncService = mock(ProductSyncService.class);

    @Test
    void skipsWhenModeIsNormalizeOnly() {
        DeactivateMissingProductTasklet tasklet = new DeactivateMissingProductTasklet(
                productSyncService,
                properties(Source.ALL, Mode.NORMALIZE_ONLY, 3)
        );

        RepeatStatus result = tasklet.execute(null, chunkContext());

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verifyNoInteractions(productSyncService);
    }

    @Test
    void deactivatesOntongYouthOnlyWhenSourceIsOntongYouth() {
        DeactivateMissingProductTasklet tasklet = new DeactivateMissingProductTasklet(
                productSyncService,
                properties(Source.ONTONG, Mode.SYNC, 7)
        );

        Instant before = Instant.now().minusSeconds(1);
        RepeatStatus result = tasklet.execute(null, chunkContext());
        Instant after = Instant.now().plusSeconds(1);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.ONTONG),
                thresholdCaptor.capture()
        );
        verify(productSyncService, never()).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.FSS),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(thresholdCaptor.getValue())
                .isBetween(before.minusSeconds(7 * 24 * 60 * 60), after.minusSeconds(7 * 24 * 60 * 60));
    }

    @Test
    void deactivatesFssOnlyWhenSourceIsFss() {
        DeactivateMissingProductTasklet tasklet = new DeactivateMissingProductTasklet(
                productSyncService,
                properties(Source.FSS, Mode.SYNC, 7)
        );

        RepeatStatus result = tasklet.execute(null, chunkContext());

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.FSS),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService, never()).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.ONTONG),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void deactivatesAllSourcesWhenSourceIsAll() {
        DeactivateMissingProductTasklet tasklet = new DeactivateMissingProductTasklet(
                productSyncService,
                properties(Source.ALL, Mode.SYNC, 7)
        );

        RepeatStatus result = tasklet.execute(null, chunkContext());

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.ONTONG),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.FSS),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.KFB),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void deactivatesKfbOnlyWhenSourceIsKfb() {
        DeactivateMissingProductTasklet tasklet = new DeactivateMissingProductTasklet(
                productSyncService,
                properties(Source.KFB, Mode.SYNC, 7)
        );

        RepeatStatus result = tasklet.execute(null, chunkContext());

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.KFB),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService, never()).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.FSS),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService, never()).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.ONTONG),
                org.mockito.ArgumentMatchers.any()
        );
    }

    // ALL 실행에서 KFB 수집이 실패하면 KFB raw의 lastSeen이 그대로라, 비활성화하면 KFB가 전부 꺼진다.
    @Test
    void skipsKfbButDeactivatesOthersWhenKfbFetchFailedInThisRun() {
        DeactivateMissingProductTasklet tasklet = new DeactivateMissingProductTasklet(
                productSyncService,
                properties(Source.ALL, Mode.SYNC, 7)
        );

        RepeatStatus result = tasklet.execute(null, chunkContext(FetchKfbRawTasklet.STEP_NAME));

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.ONTONG),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.FSS),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productSyncService, never()).disableAllUnseenProducts(
                org.mockito.ArgumentMatchers.eq(Source.KFB),
                org.mockito.ArgumentMatchers.any()
        );
    }

    // 이번 실행의 JobExecution에 failedSteps 이름의 실패한 step을 넣은 채 이 step의 ChunkContext를 만든다.
    // 실패 뒤 흐름이 이어진 step은 실제로 ABANDONED + ExitStatus FAILED가 된다(AllSyncFlowTest에서 확인).
    private static ChunkContext chunkContext(String... failedSteps) {
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        long id = 1;
        for (String failedStep : failedSteps) {
            StepExecution failed = new StepExecution(id++, failedStep, jobExecution);
            failed.setStatus(BatchStatus.ABANDONED);
            failed.setExitStatus(ExitStatus.FAILED);
            jobExecution.addStepExecution(failed);
        }
        StepExecution current = new StepExecution(id, "deactivateMissingProductStep", jobExecution);
        jobExecution.addStepExecution(current);
        return new ChunkContext(new StepContext(current));
    }

    private CollectorProperties properties(Source source, Mode mode, int unseenDisablePeriod) {
        return new CollectorProperties(
                true,
                source,
                mode,
                1,
                100,
                unseenDisablePeriod,
                null,
                null,
                new CollectorProperties.Llm(false, "GEMINI", "gemini-test", 1, 1, 10, 3, 0.1, "http://localhost", "")
        );
    }
}
