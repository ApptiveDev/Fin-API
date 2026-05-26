package apptive.fin.apicollector.bank.model;

public record VerificationResult(
        VerificationStatus status,
        int score,
        String matchedText
) {
}
