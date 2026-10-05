package apptive.fin.apicollector.batch;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.config.CollectorProperties;
import apptive.fin.apicollector.raw.ProductRaw;
import apptive.fin.apicollector.raw.ProductRawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;


public class RawProductItemReader implements ItemReader<ProductRaw> {

    private final ProductRawRepository repository;
    private final CollectorProperties properties;
    private final Source source;
    // LLM 보강 대상 소스. 이 소스의 raw는 현재 LLM 설정의 SUCCESS 캐시가 없으면 다시 정규화한다.
    private final Collection<Source> llmSources;

    private Iterator<ProductRaw> iterator = List.<ProductRaw>of().iterator();
    private long lastSeenId = 0L;
    private boolean exhausted = false;

    public RawProductItemReader(
            ProductRawRepository repository,
            CollectorProperties properties,
            Source source,
            Collection<Source> llmSources
    ) {
        this.repository = repository;
        this.properties = properties;
        this.source = source;
        this.llmSources = llmSources;
    }

    @Override
    public ProductRaw read() {
        if (!iterator.hasNext() && !exhausted) {
            List<ProductRaw> page = repository.findNextNeedNormalize(
                    List.of(source),
                    lastSeenId,
                    properties.normalizerVersion(),
                    llmEnrichmentEnabled(),
                    llmSources,
                    llmProvider(),
                    llmModel(),
                    llmPromptVersion(),
                    llmSchemaVersion(),
                    PageRequest.of(0, properties.readerPageSize())
            );

            if (page.isEmpty()) {
                exhausted = true;
                return null;
            }

            iterator = page.iterator();
        }

        if (!iterator.hasNext()) {
            return null;
        }

        ProductRaw item = iterator.next();
        lastSeenId = item.getId();

        return item;
    }

    private boolean llmEnrichmentEnabled() {
        return properties.llm() != null
                && properties.llm().enabled()
                && properties.llm().apiKey() != null
                && !properties.llm().apiKey().isBlank();
    }

    private String llmProvider() {
        return properties.llm() == null ? "" : properties.llm().provider();
    }

    private String llmModel() {
        return properties.llm() == null ? "" : properties.llm().model();
    }

    private int llmPromptVersion() {
        return properties.llm() == null ? 0 : properties.llm().promptVersion(source);
    }

    private int llmSchemaVersion() {
        return properties.llm() == null ? 0 : properties.llm().schemaVersion(source);
    }
}
