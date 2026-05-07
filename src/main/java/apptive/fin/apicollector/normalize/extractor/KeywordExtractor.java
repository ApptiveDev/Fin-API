package apptive.fin.apicollector.normalize.extractor;

import apptive.fin.apicollector.normalize.ProductDraft;
import apptive.fin.apicollector.normalize.extractor.keywords.KeywordRecognizer;
import apptive.fin.apicollector.product.KeywordValueEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KeywordExtractor {
    private final List<KeywordRecognizer> keywordRecognizers;

    public List<KeywordValueEnum> extract(ProductDraft productDraft) {
        List<KeywordValueEnum> keywords = new ArrayList<>();
        for (KeywordRecognizer keywordRecognizer : keywordRecognizers) {
            keywords.addAll(keywordRecognizer.recognize(productDraft));
        }
        return keywords;
    }

}
