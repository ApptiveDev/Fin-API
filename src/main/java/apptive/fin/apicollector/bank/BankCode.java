package apptive.fin.apicollector.bank;

public enum BankCode {
    KB("KB국민은행", "obank.kbstar.com"),
    HANA("하나은행", "m.kebhana.com"),
    WOORI("우리은행", "spot.wooribank.com"),
    IBK("IBK기업은행", "mybank.ibk.co.kr"),
    KJB("광주은행", "www.kjbank.com");

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
