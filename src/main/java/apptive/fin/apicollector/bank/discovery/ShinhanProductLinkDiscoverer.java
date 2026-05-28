package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShinhanProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    @Override
    public BankCode bankCode() {
        return BankCode.SHINHAN;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "신한 청년도약계좌",
                        "https://bank.shinhan.com/rib/gate.xml?mcd=020102010110&language=ko",
                        "청년도약계좌"
                )
        );
    }
}
