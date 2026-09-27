package apptive.fin.apicollector.bankurl.scraper;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** 실제 은행 스크래퍼 전부. 허용 도메인 같은 은행별 기준을 테스트에서 실제 값으로 쓸 때 사용한다. */
public final class BankProductScrapers {

    private BankProductScrapers() {
    }

    public static List<BankProductScraper> all() {
        ObjectMapper objectMapper = new ObjectMapper();
        return List.of(
                new BusanBankScraper(),
                new HanaBankScraper(),
                new IbkBankScraper(),
                new ImBankScraper(objectMapper),
                new JejuBankScraper(objectMapper),
                new JeonbukBankScraper(objectMapper),
                new KakaoBankScraper(),
                new KbankScraper(objectMapper),
                new KbBankScraper(),
                new KdbBankScraper(objectMapper),
                new KwangjuBankScraper(objectMapper),
                new KyongnamBankScraper(),
                new NhBankScraper(),
                new ScBankScraper(),
                new ShinhanBankScraper(),
                new SuhyupBankScraper(),
                new TossBankScraper(),
                new WooriBankScraper()
        );
    }
}
