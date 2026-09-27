package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.client.kfb.KfbClient;
import apptive.fin.apicollector.client.kfb.KfbParkingFilter;
import apptive.fin.apicollector.client.kfb.KfbRawProduct;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.raw.RawProductSaveService;
import apptive.fin.apicollector.raw.SaveResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 은행연합회 입출금자유예금 공시를 받아 파킹통장만 raw로 저장한다.
 *
 * <p>필터를 정규화가 아니라 여기서 거는 이유: 기준에서 빠진 상품은 raw의 lastSeen이 갱신되지 않아
 * 기존 DeactivateMissingProductTasklet 경로로 비활성화된다. 정규화 단계에서 거르면 이미 저장된 상품이 활성으로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchKfbRawTasklet implements Tasklet {

    // DeactivateMissingProductTasklet이 이번 실행의 KFB 수집 실패 여부를 이 이름으로 확인한다.
    public static final String STEP_NAME = "fetchKfbRawStep";

    private final KfbClient kfbClient;
    private final KfbParkingFilter parkingFilter;
    private final RawProductSaveService rawProductSaveService;
    private final CollectorProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (properties.mode().isNormalizeOnly()) {
            log.info("FetchKfbRawTasklet skipped. mode={}", properties.mode());
            return RepeatStatus.FINISHED;
        }

        List<KfbRawProduct> products = kfbClient.fetchAll();
        // 페이지 구조가 바뀌면 0건이 된다. 그대로 두면 unseen 기간 뒤 KFB 상품이 전부 비활성화되므로 실패로 알린다.
        if (products.isEmpty()) {
            throw new IllegalStateException("KFB free deposit disclosure returned no products. Page structure may have changed.");
        }

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int filteredOut = 0;

        for (KfbRawProduct product : products) {
            if (!parkingFilter.isParking(product)) {
                filteredOut++;
                continue;
            }

            SaveResult result = rawProductSaveService.saveOrUpdate(
                    Source.KFB,
                    product.externalId(),
                    toRaw(product),
                    ProductType.PARKING
            );

            switch (result) {
                case INSERTED -> inserted++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }

        log.info(
                "FetchKfbRawTasklet finished. fetched={}, inserted={}, updated={}, unchanged={}, filteredOut={}",
                products.size(),
                inserted,
                updated,
                unchanged,
                filteredOut
        );

        return RepeatStatus.FINISHED;
    }

    private ObjectNode toRaw(KfbRawProduct product) {
        ObjectNode raw = objectMapper.valueToTree(product);
        raw.put("source", Source.KFB.name());
        raw.put("productType", ProductType.PARKING.name());
        return raw;
    }
}
