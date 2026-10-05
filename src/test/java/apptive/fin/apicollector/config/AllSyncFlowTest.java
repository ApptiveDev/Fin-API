package apptive.fin.apicollector.config;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.product.service.ProductSyncService;
import apptive.fin.apicollector.support.IntegrationTestSupport;
import apptive.fin.apicollector.tasklet.DeactivateMissingProductTasklet;
import apptive.fin.apicollector.tasklet.FailIfAnyStepFailedTasklet;
import apptive.fin.apicollector.tasklet.FetchKfbRawTasklet;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * allSyncFlow의 전이만 검증한다. 각 step은 실행 기록용 stub이고, 비활성화 step만 실제 tasklet(서비스는 mock)을 써서
 * 실제 JobRepository가 만든 JobExecution으로 KFB 실패를 알아보는지 확인한다.
 */
class AllSyncFlowTest extends IntegrationTestSupport {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> ran = new CopyOnWriteArrayList<>();
    private final ProductSyncService productSyncService = mock(ProductSyncService.class);

    @Test
    void runsOtherSourcesAndFailsJobWhenKfbFetchFails() throws Exception {
        JobExecution execution = run(step(FetchKfbRawTasklet.STEP_NAME, (contribution, context) -> {
            throw new IllegalStateException("KFB portal down");
        }));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(ran).containsExactly(
                "fetchManualRawStep",
                "fetchFssRawStep",
                "normalizeOntongRawProductStep",
                "normalizeFssRawProductStep",
                "normalizeKfbRawProductStep",
                "bankProductUrlStep",
                "resolveProductDisplayNameStep"
        );
        verify(productSyncService).disableAllUnseenProducts(eq(Source.ONTONG), any());
        verify(productSyncService).disableAllUnseenProducts(eq(Source.FSS), any());
        verify(productSyncService, never()).disableAllUnseenProducts(eq(Source.KFB), any());
    }

    @Test
    void completesAndDeactivatesKfbWhenEveryStepSucceeds() throws Exception {
        JobExecution execution = run(recording(FetchKfbRawTasklet.STEP_NAME));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(ran).contains(FetchKfbRawTasklet.STEP_NAME, "resolveProductDisplayNameStep");
        verify(productSyncService).disableAllUnseenProducts(eq(Source.KFB), any());
    }

    private JobExecution run(Step fetchKfbRawStep) throws Exception {
        Flow flow = new FinancialProductSyncJobConfig().allSyncFlow(
                recording("fetchManualRawStep"),
                recording("fetchFssRawStep"),
                fetchKfbRawStep,
                recording("normalizeOntongRawProductStep"),
                recording("normalizeFssRawProductStep"),
                recording("normalizeKfbRawProductStep"),
                step("deactivateMissingProductStep", new DeactivateMissingProductTasklet(productSyncService, allSyncProperties())),
                recording("bankProductUrlStep"),
                recording("resolveProductDisplayNameStep"),
                step("failIfAnyStepFailedStep", new FailIfAnyStepFailedTasklet())
        );
        Job job = new JobBuilder("allSyncFlowTestJob", jobRepository).start(flow).end().build();
        return jobOperator.start(job, new JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters());
    }

    private Step recording(String name) {
        return step(name, (contribution, context) -> {
            ran.add(name);
            return RepeatStatus.FINISHED;
        });
    }

    private Step step(String name, Tasklet tasklet) {
        return new StepBuilder(name, jobRepository).tasklet(tasklet, transactionManager).build();
    }

    private static CollectorProperties allSyncProperties() {
        return new CollectorProperties(
                true,
                Source.ALL,
                Mode.SYNC,
                1,
                100,
                1,
                null,
                null,
                new CollectorProperties.Llm(false, "GEMINI", "gemini-test", Map.of(), Map.of(), 10, 3, 0.1, "http://localhost", "")
        );
    }
}
