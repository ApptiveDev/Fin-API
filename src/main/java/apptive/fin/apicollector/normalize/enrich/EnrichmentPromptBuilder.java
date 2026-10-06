package apptive.fin.apicollector.normalize.enrich;

import apptive.fin.apicollector.Source;
import apptive.fin.apicollector.normalize.dto.ProductDraft;
import apptive.fin.apicollector.raw.ProductRaw;

/** 소스별 LLM enrichment 요청 프롬프트. 빌더가 등록된 소스만 LLM으로 보강한다. */
public interface EnrichmentPromptBuilder {

    Source source();

    String build(ProductRaw rawProduct, ProductDraft draft);
}
