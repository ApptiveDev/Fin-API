package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.client.OntongYouthClient;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.youthpolicy.YouthPolicyDraft;
import apptive.fin.apicollector.youthpolicy.YouthPolicyParser;
import apptive.fin.apicollector.youthpolicy.YouthPolicySyncService;
import apptive.fin.apicollector.youthpolicy.YouthPolicySyncService.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 온통청년 정책을 받아 B1 캘린더용 youth_policy에 맞춘다. raw 테이블을 거치지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncYouthPolicyTasklet implements Tasklet {

    public static final String STEP_NAME = "syncYouthPolicyStep";

    private final OntongYouthClient ontongYouthClient;
    private final YouthPolicyParser youthPolicyParser;
    private final YouthPolicySyncService youthPolicySyncService;
    private final CollectorProperties properties;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (properties.mode().isNormalizeOnly()) {
            log.info("SyncYouthPolicyTasklet skipped. mode={}", properties.mode());
            return RepeatStatus.FINISHED;
        }

        List<JsonNode> policies = ontongYouthClient.fetchAll();
        List<YouthPolicyDraft> drafts = policies.stream()
                .map(youthPolicyParser::parse)
                .flatMap(Optional::stream)
                .toList();

        // 응답 형식이나 분류 이름이 바뀌어 0건이 되면, 저장된 정책이 전부 비활성화되지 않게 실패시킨다.
        if (drafts.isEmpty()) {
            throw new IllegalStateException(
                    "No youth policy in calendar scope. fetched=" + policies.size());
        }

        SyncResult result = youthPolicySyncService.sync(drafts, Instant.now());
        log.info(
                "SyncYouthPolicyTasklet: fetched={}, inScope={}, inserted={}, updated={}, deactivated={}",
                policies.size(), drafts.size(), result.inserted(), result.updated(), result.deactivated()
        );
        return RepeatStatus.FINISHED;
    }
}
