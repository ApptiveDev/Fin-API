package apptive.fin.apicollector.config;

import apptive.fin.apicollector.Mode;
import apptive.fin.apicollector.Source;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@ConfigurationProperties(prefix = "collector")
public record CollectorProperties(
        boolean enabled,
        Source source,
        Mode mode,
        int normalizerVersion,
        int readerPageSize,
        int unseenDisablePeriod,
        OntongYouth ontongYouth,
        Fss fss,
        Llm llm
) {

    public record OntongYouth(
            String baseUrl,
            String apiKey,
            int pageSize
    ) {}

    public record Fss(
            String baseUrl,
            String apiKey,
            int pageSize
    ) {}

    /**
     * prompt/schema 버전은 소스별로 둔다. 한 소스의 프롬프트만 바꿔도 버전을 같이 쓰면
     * 다른 소스의 LLM 캐시까지 모두 무효가 되어 전체를 다시 호출하기 때문이다.
     */
    public record Llm(
            boolean enabled,
            String provider,
            String model,
            Map<Source, Integer> promptVersions,
            Map<Source, Integer> schemaVersions,
            int chunkSize,
            int maxConcurrency,
            Double temperature,
            String baseUrl,
            String apiKey
    ) {
        public Llm {
            promptVersions = promptVersions == null ? Map.of() : Map.copyOf(promptVersions);
            schemaVersions = schemaVersions == null ? Map.of() : Map.copyOf(schemaVersions);
        }

        // LLM 보강을 하지 않는 소스(ONTONG 등)는 버전이 없어 0으로 둔다.
        public int promptVersion(Source source) {
            return promptVersions.getOrDefault(source, 0);
        }

        public int schemaVersion(Source source) {
            return schemaVersions.getOrDefault(source, 0);
        }
    }
}
