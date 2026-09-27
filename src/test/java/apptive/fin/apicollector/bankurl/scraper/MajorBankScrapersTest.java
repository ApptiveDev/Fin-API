package apptive.fin.apicollector.bankurl.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MajorBankScrapersTest {

    @Test
    void kbExtractsProductResultLink() {
        var result = new KbBankScraper().extractSearchResults(Jsoup.parse("""
                <ul id="procList"><li><strong><a href="/quics?page=detail">KB Star 정기예금</a></strong></li></ul>
                """), "https://obank.kbstar.com/quics?page=search");

        assertThat(result).containsExactly(new ProductCandidate(
                "KB Star 정기예금", "https://obank.kbstar.com/quics?page=detail"
        ));
    }

    @Test
    void kbBuildsDetailUrlFromProductSearchResult() {
        var result = new KbBankScraper().extractSearchResults(Jsoup.parse("""
                <div class="area1">
                  <a href="#none" class="title"
                     onclick="productDtlSear('DP01000942','01','적금')">KB맑은하늘적금</a>
                </div>
                """), "https://obank.kbstar.com/quics?page=C016528");

        assertThat(result).containsExactly(new ProductCandidate(
                "KB맑은하늘적금",
                "https://obank.kbstar.com/quics?page=C016613"
                        + "&cc=b061496:b061645&QSL=F&prcode=DP01000942"
        ));
    }

    // KB모임금고처럼 이름에 예금·적금·통장이 없는 입출금 상품도 검색 결과 행이면 후보로 받는다.
    @Test
    void kbKeepsSearchResultWithoutDepositWord() {
        var result = new KbBankScraper().extractSearchResults(Jsoup.parse("""
                <div class="area1">
                  <a href="#none" class="title"
                     onclick="productDtlSear('DP01001593','01','입출금자유')">KB모임금고</a>
                </div>
                """), "https://obank.kbstar.com/quics?page=C016528");

        assertThat(result).containsExactly(new ProductCandidate(
                "KB모임금고",
                "https://obank.kbstar.com/quics?page=C016613"
                        + "&cc=b061496:b061645&QSL=F&prcode=DP01001593"
        ));
    }

    @Test
    void hanaExtractsOnlyProductInfoBlocks() {
        var result = new HanaBankScraper().extractSearchResults(Jsoup.parse("""
                <div class="resultDiv"><div class="productInfo"><h5>
                  <a href="/cont/product/1">하나의정기예금</a>
                </h5></div></div>
                """), "https://www.kebhana.com/search");

        assertThat(result).containsExactly(new ProductCandidate(
                "하나의정기예금", "https://www.kebhana.com/cont/product/1"
        ));
    }

    @Test
    void nhAddsKnownMiniSavingsProduct() {
        var result = new NhBankScraper().extractSearchResults(
                Jsoup.parse("<script>const code='1004713600002';</script>"),
                "https://smartmarket.nonghyup.com/list"
        );

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("NH올원e미니적금");
            assertThat(candidate.url()).contains("detailPsnFncWrsC=1004713600002");
        });
    }

    @Test
    void nhBuildsDetailUrlFromLfGetDtCall() {
        var result = new NhBankScraper().extractSearchResults(
                Jsoup.parse("""
                        <ul class="subject_product_li"><li>
                          <dt><a href="javascript:lfGetDt('10001196');">NH고향사랑기부예금</a></dt>
                        </li></ul>
                        """),
                "https://smartmarket.nonghyup.com/servlet/BFDCW1021R.view"
        );

        assertThat(result).containsExactly(new ProductCandidate(
                "NH고향사랑기부예금",
                "https://smartmarket.nonghyup.com/servlet/BFDCW1021R.view"
                        + "?detailPsnFncWrsC=10001196&psnFncWrsC=10001196&listServiceId=BFDCW1011R"
        ));
    }

    @Test
    void ibkBuildsDetailUrlFromGoBankingArguments() {
        String url = new IbkBankScraper().urlFromGoBanking(
                "gobanking_url('/uib/detail.jsp','21011310089','page','*****','IBK회전정기 예금');"
        );

        assertThat(url)
                .contains("lncd=21", "grcd=01", "tmcd=131", "pdcd=0089", "wvcd=*****")
                .endsWith("i_trns_biz_kncd=IBK%ED%9A%8C%EC%A0%84%EC%A0%95%EA%B8%B0%20%EC%98%88%EA%B8%88");
    }

    @Test
    void kdbExtractsKnownProductCode() {
        var result = new KdbBankScraper().extractProducts(
                Jsoup.parse("const PROD_C='100237000101'; const PROD_NM='KDB 정기예금';"),
                "https://banking.kdb.co.kr"
        );

        assertThat(result).contains(new ProductCandidate(
                "KDB 정기예금",
                "https://banking.kdb.co.kr/bp/BMDEWP01N10.act?PRD_C=100237000101#prd=100237000101"
        ));
    }

    @Test
    void scEncodesSearchQueryAsEucKr() {
        assertThat(new ScBankScraper().quoteEucKr("예금")).isEqualTo("%BF%B9%B1%DD");
    }

    @Test
    void shinhanExtractsProductNameAndCode() {
        var result = new ShinhanBankScraper().extractProductCodes(Jsoup.parse("""
                <ul class="listTyProducts"><li>
                  <div class="prdtName"><a>쏠편한 정기예금</a></div>
                  <span id="x상품코드y">P123</span>
                </li></ul>
                """), "https://bank.shinhan.com");

        assertThat(result).containsExactly(new ProductCandidate("쏠편한 정기예금", "P123"));
    }

    // sitemap에 없는 상품의 모바일 URL(PR0401S0000F01/PR0301S0100F01?pid=…, mid 없음)은 예금·적금·입출금 모두 모바일 홈으로
    // 튕긴다. 데스크톱 bridge 링크는 상품코드(pcd)로 상품을 연다(2026-09-27 실측: 정기예금·적금·입출금통장).
    @Test
    void shinhanFallsBackToDesktopBridgeUrlForProductMissingFromSitemap() {
        assertThat(new ShinhanBankScraper().fallbackUrl("110004301"))
                .isEqualTo("https://bank.shinhan.com/bank_bridge.jsp?cr=020102010110&pcd=110004301");
    }
}
