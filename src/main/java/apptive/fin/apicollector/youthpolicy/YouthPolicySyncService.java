package apptive.fin.apicollector.youthpolicy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class YouthPolicySyncService {

    private final YouthPolicyRepository youthPolicyRepository;

    public record SyncResult(int inserted, int updated, int deactivated) {
    }

    /**
     * 이번에 받은 정책 목록으로 저장 상태를 맞춘다. 목록에 없는 기존 정책은 비활성화한다.
     * 캘린더 범위(금융지원 분류)는 수백 건이라 기존 정책을 한 번에 읽어 비교한다.
     */
    @Transactional
    public SyncResult sync(List<YouthPolicyDraft> drafts, Instant syncedAt) {
        Map<String, YouthPolicy> existing = youthPolicyRepository.findAll().stream()
                .collect(Collectors.toMap(YouthPolicy::getPolicyNo, Function.identity()));

        int inserted = 0;
        int updated = 0;
        for (YouthPolicyDraft draft : drafts) {
            YouthPolicy policy = existing.remove(draft.policyNo());
            if (policy == null) {
                policy = new YouthPolicy(draft.policyNo());
                inserted++;
            }
            else {
                updated++;
            }
            policy.apply(draft, syncedAt);
            youthPolicyRepository.save(policy);
        }

        int deactivated = 0;
        for (YouthPolicy missing : existing.values()) {
            if (missing.isActive()) {
                missing.deactivate();
                deactivated++;
            }
        }
        return new SyncResult(inserted, updated, deactivated);
    }
}
