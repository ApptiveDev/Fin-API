package apptive.fin.apicollector.bank.adapter;

import apptive.fin.apicollector.bank.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WooriBankProductScraper extends AbstractStaticBankProductScraper {

    public WooriBankProductScraper(
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor
    ) {
        super(htmlClient, verifier, extractor);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.WOORI;
    }

    @Override
    protected List<ProductCandidate> seedCandidates(String keyword) {
        return List.of(seed(
                "청년도약계좌",
                "청년도약계좌",
                "https://spot.wooribank.com/pot/Dream?PRD_CD=P010002512&PRD_YN=Y&cc=c007095%3Ac009166%3Bc012263%3Ac012399&withyou=PODEP0019"
        ));
    }
}
