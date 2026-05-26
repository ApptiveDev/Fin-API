package apptive.fin.apicollector.bank.model;

public record ProductCandidate(
        BankCode bankCode,
        String keyword,
        String title,
        String url,
        CandidateSource source,
        int score
) {
    public ProductCandidate withScore(int score) {
        return new ProductCandidate(bankCode, keyword, title, url, source, score);
    }
}
