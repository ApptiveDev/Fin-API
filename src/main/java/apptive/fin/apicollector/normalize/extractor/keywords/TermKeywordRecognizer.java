package apptive.fin.apicollector.normalize.extractor.keywords;

import apptive.fin.apicollector.normalize.ProductDraft;
import apptive.fin.apicollector.normalize.ProductPropertyDraft;
import apptive.fin.apicollector.product.KeywordValueEnum;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class TermKeywordRecognizer implements KeywordRecognizer {
    @Override
    public List<KeywordValueEnum> recognize(ProductDraft productDraft, ProductPropertyDraft propertyDraft) {
        // TODO : ProductDraft 구조 바꾼 뒤 개발...
//        String content = productDraft.content();
//        Set<KeywordValueEnum> keywords = new HashSet<>();
//        addIfContains(keywords, content, KeywordValueEnum.TERM_AROUND_1_YEAR,
//                "(신용|체크).*카드", "카드결제", "카드사용", "카드.*결제"
//        );
//        addIfContains(keywords, content, KeywordValueEnum.BANK_SALARY_TRANSFER,
//                "급여.*(입금|이체)"
//        );
//        addIfContains(keywords, content, KeywordValueEnum.BANK_FIRST_TRANSACTION,
//                "첫거래", "최초거래", "신규고객", "첫고객"
//        );
//
//        return keywords.stream().toList();
        return List.of();
    }
}
