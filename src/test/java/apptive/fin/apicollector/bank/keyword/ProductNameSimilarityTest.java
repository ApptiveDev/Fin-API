package apptive.fin.apicollector.bank.keyword;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNameSimilarityTest {

    @Test
    void treatsSpacingAndDecorativeTextAsSimilar() {
        assertThat(ProductNameSimilarity.isSimilar("청년 주택드림 청약통장", "청년주택드림청약통장"))
                .isTrue();
        assertThat(ProductNameSimilarity.isSimilar("청년도약계좌", "청년 자산형성 지원(청년도약계좌)"))
                .isTrue();
    }

    @Test
    void rejectsClearlyDifferentNames() {
        assertThat(ProductNameSimilarity.isSimilar("청년도약계좌", "우리 WON 적금"))
                .isFalse();
        assertThat(ProductNameSimilarity.isSimilar("부산청년 기쁨두배통장", "KB청년도약계좌"))
                .isFalse();
    }

    @Test
    void rejectsDifferentSavingsAccountProductsSharingGenericWords() {
        assertThat(ProductNameSimilarity.isSimilar("내일저축계좌", "연금저축계좌"))
                .isFalse();
        assertThat(ProductNameSimilarity.isSimilar("청년내일저축계좌", "연금저축계좌"))
                .isFalse();
    }
}
