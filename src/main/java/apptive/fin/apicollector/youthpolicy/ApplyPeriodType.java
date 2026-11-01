package apptive.fin.apicollector.youthpolicy;

/** 온통청년 신청기간 구분(aplyPrdSeCd). Fin-BE의 같은 이름 enum과 저장값을 맞춘다. */
public enum ApplyPeriodType {
    PERIOD,  // 0057001 특정기간: aplyYmd에 회차가 있다
    ALWAYS,  // 0057002 상시
    CLOSED;  // 0057003 마감

    public static ApplyPeriodType fromOntongCode(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "0057001" -> PERIOD;
            case "0057002" -> ALWAYS;
            case "0057003" -> CLOSED;
            default -> null;
        };
    }
}
