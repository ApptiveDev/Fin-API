package apptive.fin.apicollector.bank;

public record VerificationResult(
        VerificationStatus status,
        int score,
        String matchedText
) {
    public boolean verified() {
        return status == VerificationStatus.VERIFIED;
    }
}
