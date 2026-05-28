package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.model.BankCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KakaoProductLinkDiscoverer extends AbstractKnownProductLinkDiscoverer {
    @Override
    public BankCode bankCode() {
        return BankCode.KAKAO;
    }

    @Override
    protected List<KnownProduct> products() {
        return List.of(
                new KnownProduct("카카오뱅크 자유적금", "https://www.kakaobank.com/products/savings", "자유적금")
        );
    }
}
