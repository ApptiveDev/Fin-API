package apptive.fin.apicollector.bankurl;

import apptive.fin.apicollector.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class BankProductUrlRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private BankProductUrlRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsDistinctActiveFssTargetAndUpdatesAllActiveVariants() {
        Long sourceId = jdbcTemplate.queryForObject(
                "select id from product_source where code = 'FSS'", Long.class
        );
        Long providerId = jdbcTemplate.queryForObject("""
                insert into provider(source_id, code, name)
                values (?, 'TEST_BANK_URL', '테스트은행')
                returning id
                """, Long.class, sourceId);
        // 디스플레이명(product_name)은 괄호가 떼인 이름, 원본(original_name)은 괄호를 포함한 이름.
        // 수집기는 원본을 검색어로 써야 하므로 original_name이 조회돼야 한다.
        Long productId = jdbcTemplate.queryForObject("""
                insert into product(source_id, type, product_code, product_name, original_name)
                values (?, 'DEPOSIT', 'TEST_URL_PRODUCT', '테스트정기예금', '테스트정기예금(개인/자유적립식)')
                returning id
                """, Long.class, sourceId);
        jdbcTemplate.update("""
                insert into product_properties(product_id, provider_id, is_joinable, save_trm)
                values (?, ?, true, 6), (?, ?, true, 12), (?, ?, false, 24)
                """, productId, providerId, productId, providerId, productId, providerId);

        var targets = repository.findActiveTargets(List.of("FSS")).stream()
                .filter(target -> target.productId().equals(productId))
                .toList();
        int updated = repository.updateActiveProductUrl(
                productId, "TEST_BANK_URL", "https://bank.example/product"
        );

        assertThat(targets).singleElement().satisfies(target ->
                assertThat(target.originalName()).isEqualTo("테스트정기예금(개인/자유적립식)")
        );
        assertThat(updated).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from product_properties
                where product_id = ? and apply_url = 'https://bank.example/product'
                """, Integer.class, productId)).isEqualTo(2);
    }

    @Test
    void findsTargetsOnlyOfRequestedSources() {
        jdbcTemplate.update("insert into product_source (code, name) values ('KFB', 'KFB') on conflict (code) do nothing");
        Long kfbProductId = seedActiveProduct("KFB", "KFB_URL_PRODUCT", "테스트파킹통장");
        Long fssProductId = seedActiveProduct("FSS", "FSS_URL_PRODUCT", "테스트정기예금");

        var kfbTargets = repository.findActiveTargets(List.of("KFB")).stream().map(BankProductUrlTarget::productId).toList();
        var bothTargets = repository.findActiveTargets(List.of("FSS", "KFB")).stream().map(BankProductUrlTarget::productId).toList();

        assertThat(kfbTargets).contains(kfbProductId).doesNotContain(fssProductId);
        assertThat(bothTargets).contains(kfbProductId, fssProductId);
    }

    private Long seedActiveProduct(String sourceCode, String productCode, String name) {
        Long sourceId = jdbcTemplate.queryForObject("select id from product_source where code = ?", Long.class, sourceCode);
        Long providerId = jdbcTemplate.queryForObject("""
                insert into provider(source_id, code, name) values (?, 'TEST_BANK_URL', '테스트은행') returning id
                """, Long.class, sourceId);
        Long productId = jdbcTemplate.queryForObject("""
                insert into product(source_id, type, product_code, product_name, original_name)
                values (?, 'PARKING', ?, ?, ?) returning id
                """, Long.class, sourceId, productCode, name, name);
        jdbcTemplate.update("insert into product_properties(product_id, provider_id, is_joinable) values (?, ?, true)", productId, providerId);
        return productId;
    }
}
