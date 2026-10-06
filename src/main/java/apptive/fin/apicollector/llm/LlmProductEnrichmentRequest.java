package apptive.fin.apicollector.llm;

import apptive.fin.apicollector.Source;

public record LlmProductEnrichmentRequest(
        Source source,
        String model,
        String prompt,
        int schemaVersion
) {
}
