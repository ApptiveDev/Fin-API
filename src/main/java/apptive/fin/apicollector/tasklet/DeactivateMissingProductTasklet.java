package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.product.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeactivateMissingProductTasklet implements Tasklet {

    private final ProductSyncService productSyncService;
    private final CollectorProperties properties;

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {

        if (properties.mode().isNormalizeOnly()) {
            log.info(
                    "DeactivateMissingProductTasklet skipped. source={}, mode={}",
                    properties.source(),
                    properties.mode()
            );
            return RepeatStatus.FINISHED;
        }


        Instant threshold = Instant.now().minus(properties.unseenDisablePeriod(), ChronoUnit.DAYS);

        if (properties.source() == Source.ALL || properties.source() == Source.ONTONG) {
            int ontongDeactivated = productSyncService.disableAllUnseenProducts(Source.ONTONG, threshold);
            log.info(
                    "DeactivateMissingProductTasklet: ontong={}",
                    ontongDeactivated
            );
        }

        if (properties.source() == Source.ALL || properties.source() == Source.FSS) {
            int fssDeactivated = productSyncService.disableAllUnseenProducts(Source.FSS, threshold);
            log.info(
                    "DeactivateMissingProductTasklet: fss={}",
                    fssDeactivated
            );

        }

        // ALL 실행은 KFB 수집이 실패해도 계속 진행한다. 이때 KFB raw의 lastSeen이 갱신되지 않았으므로
        // 비활성화하면 KFB 상품이 전부 꺼진다. 그래서 이번 실행에서 수집이 실패했으면 건너뛴다.
        if (stepFailedInThisRun(chunkContext, FetchKfbRawTasklet.STEP_NAME)) {
            log.warn("DeactivateMissingProductTasklet: kfb skipped because {} failed in this run", FetchKfbRawTasklet.STEP_NAME);
        }
        else if (properties.source() == Source.ALL || properties.source() == Source.KFB) {
            int kfbDeactivated = productSyncService.disableAllUnseenProducts(Source.KFB, threshold);
            log.info(
                    "DeactivateMissingProductTasklet: kfb={}",
                    kfbDeactivated
            );
        }

        return RepeatStatus.FINISHED;
    }

    // 실패 뒤 흐름이 이어진 step은 BatchStatus가 ABANDONED가 되고 ExitStatus만 FAILED로 남는다.
    private static boolean stepFailedInThisRun(ChunkContext chunkContext, String stepName) {
        return chunkContext.getStepContext().getStepExecution().getJobExecution().getStepExecutions().stream()
                .anyMatch(stepExecution -> stepName.equals(stepExecution.getStepName())
                        && ExitStatus.FAILED.getExitCode().equals(stepExecution.getExitStatus().getExitCode()));
    }
}
