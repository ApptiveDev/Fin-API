package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.policyround.PolicyProductRoundSyncService;
import apptive.fin.apicollector.policyround.PolicyProductRoundSyncService.SyncResult;
import apptive.fin.apicollector.policyround.ProductRounds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * B1 캘린더 1계층(Y-Fin 정책상품) 신청 회차를 manual-product-rounds.json에서 읽어 policy_product_round에 맞춘다.
 * 온통청년은 연도·회차마다 정책번호가 바뀌고 공고보다 늦게 올라오는 경우가 많아, 사람이 확인한 회차를 파일로 관리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncPolicyProductRoundTasklet implements Tasklet {

    public static final String STEP_NAME = "syncPolicyProductRoundStep";
    static final String RESOURCE = "/manual-product-rounds.json";

    private final ObjectMapper objectMapper;
    private final PolicyProductRoundSyncService syncService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<ProductRounds> entries = load(objectMapper);
        SyncResult result = syncService.sync(entries, Instant.now());
        if (!result.missingProducts().isEmpty()) {
            log.warn("SyncPolicyProductRoundTasklet: products not collected yet, rounds skipped. productCodes={}",
                    result.missingProducts());
        }
        log.info("SyncPolicyProductRoundTasklet: products={}, inserted={}, updated={}, deleted={}",
                entries.size(), result.inserted(), result.updated(), result.deleted());
        return RepeatStatus.FINISHED;
    }

    static List<ProductRounds> load(ObjectMapper objectMapper) {
        try (InputStream in = SyncPolicyProductRoundTasklet.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Policy product rounds resource not found: " + RESOURCE);
            }
            List<ProductRounds> entries = List.of(objectMapper.readValue(in, ProductRounds[].class));
            entries.forEach(ProductRounds::validate);
            return entries;
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to load policy product rounds from " + RESOURCE, e);
        }
    }
}
