package apptive.fin.apicollector.batch;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.raw.ProductRawRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RawProductItemReaderTest {

    // KFB 프롬프트 버전만 올렸을 때 FSS reader가 FSS 버전으로 캐시를 찾아야 FSS를 다시 호출하지 않는다.
    @Test
    void looksUpLlmCacheWithVersionsOfItsOwnSource() {
        ProductRawRepository repository = mock(ProductRawRepository.class);
        when(repository.findNextNeedNormalize(
                any(), anyLong(), anyInt(), anyBoolean(), any(), anyString(), anyString(), anyInt(), anyInt(), any()
        )).thenReturn(List.of());

        new RawProductItemReader(repository, properties(), Source.FSS, Set.of(Source.FSS, Source.KFB)).read();
        new RawProductItemReader(repository, properties(), Source.KFB, Set.of(Source.FSS, Source.KFB)).read();

        verify(repository).findNextNeedNormalize(
                eq(List.of(Source.FSS)), anyLong(), anyInt(), anyBoolean(), any(), anyString(), anyString(), eq(8), eq(16), any()
        );
        verify(repository).findNextNeedNormalize(
                eq(List.of(Source.KFB)), anyLong(), anyInt(), anyBoolean(), any(), anyString(), anyString(), eq(9), eq(17), any()
        );
    }

    private static CollectorProperties properties() {
        return new CollectorProperties(
                true,
                Source.ALL,
                Mode.NORMALIZE_ONLY,
                1,
                100,
                7,
                null,
                null,
                new CollectorProperties.Llm(
                        true,
                        "GEMINI",
                        "gemini-test",
                        Map.of(Source.FSS, 8, Source.KFB, 9),
                        Map.of(Source.FSS, 16, Source.KFB, 17),
                        10,
                        3,
                        0.1,
                        "http://localhost",
                        "key"
                )
        );
    }
}
