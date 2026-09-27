package apptive.fin.apicollector.tasklet;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailIfAnyStepFailedTaskletTest {

    private final FailIfAnyStepFailedTasklet tasklet = new FailIfAnyStepFailedTasklet();

    @Test
    void failsWithFailedStepNames() {
        ChunkContext context = chunkContext(
                step("fetchFssRawStep", BatchStatus.COMPLETED, ExitStatus.COMPLETED),
                // 실패 뒤 흐름이 이어진 step의 실제 상태(AllSyncFlowTest에서 확인)
                step("fetchKfbRawStep", BatchStatus.ABANDONED, ExitStatus.FAILED)
        );

        assertThatThrownBy(() -> tasklet.execute(null, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fetchKfbRawStep")
                .hasMessageNotContaining("fetchFssRawStep");
    }

    @Test
    void finishesWhenNoStepFailed() {
        ChunkContext context = chunkContext(step("fetchKfbRawStep", BatchStatus.COMPLETED, ExitStatus.COMPLETED));

        assertThat(tasklet.execute(null, context)).isEqualTo(RepeatStatus.FINISHED);
    }

    private record StepState(String name, BatchStatus status, ExitStatus exitStatus) {
    }

    private static StepState step(String name, BatchStatus status, ExitStatus exitStatus) {
        return new StepState(name, status, exitStatus);
    }

    private static ChunkContext chunkContext(StepState... earlierSteps) {
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        long id = 1;
        for (StepState earlier : earlierSteps) {
            StepExecution execution = new StepExecution(id++, earlier.name(), jobExecution);
            execution.setStatus(earlier.status());
            execution.setExitStatus(earlier.exitStatus());
            jobExecution.addStepExecution(execution);
        }
        StepExecution current = new StepExecution(id, "failIfAnyStepFailedStep", jobExecution);
        current.setStatus(BatchStatus.STARTED);
        jobExecution.addStepExecution(current);
        return new ChunkContext(new StepContext(current));
    }
}
