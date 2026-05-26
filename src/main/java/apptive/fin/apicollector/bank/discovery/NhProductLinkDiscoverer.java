package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NhProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {

    @Override
    public BankCode bankCode() {
        return BankCode.NH;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "NH청년도약계좌",
                        "https://nhlink.nonghyup.com/content/nhbank/html/sf/mk/dt/dt10001241_02.html",
                        "청년도약계좌"
                )
        );
    }
}
