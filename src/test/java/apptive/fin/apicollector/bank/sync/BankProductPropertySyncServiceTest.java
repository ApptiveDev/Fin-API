package apptive.fin.apicollector.bank.sync;

import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductSource;
import apptive.fin.apicollector.product.entity.Provider;
import apptive.fin.apicollector.product.repository.ProductPropertyRepository;
import apptive.fin.apicollector.product.repository.ProviderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankProductPropertySyncServiceTest {

    private final ProviderRepository providerRepository = mock(ProviderRepository.class);
    private final ProductPropertyRepository productPropertyRepository = mock(ProductPropertyRepository.class);
    private final BankProductPropertySyncService syncService = new BankProductPropertySyncService(
            providerRepository,
            productPropertyRepository
    );

    @Test
    void syncSkipsNewPropertyWhenProductAlreadyHasSameApplyUrl() {
        ProductSource source = ProductSource.create("ONTONG", "Ontong");
        Product product = Product.create(source, ProductType.POLICY, "P001", "Product");
        Provider provider = Provider.create(source, BankCode.KB.name(), BankCode.KB.displayName());
        BankProductInfo info = new BankProductInfo(
                BankCode.KB,
                "KB product",
                "SAVING",
                " https://example.com/product ",
                new BigDecimal("4.50"),
                new BigDecimal("6.00")
        );

        when(providerRepository.findBySourceAndCode(source, BankCode.KB.name())).thenReturn(Optional.of(provider));
        when(productPropertyRepository.findFirstByProductAndProvider(product, provider)).thenReturn(Optional.empty());
        when(productPropertyRepository.existsByProductAndApplyUrl(product, "https://example.com/product")).thenReturn(true);

        syncService.sync(product, info);

        verify(productPropertyRepository).existsByProductAndApplyUrl(product, "https://example.com/product");
        verify(productPropertyRepository, never()).save(any());
    }
}
