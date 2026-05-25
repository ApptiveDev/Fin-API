package apptive.fin.apicollector.bank;

import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.product.ProductPropertyOrigin;
import apptive.fin.apicollector.product.entity.Product;
import apptive.fin.apicollector.product.entity.ProductProperty;
import apptive.fin.apicollector.product.entity.Provider;
import apptive.fin.apicollector.product.repository.ProductPropertyRepository;
import apptive.fin.apicollector.product.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankProductPropertySyncService {
    private final ProviderRepository providerRepository;
    private final ProductPropertyRepository productPropertyRepository;

    @Transactional
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
                .propertyOrigin(ProductPropertyOrigin.BANK_SCRAPE)
                .providerCode(info.bankCode().name())
                .providerName(info.bankCode().displayName())
                .baseRate(info.baseRate())
                .maxRate(info.maxRate())
                .applyUrl(info.productUrl())
                .joinTarget(info.joinTarget())
                .joinPeriodText(info.joinPeriod())
                .joinAmountText(info.joinAmount())
                .joinMethod(info.joinMethod())
                .baseRateText(info.baseRateText())
                .maxRateText(info.maxRateText())
                .preferentialCondition(info.preferentialCondition())
                .depositorProtectionText(info.depositorProtectionText())
                .sourceHash(info.sourceHash())
                .scrapedAt(info.scrapedAt())
                .build();

        productPropertyRepository.findByProductAndProviderAndPropertyOrigin(
                        product,
                        provider,
                        ProductPropertyOrigin.BANK_SCRAPE
                )
                .ifPresentOrElse(
                        property -> property.updateFrom(draft),
                        () -> productPropertyRepository.save(ProductProperty.create(product, provider, draft))
                );
    }
}
