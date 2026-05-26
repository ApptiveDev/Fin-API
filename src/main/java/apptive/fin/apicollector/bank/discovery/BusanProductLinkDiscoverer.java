package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusanProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {

    @Override
    public BankCode bankCode() {
        return BankCode.BUSAN;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "부산은행 청년도약계좌",
                        "https://www.busanbank.co.kr/ib20/mnu/FPMDPO012001002?FPCD=0010100189&FP_HLV_DVCD=00101&TIT_NM=%EB%AA%A9%EB%8F%88%EB%A7%8C%EB%93%A4%EA%B8%B0&FP_LRG_CLACD=001010102&FP_MD_CLACD=000000000&MENU_ID=FPMDPO012002001",
                        "청년도약계좌"
                )
        );
    }
}
