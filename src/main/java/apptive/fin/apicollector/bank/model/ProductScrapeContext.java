package apptive.fin.apicollector.bank.model;

import apptive.fin.apicollector.product.entity.Product;

public record ProductScrapeContext(
        Product product,
        ProductSearchKeyword keyword
) {
}
