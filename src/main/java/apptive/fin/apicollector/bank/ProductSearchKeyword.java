package apptive.fin.apicollector.bank;

public record ProductSearchKeyword(String value) {
    public ProductSearchKeyword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("keyword value is required");
        }
        value = value.trim();
    }

    public String compact() {
        return value.replaceAll("\\s+", "");
    }
}
