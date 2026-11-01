package apptive.fin.apicollector.tasklet;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.client.OntongYouthClient;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.youthpolicy.ApplyPeriodType;
import apptive.fin.apicollector.youthpolicy.YouthPolicyDraft;
import apptive.fin.apicollector.youthpolicy.YouthPolicyParser;
import apptive.fin.apicollector.youthpolicy.YouthPolicySyncService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncYouthPolicyTaskletTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntongYouthClient client = mock(OntongYouthClient.class);
    // 범위·필드 해석은 YouthPolicyParserTest가 본다. 여기서는 tasklet이 파서 판정을 따르는지만 본다.
    private final YouthPolicyParser parser = mock(YouthPolicyParser.class);
    private final YouthPolicySyncService syncService = mock(YouthPolicySyncService.class);

    @Test
    void 범위_안의_정책만_저장한다() {
        JsonNode inScope = MAPPER.createObjectNode().put("plcyNo", "A");
        JsonNode outOfScope = MAPPER.createObjectNode().put("plcyNo", "B");
        YouthPolicyDraft draft = draft("A");
        when(client.fetchAll()).thenReturn(List.of(inScope, outOfScope));
        when(parser.parse(inScope)).thenReturn(Optional.of(draft));
        when(parser.parse(outOfScope)).thenReturn(Optional.empty());
        when(syncService.sync(any(), any())).thenReturn(new YouthPolicySyncService.SyncResult(1, 0, 0));

        tasklet(Mode.SYNC).execute(null, null);

        verify(syncService).sync(eq(List.of(draft)), any());
    }

    // 응답 형식이나 분류 이름이 바뀌어 0건이 되면 저장된 정책이 전부 비활성화되지 않게 실패시킨다.
    @Test
    void 범위_안의_정책이_하나도_없으면_실패한다() {
        JsonNode policy = MAPPER.createObjectNode().put("plcyNo", "A");
        when(client.fetchAll()).thenReturn(List.of(policy));
        when(parser.parse(policy)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tasklet(Mode.SYNC).execute(null, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(syncService);
    }

    @Test
    void 정규화만_하는_실행에서는_건너뛴다() {
        tasklet(Mode.NORMALIZE_ONLY).execute(null, null);

        verifyNoInteractions(client, syncService);
    }

    private SyncYouthPolicyTasklet tasklet(Mode mode) {
        CollectorProperties properties = new CollectorProperties(
                true, Source.YOUTH_POLICY, mode, 1, 100, 1, null, null, null);
        return new SyncYouthPolicyTasklet(client, parser, syncService, properties);
    }

    private static YouthPolicyDraft draft(String policyNo) {
        return new YouthPolicyDraft(policyNo, "정책", null, null, null, null, null, ApplyPeriodType.ALWAYS, List.of());
    }
}
