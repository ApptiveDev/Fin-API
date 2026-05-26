package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KyongnamProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {

    @Override
    public BankCode bankCode() {
        return BankCode.KYONGNAM;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct(
                        "경남은행 청년도약계좌",
                        "https://m.knbank.co.kr/ib20/mnu/MBWSFM030103000?fnc_prd_no=0000206467&ib20_cur_mnu=MBWSFM030000000&ib20_cur_wgt=MBWSFM024JNGV01M&ib20_wc=MBWSFM024JNGV01M%3AMBWSFM024JNGV02M",
                        "청년도약계좌"
                )
        );
    }
}
