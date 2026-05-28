package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TossProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    @Override
    public BankCode bankCode() {
        return BankCode.TOSS;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct("토스뱅크 키워봐요 적금", "https://www.tossbank.com/product-service/savings", "키워봐요 적금")
        );
    }
}
