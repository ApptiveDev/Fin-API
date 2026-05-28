package apptive.fin.apicollector.bank.sync;

import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductProperty;
import apptive.fin.apicollector.product.entity.Provider;
import apptive.fin.apicollector.product.repository.ProductPropertyRepository;
import apptive.fin.apicollector.product.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
                .applyUrl(normalizeUrl(info.productUrl()))
                .build();

        productPropertyRepository.findFirstByProductAndProvider(product, provider)
                .ifPresentOrElse(
                        property -> property.updateFrom(draft),
                        () -> saveIfUrlNotDuplicated(product, provider, draft)
                );
    }

    private void saveIfUrlNotDuplicated(Product product, Provider provider, ProductPropertyDraft draft) {
        if (hasText(draft.applyUrl()) && productPropertyRepository.existsByProductAndApplyUrl(product, draft.applyUrl())) {
            log.info(
                    "Bank product property skipped because applyUrl already exists. productId={}, providerCode={}, applyUrl={}",
                    product.getId(),
                    provider.getCode(),
                    draft.applyUrl()
            );
            return;
        }
        productPropertyRepository.save(ProductProperty.create(product, provider, draft));
    }

    private String normalizeUrl(String url) {
        if (!hasText(url)) {
            return url;
        }
        return url.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
