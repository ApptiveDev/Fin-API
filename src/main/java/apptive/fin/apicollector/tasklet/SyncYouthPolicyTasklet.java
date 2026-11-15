package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.client.OntongYouthClient;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.youthpolicy.PolicyProductMatcher;
import apptive.fin.apicollector.youthpolicy.PolicyProductMatcher.PolicyProduct;
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
import java.util.ArrayList;
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
    private final PolicyProductMatcher policyProductMatcher;
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

        List<YouthPolicyDraft> calendarDrafts = excludePolicyProducts(drafts);
        SyncResult result = youthPolicySyncService.sync(calendarDrafts, Instant.now());
        log.info(
                "SyncYouthPolicyTasklet: fetched={}, inScope={}, sameAsPolicyProduct={}, inserted={}, updated={}, deactivated={}",
                policies.size(), drafts.size(), drafts.size() - calendarDrafts.size(),
                result.inserted(), result.updated(), result.deactivated()
        );
        return RepeatStatus.FINISHED;
    }

    // 1계층 정책상품과 같은 사업은 2계층에 넣지 않는다. 시군 별도 모집처럼 회차가 1계층과 다를 수 있어
    // manual-product-rounds.json을 고칠 때 볼 수 있게 회차를 함께 남긴다.
    private List<YouthPolicyDraft> excludePolicyProducts(List<YouthPolicyDraft> drafts) {
        List<YouthPolicyDraft> kept = new ArrayList<>();
        for (YouthPolicyDraft draft : drafts) {
            Optional<PolicyProduct> product = policyProductMatcher.match(draft.name());
            if (product.isPresent()) {
                log.info("SyncYouthPolicyTasklet: same as policy product, skipped. productCode={}, plcyNo={}, plcyNm={}, institution={}, rounds={}",
                        product.get().productCode(), draft.policyNo(), draft.name(), draft.institution(), draft.rounds());
            }
            else {
                kept.add(draft);
            }
        }
        return kept;
    }
}
