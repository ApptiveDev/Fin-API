package apptive.fin.apicollector.bank.discovery;

import apptive.fin.apicollector.bank.client.StaticHtmlClient;
import apptive.fin.apicollector.bank.model.ProductCandidate;
import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchBackedProductLinkDiscovererTest {

    @Test
    void hanaExtractorBuildsCandidatesFromProductSearchLinks() {
        HanaProductLinkDiscoverer discoverer = new HanaProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.kebhana.com/cont/mall/mall08/mall0805/index.jsp",
                """
                <em><a href="/cont/mall/mall08/mall0801/mall080102/1492305_115157.jsp">Youth Leap Account</a></em>
                <em><a href="/cont/mall/mall08/mall0801/mall080102/1524394_115157.jsp">Savings Account</a></em>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("Youth Leap Account"), page);

        assertThat(candidates)
                .extracting(ProductCandidate::title)
                .containsExactly("Youth Leap Account");
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.kebhana.com/cont/mall/mall08/mall0801/mall080102/1492305_115157.jsp");
    }

    @Test
    void busanExtractorBuildsDetailUrlFromFpcdAndHighLevelCodeAttributes() {
        BusanProductLinkDiscoverer discoverer = new BusanProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.busanbank.co.kr/ib20/mnu/FPMDPO012009001",
                """
                <p class="item-thumb-tit">
                    <a href="#none" class="FPCD_DTL" FPCD="0010100189" FP_HLV_DVCD="00101">Youth Leap Account</a>
                </p>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("Youth Leap Account"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.busanbank.co.kr/ib20/mnu/FPMDPO012001002?FPCD=0010100189&FP_HLV_DVCD=00101&TIT_NM=%EC%A0%84%EC%B2%B4%EC%83%81%ED%92%88&MENU_ID=FPMDPO012009001");
    }

    @Test
    void ibkDiscovererSearchesAllVisibleListPages() {
        IbkProductLinkDiscoverer discoverer = new IbkProductLinkDiscoverer(new StaticHtmlClient());

        List<SearchBackedProductLinkDiscoverer.SearchRequest> requests = discoverer.searchRequests(
                new ProductSearchKeyword("IBK장병내일준비적금")
        );

        assertThat(requests).hasSize(10);
        assertThat(requests)
                .extracting(request -> request.data().get("pageIndex"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(requests.getFirst().data())
                .containsEntry("product_flag", "Y")
                .containsEntry("prdcSaleYN", "Y");
    }

    @Test
    void ibkExtractorBuildsDetailUrlFromPagedProductList() {
        IbkProductLinkDiscoverer discoverer = new IbkProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://mybank.ibk.co.kr/uib/jsp/guest/ntr/ntr70/ntr7010/PNTR701000_m.jsp",
                """
                <a href="#none" onclick="uf_showDetail('01','21','121','0112','***********','IBK장병내일준비적금');" class="stit">
                    IBK장병내일준비적금
                </a>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("IBK장병내일준비적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://mybank.ibk.co.kr/uib/jsp/guest/ntr/ntr70/ntr7010/PNTR701000_i2.jsp?MENU_DIV=GNB&lncd=01&grcd=21&tmcd=121&pdcd=0112&wvcd=%2A%2A%2A%2A%2A%2A%2A%2A%2A%2A%2A&i_trns_biz_kncd=IBK%EC%9E%A5%EB%B3%91%EB%82%B4%EC%9D%BC%EC%A4%80%EB%B9%84%EC%A0%81%EA%B8%88");
    }

    @Test
    void ibkExtractorCanDiscoverFutureYouthSavingsProductFromPagedList() {
        IbkProductLinkDiscoverer discoverer = new IbkProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://mybank.ibk.co.kr/uib/jsp/guest/ntr/ntr70/ntr7010/PNTR701000_m.jsp",
                """
                <a href="#none" onclick="uf_showDetail('01','21','121','0999','***********','IBK청년미래적금');" class="stit">
                    IBK청년미래적금
                </a>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("청년미래적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().title()).isEqualTo("IBK청년미래적금");
        assertThat(candidates.getFirst().url())
                .contains("pdcd=0999")
                .contains("i_trns_biz_kncd=IBK%EC%B2%AD%EB%85%84%EB%AF%B8%EB%9E%98%EC%A0%81%EA%B8%88");
    }

    @Test
    void jbDiscovererSearchesCurrentSavingProductList() {
        JbProductLinkDiscoverer discoverer = new JbProductLinkDiscoverer(new StaticHtmlClient());

        List<SearchBackedProductLinkDiscoverer.SearchRequest> requests = discoverer.searchRequests(
                new ProductSearchKeyword("JB 장병내일준비적금")
        );

        assertThat(requests).hasSize(11);
        assertThat(requests.getFirst().url())
                .isEqualTo("https://www.jbbank.co.kr/gdnc_szmy.act");
        assertThat(requests)
                .extracting(SearchBackedProductLinkDiscoverer.SearchRequest::url)
                .contains(
                        "https://www.jbbank.co.kr/NFF_FRDP_OPAC.act",
                        "https://www.jbbank.co.kr/NFF_FRDP_SZMY.act",
                        "https://www.jbbank.co.kr/NFF_FRDP_SMYR.act",
                        "https://www.jbbank.co.kr/NFF_FRDP_HSBC.act",
                        "https://www.jbbank.co.kr/NFF_FRDP_MRKT.act",
                        "https://www.jbbank.co.kr/EBCIB_GDSBS_M_R001.jct"
                );
        assertThat(requests)
                .filteredOn(request -> request.url().equals("https://www.jbbank.co.kr/EBCIB_GDSBS_M_R001.jct"))
                .allSatisfy(request -> {
                    assertThat(request.headers())
                            .containsEntry("X-Requested-With", "XMLHttpRequest")
                            .containsKey("Referer");
                    assertThat(request.data().get("_JSON_"))
                            .contains("\"GDS_NM\":\"JB 장병내일준비적금\"")
                            .contains("\"GDS_STCD\":\"20\"");
                });
    }

    @Test
    void jbExtractorBuildsCandidatesFromSavingProductList() {
        JbProductLinkDiscoverer discoverer = new JbProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.jbbank.co.kr/gdnc_szmy.act",
                """
                <dt><strong>JB 장병내일준비적금</strong>
                    <a href="#jbbank" onclick="jb_location('GDNC_NTAR_PRPR.act'); return false;">Open</a>
                </dt>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("JB 장병내일준비적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.jbbank.co.kr/GDNC_NTAR_PRPR.act");
    }

    @Test
    void jbExtractorCanDiscoverFutureYouthSavingsProductFromSavingList() {
        JbProductLinkDiscoverer discoverer = new JbProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.jbbank.co.kr/gdnc_szmy.act",
                """
                <dt><strong>JB 청년미래적금</strong>
                    <a href="#jbbank" onclick="jb_location('GDNC_YOUTH_FUTURE.act'); return false;">Open</a>
                </dt>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("청년미래적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().title()).isEqualTo("JB 청년미래적금");
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.jbbank.co.kr/GDNC_YOUTH_FUTURE.act");
    }

    @Test
    void jbExtractorBuildsCandidatesFromProductMallApiJson() {
        JbProductLinkDiscoverer discoverer = new JbProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.jbbank.co.kr/EBCIB_GDSBS_M_R001.jct",
                """
                {"REC":[{"GDS_NM":"JB 청년미래적금","LINK_URL":"GDNC_YOUTH_FUTURE.act","MCCD":"20"}],"COMMON_HEAD":{"ERROR":false}}
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("청년미래적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().title()).isEqualTo("JB 청년미래적금");
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.jbbank.co.kr/GDNC_YOUTH_FUTURE.act");
    }

    @Test
    void jbExtractorFallsBackToProductMallAnchorWhenApiJsonHasNoDetailUrl() {
        JbProductLinkDiscoverer discoverer = new JbProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.jbbank.co.kr/EBCIB_GDSBS_M_R001.jct",
                """
                {"REC":[{"GDS_NM":"JB 청년미래적금","GDS_WHOL_CD":"10013003100990000","MCCD":"20"}],"COMMON_HEAD":{"ERROR":false}}
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("청년미래적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.jbbank.co.kr/NFF_FRDP_SZMY.act#product-10013003100990000");
    }

    @Test
    void kyongnamExtractorBuildsMobileDetailUrlFromEmbeddedProductJson() {
        KyongnamProductLinkDiscoverer discoverer = new KyongnamProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.knbank.co.kr/ib20/mnu/FPMDPT020107000",
                """
                <script>
                setPrdArray('[{"FNC_PRD_NO":"0000206467","KOR_PRD_NM":"Youth Leap Account","PRD_CD":"21001241"}]');
                </script>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("Youth Leap Account"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .contains("fnc_prd_no=0000206467");
    }

    @Test
    void nhDiscovererBuildsCandidatesFromSmartMarketFragment() {
        NhProductLinkDiscoverer discoverer = new NhProductLinkDiscoverer(new FakeNhHtmlClient());

        List<ProductCandidate> candidates = discoverer.discover(new ProductSearchKeyword("청년도약계좌"));

        assertThat(candidates)
                .extracting(ProductCandidate::title)
                .contains("NH청년도약계좌");
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://smartmarket.nonghyup.com/servlet/BFDCW1021R.view?psnFncWrsC=10001241&serviceId=BFDCW1011R");
    }

    @Test
    void kbankExtractorBuildsDetailUrlFromHomepageMenuJson() {
        KbankProductLinkDiscoverer discoverer = new KbankProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.kbanknow.com/",
                """
                <script>
                var HomMnuList3 = [{"CMN_CD_NM":"코드K 자유적금","CMN_CD_ABRV_NM":"FPMDPT080000"}];
                </script>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("코드K 자유적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.kbanknow.com/ib20/mnu/FPMDPT080000");
    }

    @Test
    void suhyupExtractorBuildsCandidatesFromAllDepositList() {
        SuhyupProductLinkDiscoverer discoverer = new SuhyupProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.suhyup-bank.com/ib20/mnu/FPD00124",
                """
                <a href="#258" class="go-detail" title="Sh월복리자유적금 상세보기">
                    <dl><dt>Sh월복리자유적금</dt></dl>
                </a>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("Sh월복리자유적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.suhyup-bank.com/ib20/mnu/FPD00124#product-258");
    }

    @Test
    void imExtractorBuildsCandidatesFromProductMallLinks() {
        ImProductLinkDiscoverer discoverer = new ImProductLinkDiscoverer(new StaticHtmlClient());
        StaticHtmlClient.FetchedPage page = page(
                "https://www.imbank.co.kr/com_ebz_fpm_sub_main.jsp",
                """
                <dl>
                    <dt><span class="category">예금 / 목돈만들기</span> <span>iM자유적금</span></dt>
                    <dd><a href="javascript:goProductDetailByPdCd('105270','010000','01000','D');">상세보기</a></dd>
                </dl>
                """
        );

        List<ProductCandidate> candidates = discoverer.extract(new ProductSearchKeyword("iM자유적금"), page);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().url())
                .isEqualTo("https://www.imbank.co.kr/com_ebz_fpm_sub_main.jsp#product-10527001000001000");
    }

    private StaticHtmlClient.FetchedPage page(String url, String html) {
        return new StaticHtmlClient.FetchedPage(url, "", org.jsoup.Jsoup.parse(html).text(), html, "hash");
    }

    private static class FakeNhHtmlClient extends StaticHtmlClient {
        @Override
        public FetchedPageWithCookies fetchWithResponseCookies(String url) {
            return new FetchedPageWithCookies(
                    new FetchedPage(
                            url,
                            "",
                            "",
                            "window[\"TOKEN\"] = 'test-token';",
                            "hash"
                    ),
                    java.util.Map.of("JSESSIONID", "test-session")
            );
        }

        @Override
        public FetchedPage post(
                String url,
                java.util.Map<String, String> data,
                java.util.Map<String, String> headers,
                java.util.Map<String, String> cookies
        ) {
            assertThat(headers).containsEntry("TOKEN", "test-token");
            assertThat(cookies).containsEntry("JSESSIONID", "test-session");
            return new FetchedPage(
                    url,
                    "",
                    org.jsoup.Jsoup.parse(fragment()).text(),
                    fragment(),
                    "hash"
            );
        }

        private String fragment() {
            return """
                    <p><em id="list_count">1</em>개의 상품이 검색되었습니다. (총 1건)</p>
                    <a href="#none" class="sbj" onclick="lfGetDp('10001241');">NH청년도약계좌</a>
                    <a href="#none" class="sbj" onclick="lfGetDp('10001011');">NH1934월복리적금</a>
                    """;
        }
    }
}
