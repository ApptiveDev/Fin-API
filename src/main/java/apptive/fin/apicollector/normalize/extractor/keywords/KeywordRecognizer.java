package apptive.fin.apicollector.normalize.extractor.keywords;

import apptive.fin.apicollector.normalize.ProductDraft;
import apptive.fin.apicollector.product.KeywordValueEnum;

import java.util.List;
import java.util.Optional;

public interface KeywordRecognizer {
    // 어떤 키워드를 인식하면 true, 아니면 false
    List<KeywordValueEnum> recognize(ProductDraft productDraft);
}
