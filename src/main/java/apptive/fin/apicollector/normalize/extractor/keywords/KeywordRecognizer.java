package apptive.fin.apicollector.normalize.extractor.keywords;

import apptive.fin.apicollector.normalize.ProductDraft;
import apptive.fin.apicollector.normalize.ProductPropertyDraft;
import apptive.fin.apicollector.product.KeywordValueEnum;

import java.util.List;
import java.util.Set;

public interface KeywordRecognizer {
    List<KeywordValueEnum> recognize(ProductDraft productDraft, ProductPropertyDraft propertyDraft);
    default void addIfContains(
            Set<KeywordValueEnum> keywords,
            String value,
            KeywordValueEnum keyword,
            String... tokens
    ) {
        if (value == null) {
            return;
        }

        for (String token : tokens) {
            if (value.contains(token) || value.matches(token)) {
                keywords.add(keyword);
                return;
            }
        }
    }
}
