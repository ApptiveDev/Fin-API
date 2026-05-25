package apptive.fin.apicollector.product.entity;

import apptive.fin.apicollector.normalize.dto.ProductPropertyDraft;
import apptive.fin.apicollector.product.InterestRateType;
import apptive.fin.apicollector.product.KeywordValueEnum;
import apptive.fin.apicollector.product.ProductPropertyOrigin;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_properties")
public class ProductProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductPropertyOrigin propertyOrigin = ProductPropertyOrigin.NORMALIZED;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "productProperty", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductKeyword> keywords = new ArrayList<>();

    @Column(precision = 5, scale = 2)
    private BigDecimal baseRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal govContributionRate;

    private Long minMonthlyLimit;
    private Long maxMonthlyLimit;

    private Integer minAge;
    private Integer maxAge;
    private Long earnMaxAmt;
    private Integer earnPercent;
    private Integer minTenureMonths;

    @Column(nullable = false)
    private Boolean requiresHomeless = false;

    @Column(nullable = false)
    private Boolean requiresHouseholder = false;

    @Column(nullable = false)
    private Boolean isJoinable = true;

    private String applyUrl;

    @Column(columnDefinition = "TEXT")
    private String joinTarget;

    @Column(columnDefinition = "TEXT")
    private String joinPeriodText;

    @Column(columnDefinition = "TEXT")
    private String joinAmountText;

    @Column(columnDefinition = "TEXT")
    private String joinMethod;

    @Column(columnDefinition = "TEXT")
    private String baseRateText;

    @Column(columnDefinition = "TEXT")
    private String maxRateText;

    @Column(columnDefinition = "TEXT")
    private String preferentialCondition;

    @Column(columnDefinition = "TEXT")
    private String depositorProtectionText;

    @Column(length = 64)
    private String sourceHash;

    private Instant scrapedAt;

    @Enumerated(EnumType.STRING)
    private InterestRateType intrRateType;

    private Integer saveTrm;

    private ProductProperty(
            Product product,
            Provider provider,
            ProductPropertyDraft propertyDraft
    ) {
        this.product = product;
        this.provider = provider;
        updateFrom(propertyDraft);
    }

    public void updateFrom(ProductPropertyDraft propertyDraft) {
        this.propertyOrigin = propertyDraft.propertyOrigin();
        this.baseRate = propertyDraft.baseRate();
        this.maxRate = propertyDraft.maxRate();
        this.govContributionRate = propertyDraft.govContributionRate();
        this.minMonthlyLimit = propertyDraft.minMonthlyLimit();
        this.maxMonthlyLimit = propertyDraft.maxMonthlyLimit();
        this.minAge = propertyDraft.minAge();
        this.maxAge = propertyDraft.maxAge();
        this.earnMaxAmt = propertyDraft.earnMaxAmt();
        this.earnPercent = propertyDraft.earnPercent();
        this.minTenureMonths = propertyDraft.minTenureMonths();
        this.requiresHomeless = propertyDraft.requiresHomeless();
        this.requiresHouseholder = propertyDraft.requiresHouseholder();
        this.isJoinable = true;
        this.applyUrl = propertyDraft.applyUrl();
        this.joinTarget = propertyDraft.joinTarget();
        this.joinPeriodText = propertyDraft.joinPeriodText();
        this.joinAmountText = propertyDraft.joinAmountText();
        this.joinMethod = propertyDraft.joinMethod();
        this.baseRateText = propertyDraft.baseRateText();
        this.maxRateText = propertyDraft.maxRateText();
        this.preferentialCondition = propertyDraft.preferentialCondition();
        this.depositorProtectionText = propertyDraft.depositorProtectionText();
        this.sourceHash = propertyDraft.sourceHash();
        this.scrapedAt = propertyDraft.scrapedAt();
        this.intrRateType = InterestRateType.fromCode(propertyDraft.intrRateType());
        this.saveTrm = propertyDraft.saveTerm();
        replaceKeywords(propertyDraft.keywords());
    }

    public static ProductProperty create(
            Product product,
            Provider provider,
            ProductPropertyDraft propertyDraft
    ) {
        return new ProductProperty(product, provider, propertyDraft);
    }

    public void replaceKeywords(List<KeywordValueEnum> keywordCodes) {
        Set<KeywordValueEnum> desiredKeywords = keywordCodes == null || keywordCodes.isEmpty()
                ? EnumSet.noneOf(KeywordValueEnum.class)
                : EnumSet.copyOf(keywordCodes);

        this.keywords.removeIf(keyword -> !desiredKeywords.contains(keyword.getKeywordCode()));

        Set<KeywordValueEnum> currentKeywords = new HashSet<>();
        for (ProductKeyword keyword : this.keywords) {
            currentKeywords.add(keyword.getKeywordCode());
        }

        for (KeywordValueEnum keywordCode : desiredKeywords) {
            if (!currentKeywords.contains(keywordCode)) {
                this.keywords.add(ProductKeyword.create(this, keywordCode));
            }
        }
    }

    public void markUnjoinable() {
        this.isJoinable = false;
    }

    public void markJoinable() {
        this.isJoinable = true;
    }
}
