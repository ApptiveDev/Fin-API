package apptive.fin.apicollector.bank.keyword;

import apptive.fin.apicollector.bank.model.ProductSearchKeyword;
import apptive.fin.apicollector.product.entity.Product;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductSearchKeywordExtractor {
    private static final List<String> KNOWN_PRODUCT_NAMES = List.of(
            "청년도약계좌",
            "청년희망적금",
            "청년미래적금",
            "장병내일준비적금",
            "청년 주택드림 청약통장",
            "청년주택드림청약통장",
            "부산청년 기쁨두배통장",
            "부산청년기쁨두배통장",
            "기쁨두배통장",
            "행복키움 통장",
            "행복키움통장",
            "청년내일저축계좌",
            "내일저축계좌",
            "새희망홀씨",
            "햇살론유스"
    );

    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile(
            "([가-힣A-Za-z0-9\\s]{0,20}(?:청년|미래|도약|희망|내일|장병|주택|드림|상생|우대)[가-힣A-Za-z0-9\\s]{0,20}(?:적금|예금|계좌|통장|대출|보증|청약))"
    );

    public List<ProductSearchKeyword> extract(Product product) {
        String productName = defaultString(product.getProductName());
        Set<ProductSearchKeyword> keywords = new LinkedHashSet<>();
        Set<String> canonicalNames = canonicalNames(productName);

        if (canonicalNames.isEmpty() && !productName.isBlank()) {
            canonicalNames.add(productName);
        }

        for (String canonicalName : canonicalNames) {
            if (!productName.isBlank()) {
                keywords.add(new ProductSearchKeyword(productName, canonicalName));
            }
            keywords.add(new ProductSearchKeyword(canonicalName, canonicalName));
            keywords.add(new ProductSearchKeyword(compact(canonicalName), canonicalName));
        }

        return new ArrayList<>(keywords);
    }

    private Set<String> canonicalNames(String productName) {
        String compactText = compact(productName);
        Set<String> result = new LinkedHashSet<>();

        for (String knownProductName : KNOWN_PRODUCT_NAMES) {
            if (compactText.contains(compact(knownProductName))) {
                result.add(knownProductName);
            }
        }

        Matcher parenthesisMatcher = Pattern.compile("\\(([^)]+)\\)").matcher(productName);
        while (parenthesisMatcher.find()) {
            String candidate = parenthesisMatcher.group(1).replaceAll("\\s+", " ").trim();
            if (looksLikeFinancialProductName(candidate)) {
                result.add(candidate);
            }
        }

        Matcher matcher = PRODUCT_NAME_PATTERN.matcher(productName);
        while (matcher.find()) {
            String keyword = matcher.group(1).replaceAll("\\s+", " ").trim();
            if (keyword.length() >= 4 && keyword.length() <= 30) {
                result.add(keyword);
            }
        }

        if (looksLikeFinancialProductName(productName)) {
            result.add(productName);
        }
        return result;
    }

    private boolean looksLikeFinancialProductName(String productName) {
        String compact = compact(productName);
        return compact.contains("적금")
                || compact.contains("예금")
                || compact.contains("계좌")
                || compact.contains("통장")
                || compact.contains("청약")
                || compact.contains("대출")
                || compact.contains("보증");
    }

    private String compact(String value) {
        return defaultString(value).replaceAll("\\s+", "");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
