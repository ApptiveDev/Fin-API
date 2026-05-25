package apptive.fin.apicollector.bank;

import apptive.fin.apicollector.product.entity.Product;

public record ProductScrapeContext(
        Product product,
        ProductSearchKeyword keyword
) {
}
