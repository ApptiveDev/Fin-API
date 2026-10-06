package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.client.kfb.KfbClient;
import apptive.fin.apicollector.client.kfb.KfbParkingFilter;
import apptive.fin.apicollector.client.kfb.KfbRawProduct;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.product.ProductType;
import apptive.fin.apicollector.raw.RawProductSaveService;
import apptive.fin.apicollector.raw.SaveResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FetchKfbRawTaskletTest {

    private final KfbClient kfbClient = mock(KfbClient.class);
    private final RawProductSaveService saveService = mock(RawProductSaveService.class);

    @Test
    void savesOnlyParkingProductsAsKfbParkingRaw() {
        when(kfbClient.fetchAll()).thenReturn(List.of(
                product("세이프박스", "1.60"),
                product("주거래우대통장", "0.10")
        ));
        when(saveService.saveOrUpdate(any(), anyString(), any(), any())).thenReturn(SaveResult.INSERTED);
        ArgumentCaptor<JsonNode> raw = ArgumentCaptor.forClass(JsonNode.class);

        tasklet(Mode.SYNC).execute(null, null);

        verify(saveService).saveOrUpdate(
                eq(Source.KFB),
                eq("KFB:PARKING:0015130:세이프박스"),
                raw.capture(),
                eq(ProductType.PARKING)
        );
        assertThat(raw.getValue().path("source").asText()).isEqualTo("KFB");
        assertThat(raw.getValue().path("productType").asText()).isEqualTo("PARKING");
        assertThat(raw.getValue().path("bankCode").asText()).isEqualTo("0015130");
        assertThat(raw.getValue().path("productName").asText()).isEqualTo("세이프박스");
        assertThat(raw.getValue().path("baseRate").decimalValue()).isEqualByComparingTo("1.60");
        assertThat(raw.getValue().path("maxLimit").asLong()).isEqualTo(100_000_000L);
    }

    // 페이지 구조가 바뀌어 0건이 되면, 조용히 넘어가는 대신 실패시켜 기존 상품이 전부 비활성화되지 않게 한다.
    @Test
    void failsWhenNothingIsParsed() {
        when(kfbClient.fetchAll()).thenReturn(List.of());

        assertThatThrownBy(() -> tasklet(Mode.SYNC).execute(null, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(saveService);
    }

    @Test
    void skipsFetchInNormalizeOnlyMode() {
        tasklet(Mode.NORMALIZE_ONLY).execute(null, null);

        verifyNoInteractions(kfbClient, saveService);
    }

    private FetchKfbRawTasklet tasklet(Mode mode) {
        return new FetchKfbRawTasklet(kfbClient, new KfbParkingFilter(), saveService, properties(mode), new ObjectMapper());
    }

    private static KfbRawProduct product(String name, String baseRate) {
        return new KfbRawProduct("0015130", "카카오뱅크", name, "https://bank.example/p",
                new BigDecimal(baseRate), new BigDecimal("2.00"), "월지급",
                "스마트뱅킹", "없음", "제한없음", "개인", "유의사항", 100_000_000L);
    }

    private static CollectorProperties properties(Mode mode) {
        return new CollectorProperties(
                true,
                Source.KFB,
                mode,
                1,
                100,
                1,
                null,
                null,
                new CollectorProperties.Llm(false, "GEMINI", "gemini-test", 1, 1, 10, 3, 0.1, "http://localhost", "")
        );
    }
}
