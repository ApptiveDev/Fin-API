package apptive.fin.apicollector.bank.model;

public enum BankCode {
    KB("KB국민은행", "obank.kbstar.com"),
    SHINHAN("신한은행", "shinhan.com"),
    HANA("하나은행", "kebhana.com"),
    WOORI("우리은행", "spot.wooribank.com"),
    NH("NH농협은행", "nhlink.nonghyup.com"),
    IBK("IBK기업은행", "mybank.ibk.co.kr"),
    SC("SC제일은행", "standardchartered.co.kr"),
    IM("iM뱅크", "imbank.co.kr"),
    BUSAN("BNK부산은행", "busanbank.co.kr"),
    KJB("광주은행", "www.kjbank.com"),
    JB("전북은행", "www.jbbank.co.kr"),
    JEJU("제주은행", "www.e-jejubank.com"),
    KYONGNAM("BNK경남은행", "knbank.co.kr"),
    SUHYUP("Sh수협은행", "www.suhyup-bank.com"),
    KAKAO("카카오뱅크", "kakaobank.com"),
    KBANK("케이뱅크", "kbanknow.com"),
    TOSS("토스뱅크", "tossbank.com");

    private final String displayName;
    private final String officialHost;

    BankCode(String displayName, String officialHost) {
        this.displayName = displayName;
        this.officialHost = officialHost;
    }

    public String displayName() {
        return displayName;
    }

    public String officialHost() {
        return officialHost;
    }
}
