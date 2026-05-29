package apptive.fin.apicollector.bank.rate;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.extract.ProductInfoExtractor;
import apptive.fin.apicollector.bank.model.BankCode;
import apptive.fin.apicollector.bank.model.BankProductInfo;
import apptive.fin.apicollector.bank.model.CandidateSource;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankRateExtractorTest {

    @Test
    void wooriExtractorReadsSummaryDomRates() {
        BankRateExtractor extractor = new WooriRateExtractor();
        ProductCandidate candidate = candidate(BankCode.WOORI, "Woori soldier saving");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <input type="hidden" name="CHR_TXT" value="max 6.0% base 5.0%, 15 months">
                <div class="product-box">
                    <div class="product-list">
                        <div class="prd-info"><dd class="tit"><em>max 6.0% base 5.0%</em></dd></div>
                    </div>
                </div>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("5.0");
        assertThat(rates.maxRate()).isEqualByComparingTo("6.0");
    }

    @Test
    void wooriExtractorReadsRateTabDomWhenSummaryHasOnlyMaxRate() {
        BankRateExtractor extractor = new WooriRateExtractor(new FakeHtmlClient("""
                <table class="tbl-type-1">
                    <tbody>
                        <tr><th>contract</th><td>1 month</td><td class="dtd-r">0.00</td></tr>
                        <tr><td>1년이상~2년미만</td><td class="dtd-r">2.80</td></tr>
                        <tr><td>2년이상~10년이내</td><td class="dtd-r">4.50</td></tr>
                        <tr><td>10년초과</td><td class="dtd-r">3.10</td></tr>
                    </tbody>
                </table>
                """));
        ProductCandidate candidate = candidate(BankCode.WOORI, "Woori subscription");
        StaticHtmlClient.FetchedPage page = new StaticHtmlClient.FetchedPage(
                "https://spot.wooribank.com/pot/Dream?withyou=PODEP0019",
                candidate.title(),
                "",
                """
                <input type="hidden" name="PRD_CD" value="P010002293">
                <input type="hidden" name="SPCHR_TXT" value="<p>max 4.5%</p>">
                """,
                "hash"
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("3.10");
        assertThat(rates.maxRate()).isEqualByComparingTo("4.50");
    }

    @Test
    void jbExtractorFindsDecimalRateNearInterestLabels() {
        BankRateExtractor extractor = new JbRateExtractor();
        ProductCandidate candidate = candidate(BankCode.JB, "행복키움 통장");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                "상품안내 가입대상 가입기간 적용이율 약정이율 3년제 4.65 예금자보호"
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("4.65");
        assertThat(rates.maxRate()).isEqualByComparingTo("4.65");
    }

    @Test
    void wooriExtractorDoesNotTreatTermMonthsAsRates() {
        BankRateExtractor extractor = new WooriRateExtractor();
        ProductCandidate candidate = candidate(BankCode.WOORI, "청년주택드림청약통장");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                "<table><tr><th>기본금리</th><td>12개월</td></tr></table>"
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isNull();
        assertThat(rates.maxRate()).isNull();
    }

    @Test
    void nhExtractorIgnoresContributionAndEarlyTerminationRates() {
        BankRateExtractor extractor = new NhRateExtractor();
        ProductCandidate candidate = candidate(BankCode.NH, "NH청년도약계좌");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div class="product_new">
                    <table>
                        <tr><td>기여금 매칭 비율</td><td>6.0%</td><td>3.0%</td></tr>
                        <tr><td>중도해지 최저이자율</td><td>0.1%</td></tr>
                        <tr><td>중도해지 기준이율 X 80%</td></tr>
                    </table>
                </div>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isNull();
        assertThat(rates.maxRate()).isNull();
    }

    @Test
    void nhExtractorReadsSmartMarketSummaryRates() {
        BankRateExtractor extractor = new NhRateExtractor();
        ProductCandidate candidate = candidate(BankCode.NH, "청년 주택드림 청약통장");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div class="interestBanner">
                    <span>최저 연</span>
                    <strong>3.10<em>%</em></strong>
                    <em> ~ </em>
                    <span>최고 연</span>
                    <strong>4.50<em>%</em></strong>
                </div>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("3.10");
        assertThat(rates.maxRate()).isEqualByComparingTo("4.50");
    }

    @Test
    void productInfoExtractorUsesMatchingBankExtractor() {
        ProductInfoExtractor extractor = new ProductInfoExtractor(java.util.List.of(new KbRateExtractor()));
        ProductCandidate candidate = candidate(BankCode.KB, "청년도약계좌");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div class="info-data3">
                    <dl>
                        <dt>최고금리</dt>
                        <dd>금리 <span>연 </span><em>4.5</em><em>%</em> ~ <em>6.0</em><em>%</em></dd>
                    </dl>
                </div>
                <button title="금리 +0.1%">+0.1%</button>
                """
        );

        BankProductInfo info = extractor.extract(candidate, page);

        assertThat(info.baseRate()).isEqualByComparingTo("4.5");
        assertThat(info.maxRate()).isEqualByComparingTo("6.0");
    }

    @Test
    void kbExtractorReadsCompactSummaryRateRange() {
        BankRateExtractor extractor = new KbRateExtractor();
        ProductCandidate candidate = candidate(BankCode.KB, "KB청년도약계좌");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div class="info-data3">
                    <dl class="img3 t1">
                        <dt>최고금리</dt>
                        <dd>금리<span class="info-data2 t4">
                            <span>연 </span><em class="number normal">4.50~6.00%</em>
                        </span></dd>
                        <dd>2026.05.29 기준, 세금공제전, 우대금리포함</dd>
                    </dl>
                </div>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("4.50");
        assertThat(rates.maxRate()).isEqualByComparingTo("6.00");
    }

    @Test
    void busanExtractorReadsEncodedTemplateJson() {
        BankRateExtractor extractor = new BusanRateExtractor();
        ProductCandidate candidate = candidate(BankCode.BUSAN, "부산은행 청년도약계좌");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <input class="SSP_TMPLT_JSON_TEXT" value="%7B%22dtb_Ltiv%22%3A%5B%7B%22MKPD_LTIV_ITEM_NM%22%3A%22%EA%B8%B0%EB%B3%B8%EA%B8%88%EB%A6%AC%22%2C%22MKPD_LTIV_ITEM_CNTN%22%3A%224.00%22%7D%2C%7B%22MKPD_LTIV_ITEM_NM%22%3A%22%EC%B5%9C%EA%B3%A0%EA%B8%88%EB%A6%AC%22%2C%22MKPD_LTIV_ITEM_CNTN%22%3A%226.00%22%7D%5D%7D"/>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("4.00");
        assertThat(rates.maxRate()).isEqualByComparingTo("6.00");
    }

    @Test
    void hanaExtractorReadsBankRateApiJson() {
        BankRateExtractor extractor = new HanaRateExtractor(new FakeHtmlClient("""
                {"irtList":[{"baseIrt":4.5,"maxIrt":6.0}]}
                """));
        ProductCandidate candidate = candidate(BankCode.HANA, "하나 청년도약계좌");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                "<script>jQuery.ajax({url : \"/myhana/personal/wpcus401_99i_01.do?prdCd=0100308000101\"});</script>"
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("4.5");
        assertThat(rates.maxRate()).isEqualByComparingTo("6.0");
    }

    @Test
    void kyongnamExtractorReadsTopSummaryRates() {
        BankRateExtractor extractor = new KyongnamRateExtractor();
        ProductCandidate candidate = candidate(BankCode.KYONGNAM, "경남은행 청년도약계좌");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div class="acc_newtxtinfo">
                    <div class="acbox"><span class="txt">기본</span><span class="num">4.00</span><span>%</span></div>
                    <div class="acbox"><span class="txt">연 최고</span><span class="num">6.00</span><span>%</span></div>
                </div>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("4.00");
        assertThat(rates.maxRate()).isEqualByComparingTo("6.00");
    }

    @Test
    void kbankExtractorReadsEmbeddedContractRateJson() {
        BankRateExtractor extractor = new KbankRateExtractor();
        ProductCandidate candidate = candidate(BankCode.KBANK, "코드K 자유적금");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <script>
                var rateList = '{"listOut":[
                  {"pdCndNm":"약정이율","bsicIntRt":"000000000000003.30","maxIntRt":"000000000000003.30"},
                  {"pdCndNm":"약정이율","bsicIntRt":"000000000000003.50","maxIntRt":"000000000000003.50"},
                  {"pdCndNm":"중도해지이율","bsicIntRt":"000000000000000.10","maxIntRt":"000000000000000.10"}
                ]}';
                </script>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("3.30");
        assertThat(rates.maxRate()).isEqualByComparingTo("3.50");
    }

    @Test
    void kjbExtractorReadsTopSummaryBeforeBodySupportRates() {
        BankRateExtractor extractor = new KjbRateExtractor();
        ProductCandidate candidate = candidate(BankCode.KJB, "KJB장병내일준비적금");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div class="explain-box">
                    <div class="item">
                        <span class="name">최고 연</span>
                        <div class="value max"><span class="number">5.50</span><span class="unit">%</span></div>
                    </div>
                    <div class="item">
                        <span class="name">기본 연</span>
                        <div class="value default"><span class="number">5.00</span><span class="unit">%</span></div>
                    </div>
                </div>
                <section>
                    <strong>1% 이자지원금</strong>
                    <p>가입일부터 만기일까지 납입금액 건 별로 실제 예치일수 기간동안 연 1% 이율을 적용합니다.</p>
                    <strong>매칭지원금</strong>
                    <p>2023.01.01.~2023.12.31. 입금액 : 71%</p>
                </section>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("5.00");
        assertThat(rates.maxRate()).isEqualByComparingTo("5.50");
    }

    @Test
    void genericExtractorIgnoresInvalidLargeNumbersNearRateLabels() {
        BankRateExtractor extractor = new KjbRateExtractor();
        ProductCandidate candidate = candidate(BankCode.KJB, "전남청년미래적금");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div>최고금리 상품코드 DEP20220520002</div>
                <div>기본금리 연 3.00%</div>
                <div>최고금리 연 3.50%</div>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isEqualByComparingTo("3.00");
        assertThat(rates.maxRate()).isEqualByComparingTo("3.50");
    }

    @Test
    void genericExtractorIgnoresImplausibleLargeRates() {
        BankRateExtractor extractor = new IbkRateExtractor();
        ProductCandidate candidate = candidate(BankCode.IBK, "IBK product");
        StaticHtmlClient.FetchedPage page = page(
                candidate,
                """
                <div>기본금리 99.00%</div>
                <div>최고금리 100.00%</div>
                <section>금리 안내 71%</section>
                """
        );

        ExtractedRates rates = extractor.extract(candidate, page);

        assertThat(rates.baseRate()).isNull();
        assertThat(rates.maxRate()).isNull();
    }

    private ProductCandidate candidate(BankCode bankCode, String title) {
        return new ProductCandidate(
                bankCode,
                title,
                title,
                "https://example.com",
                CandidateSource.BANK_SEARCH,
                100
        );
    }

    private StaticHtmlClient.FetchedPage page(ProductCandidate candidate, String htmlOrText) {
        return new StaticHtmlClient.FetchedPage(
                candidate.url(),
                candidate.title(),
                org.jsoup.Jsoup.parse(htmlOrText).text(),
                htmlOrText,
                "hash"
        );
    }

    private static class FakeHtmlClient extends StaticHtmlClient {
        private final String body;

        private FakeHtmlClient(String body) {
            this.body = body;
        }

        @Override
        public FetchedPage fetch(String url) {
            return new FetchedPage(url, "", body, body, "hash");
        }

        @Override
        public FetchedPage post(String url, java.util.Map<String, String> data) {
            return new FetchedPage(url, "", org.jsoup.Jsoup.parse(body).text(), body, "hash");
        }
    }
}
