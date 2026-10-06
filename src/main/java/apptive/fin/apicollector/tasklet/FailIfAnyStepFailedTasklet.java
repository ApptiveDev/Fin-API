package apptive.fin.apicollector.tasklet;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 같은 실행에서 앞서 실패한 step이 있으면 잡을 FAILED로 끝낸다.
 *
 * <p>ALL 실행은 KFB 수집이 실패해도 나머지 소스를 끝까지 돌리도록 실패 전이를 건너뛰는데(allSyncFlow),
 * 그대로 두면 잡이 COMPLETED로 끝나 실패가 묻힌다. 마지막 step으로 두어 실패를 잡 상태에 드러낸다.
 * 실패 뒤 흐름이 이어진 step은 BatchStatus가 ABANDONED로 바뀌고 ExitStatus만 FAILED로 남으므로 ExitStatus로 본다.
 */
@Component
public class FailIfAnyStepFailedTasklet implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<String> failedSteps = chunkContext.getStepContext().getStepExecution().getJobExecution()
                .getStepExecutions().stream()
                .filter(stepExecution -> ExitStatus.FAILED.getExitCode().equals(stepExecution.getExitStatus().getExitCode()))
                .map(StepExecution::getStepName)
                .toList();

        if (!failedSteps.isEmpty()) {
            throw new IllegalStateException("Earlier steps failed in this run. steps=" + failedSteps);
        }
        return RepeatStatus.FINISHED;
    }
}
