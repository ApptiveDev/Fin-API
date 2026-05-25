package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.bank.BankProductScrapeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankProductScrapeTasklet implements Tasklet {
    private final BankProductScrapeService scrapeService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        BankProductScrapeService.ScrapeSummary summary = scrapeService.scrapeOntongProducts();
        log.info(
                "BankProductScrapeTasklet finished. products={}, candidates={}, saved={}, failed={}",
                summary.productCount(),
                summary.candidateCount(),
                summary.savedCount(),
                summary.failedCount()
        );
        return RepeatStatus.FINISHED;
    }
}
