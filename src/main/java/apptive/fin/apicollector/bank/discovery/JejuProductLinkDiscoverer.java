package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JejuProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    @Override
    public BankCode bankCode() {
        return BankCode.JEJU;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "사이버우대정기예금",
                        "https://www.e-jejubank.com/HomeFMDeposit.do?gLnbMenuCd=349&goodsCodeNo=343&menuCd=349&scrType=View",
                        "제주은행 사이버우대정기예금"
                )
        );
    }
}
