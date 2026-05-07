package apptive.fin.apicollector.normalize.extractor.keywords;

import apptive.fin.apicollector.normalize.ProductDraft;
import apptive.fin.apicollector.product.KeywordValueEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RegionKeywordRecognizer implements KeywordRecognizer {

    @Override
    public List<KeywordValueEnum> recognize(ProductDraft productDraft) {
        return List.of();
    }



}
