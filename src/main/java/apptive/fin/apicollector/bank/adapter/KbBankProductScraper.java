package apptive.fin.apicollector.bank.adapter;

import apptive.fin.apicollector.bank.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KbBankProductScraper extends AbstractStaticBankProductScraper {

    public KbBankProductScraper(
            StaticHtmlClient htmlClient,
            ProductPageVerifier verifier,
            ProductInfoExtractor extractor
    ) {
        super(htmlClient, verifier, extractor);
    }

    @Override
    public BankCode bankCode() {
        return BankCode.KB;
    }

    @Override
    protected List<ProductCandidate> seedCandidates(String keyword) {
        return List.of(seed(
                "청년도약계좌",
                "청년도약계좌",
                "https://obank.kbstar.com/quics?cc=b061496%3Ab061645&page=C016613&prcode=DP01001576"
        ));
    }
}
