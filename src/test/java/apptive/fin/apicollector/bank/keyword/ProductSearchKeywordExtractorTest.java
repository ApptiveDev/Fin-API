package apptive.fin.apicollector.bank.keyword;

import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchKeywordExtractorTest {
    private final ProductSearchKeywordExtractor extractor = new ProductSearchKeywordExtractor();

    @Test
    void extractsCanonicalNameFromParenthesizedProductName() {
        ProductSource source = ProductSource.create("ONTONG", "온통청년");
        Product product = Product.create(
                source,
                ProductType.POLICY,
                "P001",
                "청년 자산형성 지원(청년도약계좌)"
        );

        assertThat(extractor.extract(product))
                .extracting(ProductSearchKeyword::canonicalName)
                .contains("청년도약계좌");
    }

    @Test
    void keepsRegionalSpecificProductCanonicalName() {
        ProductSource source = ProductSource.create("ONTONG", "온통청년");
        Product product = Product.create(
                source,
                ProductType.POLICY,
                "P002",
                "부산 청년 자산형성 지원(부산청년 기쁨두배통장)"
        );

        assertThat(extractor.extract(product))
                .extracting(ProductSearchKeyword::canonicalName)
                .contains("부산청년 기쁨두배통장")
                .doesNotContain("청년도약계좌");
    }
}
