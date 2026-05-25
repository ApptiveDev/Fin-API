package apptive.fin.apicollector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BankProductScrapeJobConfig {

    @Bean
    public Job bankProductScrapeJob(
            JobRepository jobRepository,
            Step bankProductScrapeStep
    ) {
        return new JobBuilder("bankProductScrapeJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(bankProductScrapeStep)
                .build();
    }

    @Bean
    public Step bankProductScrapeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet bankProductScrapeTasklet
    ) {
        return new StepBuilder("bankProductScrapeStep", jobRepository)
                .tasklet(bankProductScrapeTasklet, transactionManager)
                .build();
    }
}
