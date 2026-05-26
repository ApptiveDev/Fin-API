package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HanaProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {

    @Override
    public BankCode bankCode() {
        return BankCode.HANA;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "하나 청년도약계좌",
                        "https://www.kebhana.com/cont/mall/mall08/mall0801/mall080102/1492305_115157.jsp",
                        "청년도약계좌"
                )
        );
    }
}
