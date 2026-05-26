package apptive.fin.apicollector.bank.sync;

import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductProperty;
import apptive.fin.apicollector.product.entity.Provider;
import apptive.fin.apicollector.product.repository.ProductPropertyRepository;
import apptive.fin.apicollector.product.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankProductPropertySyncService {
    private final ProviderRepository providerRepository;
    private final ProductPropertyRepository productPropertyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(Product product, BankProductInfo info) {
        Provider provider = providerRepository.findBySourceAndCode(product.getSource(), info.bankCode().name())
                .map(existing -> {
                    existing.updateName(info.bankCode().displayName());
                    return existing;
                })
                .orElseGet(() -> providerRepository.save(Provider.create(
                        product.getSource(),
                        info.bankCode().name(),
                        info.bankCode().displayName()
                )));

        ProductPropertyDraft draft = ProductPropertyDraft.builder()
                .providerCode(info.bankCode().name())
                .providerName(info.bankCode().displayName())
                .baseRate(info.baseRate())
                .maxRate(info.maxRate())
                .applyUrl(info.productUrl())
                .build();

        productPropertyRepository.findFirstByProductAndProvider(product, provider)
                .ifPresentOrElse(
                        property -> property.updateFrom(draft),
                        () -> productPropertyRepository.save(ProductProperty.create(product, provider, draft))
                );
    }
}
