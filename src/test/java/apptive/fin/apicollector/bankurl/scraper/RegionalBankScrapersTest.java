package apptive.fin.apicollector.bankurl.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionalBankScrapersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void suhyupBuildsDesktopProductUrlFromMobileCode() {
        var result = new SuhyupBankScraper().extractMobileProducts(Jsoup.parse("""
                <li class="item" data-prodcd="D00175"><span class="pdt-nm">헤이(Hey)정기예금</span></li>
                """), "https://m.suhyup-bank.com/list");

        assertThat(result).containsExactly(new ProductCandidate(
                "헤이(Hey)정기예금",
                "https://www.suhyup-bank.com/ib20/mnu/FPD00118/_menuId/FPD00124/_productCode/D00175"
        ));
    }

    // PC 상품 카테고리 페이지(실제 구조): 이름은 a.go-detail의 dt, 상품코드는 같은 항목의 관심상품 버튼 onclick에 있다.
    @Test
    void suhyupExtractsCategoryProductWithCodeFromInterestButton() {
        var result = new SuhyupBankScraper().extractCategoryProducts(Jsoup.parse("""
                <ul><li>
                  <div class="pro_list_area">
                    <div class="product_txt">
                      <a href="#557" class="go-detail" title="Sh내가만든통장 상세보기">
                        <dl><dt>Sh내가만든통장</dt><dd> 지정금액에 따른 고금리제공 </dd></dl>
                      </a>
                      <ul class="sub_icon_case"><li>단기자금</li></ul>
                    </div>
                    <div class="product_btn">
                      <a href="#none" class="btn_etc_star" onclick="interest('01', 'FPD00010', 'D00148');">관심상품등록</a>
                    </div>
                  </div>
                </li></ul>
                """), "https://www.suhyup-bank.com/ib20/mnu/FPD00010");

        assertThat(result).containsExactly(new ProductCandidate(
                "Sh내가만든통장",
                "https://www.suhyup-bank.com/ib20/mnu/FPD00118/_menuId/FPD00124/_productCode/D00148"
        ));
    }

    @Test
    void kyongnamBuildsDetailUrlFromGoDetailCall() {
        var result = new KyongnamBankScraper().extractBankProducts(Jsoup.parse("""
                <a onclick="goDetail('0000020178')">BNK 위더스자유적금</a>
                """), "https://www.knbank.co.kr/list");

        assertThat(result).containsExactly(new ProductCandidate(
                "BNK 위더스자유적금",
                "https://www.knbank.co.kr/ib20/mnu/FPMCOM990000000?FNC_PRD_NO=0000020178&DUP_CHK=N"
        ));
    }

    @Test
    void kyongnamPrefersWithUsProductOverDifferentFreeSavingsProduct() {
        KyongnamBankScraper scraper = new KyongnamBankScraper();
        ProductCandidate expected = new ProductCandidate(
                "BNK 위더스WithUs 자유적금",
                "https://www.knbank.co.kr/ib20/mnu/FPMDPT020103000?fnc_prd_no=0000202492"
        );
        ProductCandidate different = new ProductCandidate(
                "BNK더조은자유적금",
                "https://www.knbank.co.kr/ib20/mnu/FPMDPT020103000?fnc_prd_no=0000020178"
        );

        ProductCandidate selected = scraper.select(
                List.of(different, expected),
                "BNK 위더스자유적금"
        );

        assertThat(selected).isEqualTo(expected);
    }

    @Test
    void busanBuildsDirectDetailUrlFromFpcd() {
        var result = new BusanBankScraper().extractProductList(Jsoup.parse("""
                <a class="FPCD_DTL" fpcd="0010100191">더(THE) 레벨업 정기예금</a>
                """), "https://www.busanbank.co.kr/list");

        assertThat(result).containsExactly(new ProductCandidate(
                "더(THE) 레벨업 정기예금",
                "https://www.busanbank.co.kr/ib20/mnu/FPMDPO012001002?FPCD=0010100191"
        ));
    }

    @Test
    void kwangjuBuildsMobileProductUrl() {
        var payload = objectMapper.readTree("""
                {"PRD_LIST":[{
                  "SMRT_BNKN_GDS_NM":"굿스타트예금",
                  "SMRT_BNKN_PCK_GDS_CD":"PICK1",
                  "SMRT_BNKN_GDS_TYCD":"100",
                  "SMRT_BNKN_GDS_CD":"PRD1",
                  "SMRT_BNKN_HOST_GDS_CD":"HOST1"
                }]}
                """);

        var result = new KwangjuBankScraper(objectMapper).extractMobileProducts(payload);

        assertThat(result).containsExactly(new ProductCandidate(
                "굿스타트예금",
                "https://m.kjbank.com/mweb/spa/goodsDetail/?pick=PICK1&kind=deposit&prdCd=PRD1&hostGdsCd=HOST1"
        ));
    }

    @Test
    void jejuPrefersMatchingInterestPaymentType() {
        JejuBankScraper scraper = new JejuBankScraper(objectMapper);
        List<ProductCandidate> candidates = List.of(
                new ProductCandidate("제주Dream정기예금 (고정금리형-월이자지급식)", "https://www.jejubank.co.kr/monthly"),
                new ProductCandidate("제주Dream정기예금 (고정금리형-만기이자지급식)", "https://www.jejubank.co.kr/maturity")
        );

        var result = scraper.preferMatchingInterestType(
                "제주Dream 정기예금 (개인/만기지급식)", candidates
        );

        assertThat(result).containsExactly(candidates.get(1));
    }

    // 전체 목록이 후보일 때, 지급식 표시("만기")로 거르면 이름에 표시가 없는 진짜 상품이 빠지고 다른 상품이 남는다.
    // 지급식 구분은 기본 이름이 같은 후보(같은 상품의 변형)끼리만 한다(2026-09-27 J정기예금 오매칭).
    @Test
    void jejuPrefersInterestTypeOnlyAmongVariantsOfSameProduct() {
        JejuBankScraper scraper = new JejuBankScraper(objectMapper);
        ProductCandidate sameProduct = new ProductCandidate("J정기예금", "https://www.jejubank.co.kr/j");
        List<ProductCandidate> candidates = List.of(
                new ProductCandidate("정기예금 (만기이자지급식)", "https://www.jejubank.co.kr/generic"),
                sameProduct,
                new ProductCandidate("제주Dream정기예금 (고정금리형-만기이자지급식)", "https://www.jejubank.co.kr/dream")
        );

        var result = scraper.preferMatchingInterestType("J정기예금 (만기지급식)", candidates);

        assertThat(result).containsExactly(sameProduct);
    }

    @Test
    void jejuUsesUnmarkedCandidateWhenSiteOmitsInterestPaymentType() {
        JejuBankScraper scraper = new JejuBankScraper(objectMapper);
        ProductCandidate candidate = new ProductCandidate(
                "스마일드림정기예금",
                "https://www.jejubank.co.kr/prepaid"
        );

        List<ProductCandidate> result = scraper.preferMatchingInterestType(
                "스마일드림 정기예금 (개인/선이자지급식)", List.of(candidate)
        );

        assertThat(result).containsExactly(candidate);
    }

    // 예적금 목록 JSON(mndp.doax, 2026-09-27 실측). 사이트 표기는 "퍼스트 적금"처럼 FSS 이름과 띄어쓰기가 다르다.
    @Test
    void jejuBuildsDetailUrlsFromProductListJson() {
        var result = new JejuBankScraper(objectMapper).extractProductsFromApi("""
                {"data": [
                  {"prdId": "SID_5482524f871f4446ad04c819ab86aa0e", "prdNm": "퍼스트 적금"},
                  {"prdId": "SID_29e7c0c612cf4ee8867a77f29aac8a59", "prdNm": "J정기예금"},
                  {"prdId": "", "prdNm": "이름만 있는 행"}
                ], "itemsCount": 3}
                """);

        assertThat(result).containsExactly(
                new ProductCandidate(
                        "퍼스트 적금",
                        "https://www.jejubank.co.kr/hmpg/prdGdnc/sid/mndp.do?mode=detail&prdId=SID_5482524f871f4446ad04c819ab86aa0e"
                ),
                new ProductCandidate(
                        "J정기예금",
                        "https://www.jejubank.co.kr/hmpg/prdGdnc/sid/mndp.do?mode=detail&prdId=SID_29e7c0c612cf4ee8867a77f29aac8a59"
                )
        );
    }

    @Test
    void jeonbukBuildsEncodedMobileDetailUrl() {
        var payload = objectMapper.readTree("""
                {"GRID":[{
                  "GDS_NM":"JB 123 정기예금",
                  "GDS_CD":"1001200240059",
                  "GDS_DTLS_CD":"0000",
                  "GDS_WHOL_CD":"10012002400590000"
                }]}
                """);

        var result = new JeonbukBankScraper(objectMapper).extractProducts(payload);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("JB 123 정기예금");
            assertThat(candidate.url()).startsWith(
                    "https://m.jbbank.co.kr:8543/JBbank.act?TRGT_URL=P_M_SID_MALL_DTL&TRGT_PARAM="
            );
        });
    }
}
