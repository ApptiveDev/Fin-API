package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JbProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {

    @Override
    public BankCode bankCode() {
        return BankCode.JB;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "행복키움 통장",
                        "https://www.jbbank.co.kr/gdnc_spnd_comp_detail01.act",
                        "희망드림 자산형성저축",
                        "행복키움통장"
                )
        );
    }
}
