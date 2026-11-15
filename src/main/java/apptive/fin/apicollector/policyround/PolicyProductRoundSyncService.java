package apptive.fin.apicollector.policyround;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductSource;
import apptive.fin.apicollector.product.repository.ProductRepository;
import apptive.fin.apicollector.product.repository.ProductSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * manual-product-rounds.json으로 policy_product_round를 맞춘다. 파일이 원천이라 파일에 없는 회차는 지운다.
 * 회차는 상품별 파일 안 순서(round_no)로 맞춰, 날짜가 바뀐 회차만 변경 시각을 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyProductRoundSyncService {

    // 수동 정부상품은 product_source ONTONG으로 저장된다(ManualProductNormalizer).
    private static final String MANUAL_PRODUCT_SOURCE = Source.ONTONG.name();

    private final PolicyProductRoundRepository roundRepository;
    private final ProductSourceRepository productSourceRepository;
    private final ProductRepository productRepository;

    /** @param missingProducts DB에 아직 없는 상품 코드(정부상품 수집 전이면 생긴다) */
    public record SyncResult(int inserted, int updated, int deleted, List<String> missingProducts) {
    }

    @Transactional
    public SyncResult sync(List<ProductRounds> entries, Instant syncedAt) {
        Optional<ProductSource> source = productSourceRepository.findByCode(MANUAL_PRODUCT_SOURCE);
        Map<Long, List<PolicyProductRound>> existingByProduct = roundRepository.findAll().stream()
                .collect(Collectors.groupingBy(PolicyProductRound::getProductId));

        int inserted = 0;
        int updated = 0;
        int deleted = 0;
        List<String> missingProducts = new ArrayList<>();
        for (ProductRounds entry : entries) {
            Optional<Product> product = source.flatMap(s -> productRepository.findBySourceAndProductCode(s, entry.productCode()));
            if (product.isEmpty()) {
                missingProducts.add(entry.productCode());
                continue;
            }
            Long productId = product.get().getId();
            Map<Integer, PolicyProductRound> existing = existingByProduct.getOrDefault(productId, List.of()).stream()
                    .collect(Collectors.toMap(PolicyProductRound::getRoundNo, Function.identity()));
            existingByProduct.remove(productId);

            for (int i = 0; i < entry.rounds().size(); i++) {
                int roundNo = i + 1;
                ProductRounds.Round next = entry.rounds().get(i);
                PolicyProductRound round = existing.remove(roundNo);
                if (round == null) {
                    roundRepository.save(new PolicyProductRound(productId, roundNo, next));
                    inserted++;
                }
                else if (round.apply(next, syncedAt)) {
                    updated++;
                }
            }
            roundRepository.deleteAll(existing.values());
            deleted += existing.size();
        }

        // 파일에서 상품 항목이 통째로 빠졌으면 그 상품의 회차도 지운다.
        List<PolicyProductRound> orphaned = existingByProduct.values().stream().flatMap(List::stream).toList();
        roundRepository.deleteAll(orphaned);
        deleted += orphaned.size();

        return new SyncResult(inserted, updated, deleted, missingProducts);
    }
}
